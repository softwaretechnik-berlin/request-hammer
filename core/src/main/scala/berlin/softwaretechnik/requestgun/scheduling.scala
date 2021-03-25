package berlin.softwaretechnik.requestgun

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class RelativeTime(value: Long) extends AnyVal

case class ScheduledAsyncOperation[T](time: RelativeTime, operation: () => Future[T])

object Schedule {
  def scheduleAtConstantRate(requestsPerSecond: Double): Seq[Long] = {
    LazyList.from(0).map { i => (i * 1.0 / requestsPerSecond * 1000).toLong }
  }

  def cycle[T](seq: Seq[T]): LazyList[T] = {
    assert(seq.nonEmpty, "Cannot cycle over an empty sequence!")
    LazyList.continually(seq).flatten
  }
}

case class Schedule[T](operations: Seq[ScheduledAsyncOperation[T]]) {
  private def valid: Boolean = {
    val times = operations.map(_.time)
    times.zip(times.tail).forall { case (t0, t1) => t0.value <= t1.value }
  }

  assert(valid, "Schedule is not valid")
}

case class Timed[+T](timestamp: Instant, duration: Long, result: T)

class Scheduler(implicit executor: ExecutionContext) {
  private def now = System.currentTimeMillis()

  def run[T](schedule: Schedule[T]): Seq[Future[Timed[Try[T]]]] = {
    val startTime = now

    val futureResponses = schedule.operations.toList.map { case ScheduledAsyncOperation(scheduledTime, fun) =>
      val currentTime = now - startTime
      if (scheduledTime.value > currentTime) {
        val sleepTime: Long = scheduledTime.value - currentTime
        Thread.sleep(sleepTime)
      }
      val requestStartTime = now
      def duration = now - requestStartTime
      val fResult: Future[Timed[Try[T]]] = fun()
        .map(a => Timed(Instant.ofEpochMilli(requestStartTime), duration, Success(a)))
        .recover(ex => Timed(Instant.ofEpochMilli(requestStartTime), duration, Failure(ex)))
      fResult
    }
    futureResponses
  }
}
