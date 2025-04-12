package fs2.backpressuresensor

import cats.effect.kernel.Clock
import fs2.Stream
import fs2.Pipe
import cats.effect.kernel.Async
import cats.Monad

object syntax:
  implicit class StreamBaseOps[F[_]: Clock, T](stream: Stream[F, T]):
    def backpressureSensor(reporter: Reporter[F]): Stream[F, T] =
      stream.through(BackpressureSensor.sensor(reporter))

  implicit class StreamBracketOps[F[_]: Monad : Async : Clock, T](stream: Stream[F, T]):
    def backpressureBracketSensor[U](reporter: Reporter[F])(pipe: Pipe[F, T, U]): Stream[F, U] =
      stream.through(BackpressureSensor.bracket(reporter)(pipe))

