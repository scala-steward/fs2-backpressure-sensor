package fs2.backpressuresensor

import cats.effect.kernel.Clock
import cats.effect.kernel.Resource
import cats.effect.kernel.MonadCancel
import fs2.Stream
import fs2.Pipe
import cats.effect.kernel.Async
import cats.Monad
import cats.effect.kernel.Sync

object syntax {
  implicit class StreamBaseOps[F[_]: Clock, T](stream: Stream[F, T]) {
    def backpressureSensor(reporterF: F[Reporter[F]]): Stream[F, T] =
      Stream
        .eval(reporterF)
        .flatMap { reporter =>
          stream.through(BackpressureSensor.sensor(reporter))
        }

    def backpressureSensor(reporter: Reporter[F]): Stream[F, T] =
      stream.through(BackpressureSensor.sensor(reporter))
  }

  private type MonadCancelThrow[F[_]] = MonadCancel[F, Throwable]
  implicit class StreamMonadOps[F[_]: MonadCancelThrow: Clock, T](
      stream: Stream[F, T]
  ) {
    def backpressureSensor(reporterR: Resource[F, Reporter[F]]): Stream[F, T] =
      Stream
        .resource(reporterR)
        .flatMap { reporter =>
          stream.through(BackpressureSensor.sensor(reporter))
        }
  }

  implicit class StreamBracketOps[F[_]: Monad: Async: Clock, T](
      stream: Stream[F, T]
  ) {
    def backpressureBracketSensor[U](reporter: Reporter[F])(
        pipe: Pipe[F, T, U]
    ): Stream[F, U] =
      stream.through(BackpressureSensor.bracket(reporter)(pipe))

    def backpressureBracketSensor[U](reporterF: F[Reporter[F]])(
        pipe: Pipe[F, T, U]
    ): Stream[F, U] =
      Stream
        .eval(reporterF)
        .flatMap { reporter =>
          stream.through(BackpressureSensor.bracket(reporter)(pipe))
        }

    def backpressureBracketSensor[U](reporterR: Resource[F, Reporter[F]])(
        pipe: Pipe[F, T, U]
    ): Stream[F, U] =
      Stream
        .resource(reporterR)
        .flatMap { reporter =>
          stream.through(BackpressureSensor.bracket(reporter)(pipe))
        }
  }
}
