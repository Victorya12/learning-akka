package akka_cookbook.chapter9_http.sec2_consume_from_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Success

/**
  * @author Xiangnan Ren
  */
object HostLevelClientAPIApplication extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val poolClientFlow = Http().cachedHostConnectionPoolHttps[String]("api.github.com")
  val akkaToolkitRequest = HttpRequest(uri = "/repos/akka/akka-http") -> """.*"open_issues":(.*?),."""
  val responseFuture = Source.single(akkaToolkitRequest).via(poolClientFlow).runWith(Sink.head)

  responseFuture.andThen{
    case Success(result) =>
      val (tryResponse, regex) = result
      tryResponse match {
        case Success(response) =>
          response.entity.toStrict(5.seconds).
            map(_.data.decodeString("UTF-8")).andThen{
            case Success(json) =>
              val pattern = regex.r
              pattern.findAllIn(json).matchData.foreach{ m =>
                println(s"There are ${m.group(1)} open issues in Akka Http.")
                materializer.shutdown()

                system.terminate()
              }
            case _ =>
          }
        case _=> println("request failed")
      }
    case _=> println("request failed")
  }
}
