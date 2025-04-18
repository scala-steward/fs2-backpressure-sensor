package fs2.backpressuresensor

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration
import cats.effect.kernel.Ref
import scala.concurrent.duration.Duration
import java.time.ZoneOffset

class TestReporter private (
    starvingDurationAcc: Ref[IO, FiniteDuration],
    backpressureDurationAcc: Ref[IO, FiniteDuration]
) extends Reporter[IO]:
  def reportStarvedFor(duration: FiniteDuration): IO[Unit] =
    starvingDurationAcc.update(_ + duration)

  def reportBackpressuredFor(duration: FiniteDuration): IO[Unit] =
    backpressureDurationAcc.update(_ + duration)

  def getStarvedDuration: IO[FiniteDuration] =
    starvingDurationAcc.get

  def getBackpressureDuration: IO[FiniteDuration] =
    backpressureDurationAcc.get

object TestReporter:
  def apply(): IO[TestReporter] =
    for {
      starvationAcc <- Ref.of[IO, FiniteDuration](Duration.Zero)
      backpressureAcc <- Ref.of[IO, FiniteDuration](Duration.Zero)
    } yield new TestReporter(starvationAcc, backpressureAcc)
