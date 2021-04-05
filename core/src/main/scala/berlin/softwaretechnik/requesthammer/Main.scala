package berlin.softwaretechnik.requesthammer

import berlin.softwaretechnik.requesthammer.Schedule.cycle
import sttp.client3.{Request, UriContext, basicRequest}
import sttp.model.StatusCode

object Main {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    lazy val requests: Seq[Request[Either[String, String], Any]] =
      cycle(
        Seq(
          basicRequest.get(uri"https://wuetender-junger-mann.de"),
          //          basicRequest.get(uri"https://myservice/testB"),
          //          basicRequest.get(uri"https://myservice/testC"),
        )
      )

    val phases = Seq(
      Phase(requestRatePerSec = 2, durationSec = 2, requests),
      Phase(requestRatePerSec = 10, durationSec = 5, requests),
      Phase(requestRatePerSec = 20, durationSec = 5, requests),
      Phase(requestRatePerSec = 30, durationSec = 5, requests),
    )

    val scheduler = new HttpPhaseScheduler
    case class ResponseSummary(statusCode: StatusCode, length: Int)
    println(PhaseResult.headings)
    phases.foreach(phase => {
      val value: PhaseResult[ResponseSummary] = scheduler.run[ResponseSummary](phase, summarizer = resp => ResponseSummary(resp.code, resp.body.toOption.map(_.length).getOrElse(-1)))
      val summaries: Seq[String] = value match {
        case ValidPhaseResult(_,_, results) => results.map(result =>
          result._1.map(s => s.statusCode.toString() + " " + s.length).getOrElse("-  -")
        )
        case _ => Seq.empty
      }
      println(summaries.mkString("\n"))
      println(value)
    }
    )

    scheduler.close()
  }

}
