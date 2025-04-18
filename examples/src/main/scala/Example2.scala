import fs2.Stream
import fs2.backpressuresensor.syntax.*

import cats.effect.{IO, IOApp}
import scala.concurrent.duration.*
import fs2.backpressuresensor.Reporter

object Example2 extends ExampleApp:
  val stream = Stream
      .awakeEvery[IO](100.millis)
      .zipWithIndex
      .backpressureBracketSensor(reporter("pipe1"))(controlledPipe("pipe1"))
      .prefetch
      .backpressureBracketSensor(reporter("pipe2"))(controlledPipe("pipe2"))
      .as(())
