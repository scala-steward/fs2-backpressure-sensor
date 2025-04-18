import fs2.Stream
import fs2.backpressuresensor.syntax.*

import cats.effect.{IO, IOApp}
import scala.concurrent.duration.*
import fs2.backpressuresensor.Reporter

object Example1 extends ExampleApp:
  val stream = Stream
      .awakeEvery[IO](100.millis)
      .zipWithIndex
      .backpressureSensor(reporter("pre pipe1"))
      .through(controlledPipe("pipe1"))
      .backpressureSensor(reporter("post pipe1"))
      .through(controlledPipe("pipe2"))
      .backpressureSensor(reporter("post pipe2"))
      .as(())
