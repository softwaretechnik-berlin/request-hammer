package berlin.softwaretechnik.requestgun


import berlin.softwaretechnik.requestgun.Schedule.{cycle, scheduleAtConstantRate}
import sttp.model.StatusCode

import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object Main {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import sttp.client3._
    import scala.concurrent.duration._
    import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
    val backend = AsyncHttpClientFutureBackend()

    case class ResponseSummary(status: StatusCode, bodyLength: Option[Long])

    case class Phase(requestRate: Long, duration: Long, requestBuilder: () => Request[Either[String, String], Any]) {
      def createSchedule() =
        new Schedule[ResponseSummary](
          scheduleAtConstantRate(requestRate)
            .take((duration * requestRate).toInt)
            .map(time => ScheduledAsyncOperation(RelativeTime(time), () =>
              requestBuilder()
                .send(backend)
                .map(response => ResponseSummary(response.code, response.contentLength))
            ))
        )
    }

    def runPhase(phase: Phase): Unit = {

      val schedule = phase.createSchedule()

      val scheduler = new Scheduler

      val resultF = scheduler.run(schedule)
      val result: Seq[Timed[Try[ResponseSummary]]] = Await
        .result(Future.sequence(resultF), Duration(20, SECONDS))

      println(s"Running at ${phase.requestRate}req/s")
      println(result.map(r => {
        val string: String =
          r.result match {
            case Success(response) => "SUCCESS " + response.status
            case Failure(t) => "FAILURE " + t.getMessage.split("\\s").head
          }
        f"$string ${r.duration}%4d"
      }
      ).mkString("\n"))
      println("")
    }


    def request: Request[Either[String, String], Any] = {
      basicRequest
        .get(uri"https://wuetender-junger-mann.de")
    }

    val phases = Seq(
      Phase(2, 2, request _),
      Phase(10, 5, request _),
      Phase(100, 5, request _),
      Phase(200, 5, request _),
      Phase(500, 5, request _),
    )

    phases.map(runPhase(_))

    backend.close()
  }

}
