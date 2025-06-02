package fs2.backpressuresensor

import cats.effect.IO
import cats.effect.testkit.TestControl
import fs2.Stream
import fs2.backpressuresensor.syntax._
import munit.CatsEffectSuite

import scala.concurrent.duration._

class BackpressureSensorSuite extends CatsEffectSuite {
  test("sensor correctly measure starvation from upstream") {
    val iterations = 10
    val program =
      for {
        r <- TestReporter()
        _ <- Stream
          .awakeEvery[IO](1.second)
          .backpressureSensor(r)
          .take(iterations)
          .compile
          .drain
      } yield r

    for {
      result <- TestControl.executeEmbed(program)

      _ <- result.getStarvedDuration
        .map(_.toMillis)
        .assertEquals(1000L * iterations)
      _ <- result.getBackpressureDuration.map(_.toMillis).assertEquals(0L)
    } yield ()
  }

  test("sensor correctly measure backpressure from downstream") {
    val iterations = 10
    val program =
      for {
        r <- TestReporter()
        _ <- Stream
          .constant(())
          .covary[IO]
          .backpressureSensor(r)
          .evalTap(_ => IO.sleep(1.second))
          .take(iterations)
          .compile
          .drain
      } yield r

    for {
      result <- TestControl.executeEmbed(program)

      _ <- result.getStarvedDuration.map(_.toMillis).assertEquals(0L)

      // Last element does not generate backpressure as there's no pull after it to
      // trigger the backpressure computation
      backpressure = 1000L * (iterations - 1)
      _ <- result.getBackpressureDuration
        .map(_.toMillis)
        .assertEquals(backpressure)
    } yield ()
  }

  test("chained sensors measure subset of total backpressure") {
    val iterations = 10
    val program =
      for {
        r1 <- TestReporter()
        r2 <- TestReporter()
        _ <- Stream
          .constant(())
          .covary[IO]
          .backpressureSensor(r1)
          .evalTap(_ => IO.sleep(100.millis))
          .backpressureSensor(r2)
          .evalTap(_ => IO.sleep(1.second))
          .take(iterations)
          .compile
          .drain
      } yield r1 -> r2

    for {
      result <- TestControl.executeEmbed(program)
      (r1, r2) = result

      _ <- r1.getStarvedDuration.map(_.toMillis).assertEquals(0L)
      _ <- r2.getStarvedDuration.map(_.toMillis).assertEquals(100L * iterations)

      // Last element does not generate backpressure as there's no pull after it to
      // trigger the backpressure computation
      s1Backpressure = 100L * (iterations - 1)
      s2Backpressure = 1000L * (iterations - 1)
      _ <- r1.getBackpressureDuration
        .map(_.toMillis)
        .assertEquals(s1Backpressure + s2Backpressure)
      _ <- r2.getBackpressureDuration
        .map(_.toMillis)
        .assertEquals(s2Backpressure)
    } yield ()
  }

  test("chained sensors measure subset of total starvation") {
    val iterations = 10
    val program =
      for {
        r1 <- TestReporter()
        r2 <- TestReporter()
        _ <- Stream
          .constant(())
          .covary[IO]
          .evalTap(_ => IO.sleep(1.second))
          .backpressureSensor(r1)
          .evalTap(_ => IO.sleep(100.millis))
          .backpressureSensor(r2)
          .take(iterations)
          .compile
          .drain
      } yield r1 -> r2

    for {
      result <- TestControl.executeEmbed(program)
      (r1, r2) = result

      s1Starvation = 1000L * iterations
      s2Starvation = 100L * iterations
      _ <- r1.getStarvedDuration.map(_.toMillis).assertEquals(s1Starvation)
      _ <- r2.getStarvedDuration
        .map(_.toMillis)
        .assertEquals(s1Starvation + s2Starvation)

      // Last element does not generate backpressure as there's no pull after it to
      // trigger the backpressure computation
      s1Backpressure = 100L * (iterations - 1)
      _ <- r1.getBackpressureDuration
        .map(_.toMillis)
        .assertEquals(s1Backpressure)
      _ <- r2.getBackpressureDuration.map(_.toMillis).assertEquals(0L)
    } yield ()
  }

  test(
    "bracketed sensors only measure backpressure contribution of contained pipe"
  ) {
    val iterations = 3
    val program =
      for {
        r1 <- TestReporter()
        r2 <- TestReporter()
        _ <- Stream
          .constant(())
          .covary[IO]
          .evalTap(_ => IO.sleep(1.second))
          .backpressureBracketSensor(r1) { s =>
            s.evalTap(_ => IO.sleep(100.millis))
          }
          .backpressureBracketSensor(r2) { s =>
            s.evalTap(_ => IO.sleep(50.millis))
          }
          .evalTap(_ => IO.sleep(1.second))
          .take(iterations)
          .compile
          .drain
      } yield r1 -> r2

    for {
      result <- TestControl.executeEmbed(program)
      (r1, r2) = result

      s1Starvation = 1000L * iterations
      s2Starvation = (1000L * iterations) + (100L * iterations)
      _ <- r1.getStarvedDuration.map(_.toMillis).assertEquals(s1Starvation)
      _ <- r2.getStarvedDuration.map(_.toMillis).assertEquals(s2Starvation)

      // Fist element does not generate backpressure as the delay comes from within the
      // pipe itself. Further elements will be blocked by downstram thus counting as
      // backpressure for the bracket.
      //
      // Last element does not generate backpressure as there's no pull after it to
      // trigger the backpressure computation
      _ <- r1.getBackpressureDuration
        .map(_.toMillis)
        .assertEquals(100L * (iterations - 2))
      _ <- r2.getBackpressureDuration
        .map(_.toMillis)
        .assertEquals(50L * (iterations - 2))
    } yield ()
  }
}
