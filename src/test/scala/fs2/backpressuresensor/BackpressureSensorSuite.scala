package fs2.backpressuresensor

import munit.CatsEffectSuite
import cats.effect.testkit.TestControl
import cats.effect.IO
import fs2.Stream

import fs2.backpressuresensor.syntax._
import scala.concurrent.duration._

class BackpressureSensorSuite extends CatsEffectSuite:
  test("sensor correctly measure starvation from upstream"):
    val iterations = 10
    val program = 
      for
        r <- TestReporter()
        _ <- Stream.awakeEvery[IO](1.second)
          .backpressureSensor(r)
          .take(iterations)
          .compile
          .drain
      yield r

    for 
      result <- TestControl.executeEmbed(program)

      _ <- result.getStarvedDuration.map(_.toMillis).assertEquals(1000 * iterations)
      _ <- result.getBackpressureDuration.map(_.toMillis).assertEquals(0)
    yield ()

  test("sensor correctly measure backpressure from downstream"):
    val iterations = 10
    val program = 
      for
        r <- TestReporter()
        _ <- Stream.constant(()).covary[IO]
          .backpressureSensor(r)
          .evalTap(_ => IO.sleep(1.second))
          .take(iterations)
          .compile
          .drain
      yield r

    for 
      result <- TestControl.executeEmbed(program)

      _ <- result.getStarvedDuration.map(_.toMillis).assertEquals(0)
      _ <- result.getBackpressureDuration.map(_.toMillis).assertEquals(1000 * (iterations - 1))
    yield ()

  test("chained sensors measure subset of total backpressure"):
    val iterations = 10
    val program = 
      for
        r1 <- TestReporter()
        r2 <- TestReporter()
        _ <- Stream.constant(()).covary[IO]
          .backpressureSensor(r1)
          .evalTap(_ => IO.sleep(100.millis))
          .backpressureSensor(r2)
          .evalTap(_ => IO.sleep(1.second))
          .take(iterations)
          .compile
          .drain
      yield r1 -> r2

    for 
      result <- TestControl.executeEmbed(program)
      (r1, r2) = result 

      _ <- r1.getStarvedDuration.map(_.toMillis).assertEquals(0)
      _ <- r2.getStarvedDuration.map(_.toMillis).assertEquals(100 * iterations)

      s1Backpressure = 100 * (iterations - 1)
      s2Backpressure = 1000 * (iterations - 1)
      _ <- r1.getBackpressureDuration.map(_.toMillis).assertEquals(s1Backpressure + s2Backpressure)
      _ <- r2.getBackpressureDuration.map(_.toMillis).assertEquals(s2Backpressure)
    yield ()

  test("chained sensors measure subset of total starvation"):
    val iterations = 10
    val program = 
      for
        r1 <- TestReporter()
        r2 <- TestReporter()
        _ <- Stream.constant(()).covary[IO]
          .evalTap(_ => IO.sleep(1.second))
          .backpressureSensor(r1)
          .evalTap(_ => IO.sleep(100.millis))
          .backpressureSensor(r2)
          .take(iterations)
          .compile
          .drain
      yield r1 -> r2

    for 
      result <- TestControl.executeEmbed(program)
      (r1, r2) = result 

      s1Starvation = 1000 * iterations
      s2Starvation = 100 * iterations
      _ <- r1.getStarvedDuration.map(_.toMillis).assertEquals(s1Starvation)
      _ <- r2.getStarvedDuration.map(_.toMillis).assertEquals(s1Starvation + s2Starvation)

      _ <- r1.getBackpressureDuration.map(_.toMillis).assertEquals(100 * (iterations -1))
      _ <- r2.getBackpressureDuration.map(_.toMillis).assertEquals(0)
    yield ()

  test("braketed sensors only measure contained pipe starvation and backpressure"):
    val iterations = 10
    val program = 
      for
        r1 <- TestReporter()
        r2 <- TestReporter()
        _ <- Stream.constant(()).covary[IO]
          .evalTap(_ => IO.sleep(1.second))
          .backpressureBracketSensor(r1) { s1 =>
            s1.evalTap(_ => IO.sleep(100.millis))
              .backpressureBracketSensor(r2) {  s2 =>
                s2.take(iterations)
              }
          }
          .evalTap(_ => IO.sleep(1.second))
          .compile
          .drain
      yield r1 -> r2

    for 
      result <- TestControl.executeEmbed(program)
      (r1, r2) = result 

      s1Starvation = 100 * iterations
      s2Starvation = 0
      _ <- r1.getStarvedDuration.map(_.toMillis).assertEquals(s1Starvation)
      _ <- r2.getStarvedDuration.map(_.toMillis).assertEquals(s2Starvation)

      _ <- r1.getBackpressureDuration.map(_.toMillis).assertEquals(100 * (iterations -1))
      _ <- r2.getBackpressureDuration.map(_.toMillis).assertEquals(0)
    yield ()
