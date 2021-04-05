package berlin.softwaretechnik.requesthammer


import berlin.softwaretechnik.requesthammer.HttpPhaseScheduler.Resp
import berlin.softwaretechnik.requesthammer.Schedule.scheduleAtConstantRate
import sttp.client3.{Request, Response}

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object HttpPhaseScheduler {
  type Resp = Response[Either[String, String]]
}

case class Phase(
  requestRatePerSec: Long,
  durationSec: Long,
  requests: Seq[Request[Either[String, String], Any]]
)

sealed trait RequestOutcome

object Good extends RequestOutcome

object Bad extends RequestOutcome

sealed trait PhaseResult[+ResponseSummary]

object PhaseResult {
  val headings: String = "rq/s  total  succs  fails    P90   P99  P999"
}

case class ValidPhaseResult[ResponseSummary](
  requestRate: Long,
  duration: Long,
  results: Seq[(Try[ResponseSummary], Timed[RequestOutcome])]
) extends PhaseResult[ResponseSummary] {
  private lazy val sortedResults: Seq[(Try[ResponseSummary], Timed[RequestOutcome])] = results.sortBy(_._2.duration)

  def durationPercentile(filter: RequestOutcome => Boolean, percentile: Double): Long = {
    assert(percentile <= 1)
    assert(percentile >= 0)
    val filtered = sortedResults.filter(result => filter(result._2.result))
    if (filtered.isEmpty) {
      return Long.MaxValue
    }
    filtered((percentile * (filtered.length - 1)).toInt)._2.duration
  }

  override def toString: String = {
    f"${requestRate}%4d ${requestRate * duration}%6d ${results.count(_._2.result == Good)}%6d ${results.count(_._2.result == Bad)}%6d  ${durationPercentile(_ == Good, 0.90)}%5d ${durationPercentile(_ == Good, 0.99)}%5d ${durationPercentile(_ == Good, 0.999)}%5d"
  }
}

case class LaggingPhaseResult(
  requestRate: Long,
  duration: Long,
  toleranceInMillis: Long,
  numberOfScheduledOps: Long
) extends PhaseResult[Nothing]

case class TimeoutPhaseResult(exception: concurrent.TimeoutException) extends PhaseResult[Nothing]

class HttpPhaseScheduler(
  lagTolerance: Long = 200,
  overallTimeout: Duration = Duration(20, SECONDS)
)(implicit executor: ExecutionContext) {

  import sttp.client3._
  import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

  val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()


  def createSchedule(phase: Phase)(implicit executor: ExecutionContext): Schedule[Resp] = {
    new Schedule(
      scheduleAtConstantRate(phase.requestRatePerSec)
        .take((phase.durationSec * phase.requestRatePerSec).toInt)
        .zip(phase.requests)
        .map { case (time, request) => ScheduledAsyncOperation(RelativeTime(time), () =>
          request
            .send(backend)
        )
        }
    )
  }

  private def score(response: Try[Resp], score: Resp => RequestOutcome): RequestOutcome = {
    response match {
      case Failure(_) => Bad
      case Success(value) =>
        score(value)
    }
  }

  private def defaultScore(value: Resp): RequestOutcome = {
    if (value.code.isSuccess)
      Good
    else
      Bad
  }

  def run(phase: Phase): PhaseResult[Unit] = {
    run[Unit](phase, _ => ())
  }


  def run[ResponseSummary](
    phase: Phase,
    summarizer: Resp => ResponseSummary,
    scorer: Resp => RequestOutcome = defaultScore
  ): PhaseResult[ResponseSummary] = {
    val schedule = createSchedule(phase)

    val scheduler = new Scheduler(lagTolerance)
    val resultF = try {
      scheduler.run(schedule)
    } catch {
      case e: SchedulingException => return LaggingPhaseResult(
        requestRate = phase.requestRatePerSec,
        duration = phase.durationSec,
        toleranceInMillis = lagTolerance,
        numberOfScheduledOps = e.scheduledOps
      )
    }

    val result: Seq[Timed[Try[Resp]]] = try {
      Await
        .result(Future.sequence(resultF), overallTimeout)
    } catch {
      case e: TimeoutException => return TimeoutPhaseResult(e)
    }

    ValidPhaseResult(
      phase.requestRatePerSec,
      phase.durationSec,
      result.map(result => (result.result.map(summarizer), Timed(result.timestamp, result.duration, score(result.result, scorer)))
      ))
  }

  def close(): Unit = {
    backend.close()
  }
}
