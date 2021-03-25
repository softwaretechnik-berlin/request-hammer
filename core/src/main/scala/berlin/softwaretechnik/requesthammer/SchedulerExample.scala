package berlin.softwaretechnik.requesthammer

import java.time.Instant

import berlin.softwaretechnik.requesthammer.Schedule.scheduleAtConstantRate

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, Future}

object SchedulerExample {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val schedule = new Schedule[String](scheduleAtConstantRate(2).take(20)
       .map(time => ScheduledAsyncOperation(RelativeTime(time), () => Future{Thread.sleep(40); println(Instant.now);"Hello"}))
    )

    val scheduler = new Scheduler(100)
    val resultF = scheduler.run(schedule)

    val result = Await.result(Future.sequence(resultF), Duration(20, SECONDS))
    println(result.mkString("\n"))
  }

}
