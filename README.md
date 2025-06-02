[![fs2-backpressure-sensor Scala version support](https://index.scala-lang.org/nivox/fs2-backpressure-sensor/fs2-backpressure-sensor/latest.svg)](https://index.scala-lang.org/nivox/fs2-backpressure-sensor/fs2-backpressure-sensor)

# FS2 Backpressure Sensor

A lightweight, composable library for monitoring backpressure in [FS2](https://fs2.io) streams.

## Introduction

FS2 Backpressure Sensor provides tools to monitor and measure backpressure in your functional streams. 
This library helps you understand where bottlenecks occur in your stream processing by tracking:

- **Starvation**: How long a stream waits for upstream data
- **Backpressure**: How long a stream waits for downstream processing

The sensor can be applied to any FS2 stream with minimal changes to your code, making it ideal for both debugging and monitoring production systems.

## Installation

Add the dependency to your build.sbt:

```scala
libraryDependencies += "io.github.nivox" %% "fs2-backpressure-sensor" % "<version>"
```
## Usage

The library offers 2 types of sensors:
- *plain sensor*: measure backpressure at a specific point of the stream
- *bracket sensor*: measure backpressure contribution of a portion of the stream

### Plain Sensor

A plain sensor measures backpressure at a single point in your stream:

```scala
import fs2.backpressuresensor._
import fs2.backpressuresensor.syntax._
import cats.effect._
import fs2._
import scala.concurrent.duration._

object PlainSensorExample extends IOApp.Simple:
  def run: IO[Unit] =
    // Create a reporter that logs backpressure stats every second
    val reporter = Reporter.interval[IO](1.second) { (starvation, backpressure) =>
      IO.println(s"Starvation: ${starvation.toMillis}ms, Backpressure: ${backpressure.toMillis}ms")
    }
    
    // Your source stream
    val source = Stream.iterate(0)(_ + 1)
      .metered(10.millis)
    
    // Apply the backpressure sensor to your stream
    source
      .backpressureSensor(reporter)  // <-- Add sensor here
      .evalMap(n => IO.sleep(50.millis) >> IO.println(s"Processing: $n"))
      .take(100)
      .compile
      .drain
```

### Bracketed Sensor

A bracketed sensor can measure backpressure around a specific pipe transformation, helping you 
understand which part of your stream is causing bottlenecks:

```scala
import fs2.backpressuresensor._
import fs2.backpressuresensor.syntax._
import cats.effect._
import fs2._
import scala.concurrent.duration._

object BracketedSensorExample extends IOApp.Simple:
  def run: IO[Unit] =
    // Create a reporter that logs backpressure stats every second
    val reporter = Reporter.interval[IO](1.second) { (starvation, backpressure) =>
      IO.println(s"Starvation: ${starvation.toMillis}ms, Backpressure: ${backpressure.toMillis}ms")
    }
    
    // Define your transformation pipe
    val processingPipe: Pipe[IO, Int, String] = 
      _.evalMap(n => IO.sleep(50.millis) >> IO.pure(s"Processed: $n"))
    
    // Your source stream
    val source = Stream.iterate(0)(_ + 1)
      .metered(10.millis)
    
    // Apply the bracketed sensor around your processing pipe
    source
      .backpressureBracketSensor(reporter)(processingPipe)  // <-- Wrap the pipe with a sensor
      .evalTap(s => IO.println(s))
      .take(100)
      .compile
      .drain
```

### Advanced Usage

You can create custom reporters by implementing the `Reporter[F]` trait:

```scala
trait Reporter[F[_]]:
  def reportStarvedFor(duration: FiniteDuration): F[Unit]
  def reportBackpressuredFor(duration: FiniteDuration): F[Unit]
```

## Inspiration

This project was inspired by [https://github.com/timbertson/backpressure-sensor](https://github.com/timbertson/backpressure-sensor).
```

