package akka_cookbook.chapter8_stream.examples_akka_io.modularity

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl._
import akka.stream.{Attributes, FlowShape, Graph}
import scala.concurrent.duration._

/**
  * @author Xiangnan Ren
  */
object Throttler {
  import GraphDSL.Implicits._
  private val AkkaSchedulerInterval = 10.millis

  def create[T](throttleSettings: IntervalBasedThrottlerSettings):
  Graph[FlowShape[T, T], NotUsed] =
    GraphDSL.create() { implicit builder =>
      val zip = builder.add(ZipWith[T, Unit, T]((i1, i2) => i1))

      ticksSource(throttleSettings) ~> zip.in1

      FlowShape(zip.in0, zip.out)
    }.withAttributes(Attributes.inputBuffer(initial = 1, max = 1)).named("throttle")

  private def ticksSource(throttleSettings: IntervalBasedThrottlerSettings): Source[Unit, Cancellable] = {
    // Akka scheduler is lightweight but has limited frequency (up to 1ms). By default it's 10ms,
    // which would allow us to pass max 100 msg/s. Thus in case of desirable throttle throughput
    // greater than 100 msg/s we are passing more than one tick at a time (e.g. if you need to
    // send 200 msg/s, then this tick source will emit 2 ticks in 10ms intervals, instead of trying
    // to send 1 tick / 5ms).
    if (throttleSettings.interval < AkkaSchedulerInterval) {
      val factor: Int = (10.millis.toNanos / throttleSettings.interval.toNanos).toInt
      val tickSource = Source.tick(Duration.Zero, throttleSettings.interval * factor, ())
      tickSource.mapConcat(_ => List.fill(factor)(()))
    } else {
      Source.tick(Duration.Zero, throttleSettings.interval, ())
    }
  }
}

case class IntervalBasedThrottlerSettings(numberOfOps: Int,
                                            duration: FiniteDuration) {
  lazy val interval: FiniteDuration = {
    val oneUnitInNanos: Long = duration.toNanos
    val intervalInNanos = oneUnitInNanos / numberOfOps
    intervalInNanos.nanos
  }
}

object IntervalBasedThrottlerSettings {
  implicit class ThrottleSettingsBuilder(val numberOfOps: Int) extends AnyVal {
    def per(duration: FiniteDuration): IntervalBasedThrottlerSettings =
      IntervalBasedThrottlerSettings(numberOfOps, duration)
    def perSecond: IntervalBasedThrottlerSettings = per(1.second)
  }
}
