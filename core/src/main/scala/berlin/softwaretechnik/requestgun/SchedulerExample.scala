package berlin.softwaretechnik.requestgun

import java.time.Instant

import berlin.softwaretechnik.requestgun.Schedule.scheduleAtConstantRate

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, Future, blocking}

object SchedulerExample {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val schedule = new Schedule[String](scheduleAtConstantRate(2).take(20)
       .map(time => ScheduledAsyncOperation(RelativeTime(time), () => Future{Thread.sleep(40); println(Instant.now);"Hello"}))
    )

    val scheduler = new Scheduler
    val resultF = scheduler.run(schedule)
                           // Future.sequence is not your friend
    val result = Await.result(Future.sequence(resultF), Duration(20, SECONDS))
    println(result.mkString("\n"))
  }

}
