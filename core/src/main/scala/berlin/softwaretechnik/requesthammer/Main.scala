package berlin.softwaretechnik.requesthammer

import berlin.softwaretechnik.requesthammer.Schedule.cycle
import sttp.client3.{Request, UriContext, basicRequest}

object Main {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    lazy val requests: Seq[Request[Either[String, String], Any]] =
      cycle(
        Seq(
          basicRequest.get(uri"https://myservice/testA"),
          basicRequest.get(uri"https://myservice/testB"),
          basicRequest.get(uri"https://myservice/testC"),
        )
      )

    val phases = Seq(
      Phase(requestRatePerSec =  2, durationSec = 2, requests),
      Phase(requestRatePerSec = 10, durationSec = 5, requests),
      Phase(requestRatePerSec = 20, durationSec = 5, requests),
      Phase(requestRatePerSec = 30, durationSec = 5, requests),
    )

    val scheduler = new HttpPhaseScheduler

    println(PhaseResult.headings)
    phases.foreach( phase =>
      println(scheduler.run(phase))
    )

    scheduler.close()
  }

}
