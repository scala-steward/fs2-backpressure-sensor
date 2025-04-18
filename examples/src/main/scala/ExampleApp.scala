import cats.effect.IOApp
import cats.effect.IO
import cats.effect.std.Console

import fs2.Stream

import fs2.backpressuresensor.Reporter
import fs2.concurrent.SignallingRef
import scala.concurrent.duration.*
import cats.effect.kernel.Resource

import cats.effect.kernel.Ref

import org.jline.terminal.TerminalBuilder
import org.jline.terminal.Terminal
import java.io.IOException
import org.jline.terminal.Terminal.Signal
import org.jline.terminal.Terminal.SignalHandler
import scala.collection.mutable
import fs2.Pipe
import java.io.FileWriter

trait ExampleApp extends IOApp.Simple:
  def reporter(name: String) = 
    for
      writer <- Resource.make(IO(new FileWriter(s"reporter_$name.log", true)))(w => IO(w.close()))
      r <- Reporter.interval(1.second) { (starv, backp) =>
        val totalDuration = starv + backp
        val ratio =
          if (totalDuration > Duration.Zero)
            backp.toNanos.toDouble / totalDuration.toNanos.toDouble
          else 0.0

        IO.blocking {
          writer.write(f"starvation=${starv.toMillis}, backpressure=${backp.toMillis}, ratio=${ratio}%.2f\n")
          writer.flush()
        }
      }
    yield r

  lazy val pipeRefMap: Ref[IO, Map[String, Ref[IO, FiniteDuration]]] = IO.ref(Map.empty).unsafeRunSync()(runtime)
  def controlledPipe[T](name: String): Pipe[IO, T, T] = stream =>
    Stream.eval(IO.ref(Duration.Zero))
      .evalTap: ref =>
        pipeRefMap.update(_.updated(name, ref))
      .flatMap: ref =>
        stream.evalTap: _ => 
          for
            delay <- ref.get
            _ <- IO.sleep(delay)
          yield ()
        

  val stream: fs2.Stream[IO, Unit]

  private def setupTerminal: IO[Terminal] = IO.blocking:
    val terminal = TerminalBuilder.builder()
      .system(true)
      .jna(true)
      .jansi(true)
      .build()
    terminal.enterRawMode()
    terminal
  

  private def keyboardListener(terminal: Terminal): IO[Unit] =
    for
      running <- SignallingRef[IO, Boolean](true)
      keyMap <- pipeRefMap.get
        .debug()
        .map: refMap =>
          refMap.keys
            .toList
            .sorted
            .take(10)
            .zip(List('q','w','e','r','t','y','u','i','o','p'))
            .map(_.swap)
            .toMap
        .flatTap: keyMap =>
          IO.println(s"Control pipe delay (lowercase: decrease, uppercase: increase)\n${keyMap.map( (c, n) => s"- $n: $c").mkString("\n")}")
      
      _ <- fs2.Stream.repeatEval:
            for
              key <- IO.blocking(terminal.reader().read())
              _ <- keyMap.get(key.toChar.toLower) match 
                case Some(name) =>
                  for
                    refMap <- pipeRefMap.get
                    delayRef <- IO.fromOption(refMap.get(name))(new IllegalArgumentException(s"Not found $name"))
                    increase = key.toChar!= key.toChar.toLower
                    updatedDelay <- delayRef.updateAndGet( currentDelay => if (increase) currentDelay.plus(20.millis).min(1.second) else currentDelay.minus(20.millis).max(Duration.Zero))
                    _ <- IO.println(s"Pipe $name delay: ${updatedDelay.toMillis}ms")
                  yield ()
                case None => 
                  IO.println(s"No pipe associated to ${key.toChar}: ")
            yield ()
          .interruptWhen(running.map(!_))
          .compile
          .drain
          .start
      _ <- running.get.iterateUntil(!_).void
    yield ()
  

  override def run: IO[Unit] = 
    for {
      variableRef <- Ref.of[IO, Int](0)
      terminal <- setupTerminal
      _ <- IO.blocking {
        // Set up signal handler to restore terminal on exit
        terminal.handle(Signal.INT, new SignalHandler {
          def handle(signal: Signal): Unit = {
            terminal.close()
            System.exit(0)
          }
        })
      }
      // Run keyboard listener in a separate fiber
      terminationSignal <- SignallingRef[IO, Boolean](false)
      startedSignal <- SignallingRef[IO, Boolean](false)
      streamFiber <- stream
        .evalFold(true)((init, _) => IO.whenA(init)(startedSignal.set(true)) >> IO.pure(false))
        .interruptWhen(terminationSignal)
        .compile.drain.start

      _ <- startedSignal.waitUntil(identity)
      _ <- keyboardListener(terminal)
      _ <- streamFiber.join
      // Clean up
      _ <- terminationSignal.set(true)
      _ <- IO.blocking(terminal.close())
    } yield ()
