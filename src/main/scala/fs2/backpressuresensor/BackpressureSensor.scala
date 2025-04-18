package fs2.backpressuresensor

import fs2.{Pipe, Pull, Stream}
import cats.effect.kernel.Clock
import java.time.Instant
import cats.effect.kernel.Ref
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._
import scala.concurrent.duration.Duration
import cats.Applicative
import cats.syntax.all._
import cats.Monad
import cats.effect.kernel.GenConcurrent
import cats.effect.kernel.Async
import cats.effect.kernel.Resource

trait Reporter[F[_]]:
  def reportStarvedFor(duration: FiniteDuration): F[Unit]
  def reportBackpressuredFor(duration: FiniteDuration): F[Unit]

class AccumulatingReporter[F[_]: Monad](
    starvationAcc: Ref[F, Option[FiniteDuration]],
    backpressureAcc: Ref[F, Option[FiniteDuration]]
) extends Reporter[F]:
  def reportStarvedFor(duration: FiniteDuration): F[Unit] =
    starvationAcc.update(_.map(_ + duration).orElse(Some(duration)))

  def reportBackpressuredFor(duration: FiniteDuration): F[Unit] =
    backpressureAcc.update(_.map(_ + duration).orElse(Some(duration)))

  def consume(f: (Option[FiniteDuration], Option[FiniteDuration]) => F[Unit]): F[Unit] =
    for
      starvation <- starvationAcc.getAndSet(None)
      backpressure <- backpressureAcc.getAndSet(None)
      _ <- f(starvation, backpressure)
    yield ()

object AccumulatingReporter:
  def apply[F[_]: Monad: Async](): F[AccumulatingReporter[F]] =
    for
      backpressureAcc <- Ref.of(Option.empty[FiniteDuration])
      starvationAcc <- Ref.of(Option.empty[FiniteDuration])
    yield new AccumulatingReporter[F](starvationAcc, backpressureAcc)

object Reporter {
  def interval[F[_]: Monad: Async](interval: FiniteDuration)(
      reportAction: (FiniteDuration, FiniteDuration) => F[Unit]
  ): Resource[F, Reporter[F]] =
    Resource
      .make(
        for
          r <- AccumulatingReporter[F]()
          f <- Async[F].start(
            Stream
              .awakeEvery(interval)
              .evalTap { elapsed => 
                r.consume( (starvation, backpressure) => reportAction(starvation.getOrElse(elapsed), backpressure.getOrElse(Duration.Zero))) 
              }
              .compile
              .drain
          )
        yield r -> f
      )((r, f) => f.cancel)
      .map(_._1)
}

object BackpressureSensor:
  private[BackpressureSensor] class BracketReporter[F[_]: Monad](
      reporter: Reporter[F],
      upstreamStarvationAcc: Ref[F, FiniteDuration],
      upstreamBackpressureAcc: Ref[F, FiniteDuration]
  ):
    val upstream: Reporter[F] = new Reporter[F]:
      def reportStarvedFor(duration: FiniteDuration): F[Unit] =
        upstreamStarvationAcc.update(_ + duration)
      def reportBackpressuredFor(duration: FiniteDuration): F[Unit] =
        upstreamBackpressureAcc.update(_ + duration)

    val downstream: Reporter[F] = new Reporter[F]:
      def reportStarvedFor(duration: FiniteDuration): F[Unit] =
        for
          upstreamDuration <- upstreamStarvationAcc.getAndSet(Duration.Zero)
          pipeContribution = (duration - upstreamDuration).max(Duration.Zero)
          adjustedDuration = (upstreamDuration - pipeContribution).max(Duration.Zero)
          _ <- reporter.reportStarvedFor(adjustedDuration)
        yield ()
      def reportBackpressuredFor(duration: FiniteDuration): F[Unit] =
        for
          upstreamDuration <- upstreamBackpressureAcc.getAndSet(Duration.Zero)
          adjustedDuration = (upstreamDuration - duration).max(Duration.Zero)
          _ <- reporter.reportBackpressuredFor(adjustedDuration)
        yield ()

  object BracketReporter:
    def apply[F[_]: Monad: Async](
        reporter: Reporter[F]
    ): F[BracketReporter[F]] =
      for
        upstreamBackpressureAcc <- Ref.of(Duration.Zero)
        upstreamStarvationAcc <- Ref.of(Duration.Zero)
        br = new BracketReporter[F](
          reporter,
          upstreamStarvationAcc,
          upstreamBackpressureAcc,
        )
      yield br

  def sensor[F[_]: Clock, T](
      reporter: Reporter[F]
  ): Pipe[F, T, T] = stream =>
    def loop(loopStream: Stream[F, T], lastPullTs: Instant): Pull[F, T, Unit] =
      loopStream.pull.uncons1.flatMap:
        case Some(t -> rest) =>
          for
            pushTs <- Pull.eval(Clock[F].realTimeInstant)
            starvationDuration = java.time.Duration
              .between(lastPullTs, pushTs)
              .toScala
            _ <- Pull.eval(reporter.reportStarvedFor(starvationDuration))

            _ <- Pull.output1(t)

            pullTs <- Pull.eval(Clock[F].realTimeInstant)
            backpressureDuration = java.time.Duration
              .between(pushTs, pullTs)
              .toScala
            _ <- Pull.eval(
              reporter.reportBackpressuredFor(backpressureDuration)
            )

            done <- loop(rest, pullTs)
          yield done

        case None =>
          Pull.done

    Stream
      .eval(Clock[F].realTimeInstant)
      .flatMap: initTs =>
        loop(stream, initTs).stream

  def bracket[F[_]: Monad: Async: Clock, T, U](
      reporter: Reporter[F]
  )(pipe: Pipe[F, T, U]): Pipe[F, T, U] = stream =>
    Stream
      .eval(BracketReporter[F](reporter))
      .flatMap: br =>
        sensor[F, T](br.upstream)
          .andThen(pipe)
          .andThen(sensor[F, U](br.downstream))(stream)
