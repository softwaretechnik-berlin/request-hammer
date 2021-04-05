# request-hammer
A Tool(kit) to Test Services with Defined Request Rates.

**Note** this is only a sketch. That being said, it seems to 
be good enough to hit an HTTP service with request rates of 
up to a few hundred requests per second. 

Here is an example how you might construct your test:

~~~.scala
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
phases.foreach( phase =>
  println(scheduler.run(phase))
)

scheduler.close()
~~~

Which will print a result along the lines of the following:

~~~
rq/s  total  succs  fails    P90   P99  P999
   2      4      4      0    183   183   183
  10     50     50      0     43    58    58
  20    100    100      0     62   130   130
  30    150    150      0     60   130   155
~~~

## Ideas/ Issues

* [ ] Strong timeout for individual requests, to avoid `Future.sequence` timeout.
* [ ] Write a couple of tests.  
* [ ] Pass scoring function into the scheduler alongside the request.
* [ ] Introduce some sort of tagging for the request type, so that we 
      can analyse latency some more.
* [ ] Facility to write full request and response log.
* [ ] Add simple commandline tool that can take requests from file.
* [ ] Add binary chop example to show how to determine where the system under
      test collapses.
* [ ] Tune http client to really understand what we are really doing  
* [ ] Provide simple graphing (flexivis!).
* [ ] Use timer facility rather than Thread.sleep to do the scheduling.
