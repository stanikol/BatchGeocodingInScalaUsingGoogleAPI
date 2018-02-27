package fakeGeoApi

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import fakeGeoApi.test_responses.{GeocodeSampleOk, GeocodeSampleRequestDenied, InvalidApiKey, OdessaUkraine}

import scala.io.StdIn

object FakeGeoApiServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val jsonReply = HttpEntity(ContentTypes.`application/json`, OdessaUkraine.jsonText)
    val jsonReplyInvalid = HttpEntity(ContentTypes.`application/json`, InvalidApiKey.jsonText)

    val counter = new AtomicLong(0L)

    val route =
      get {
        path(Remaining) { remainingPath =>
          parameterMap { params =>
            println(remainingPath, params.toString(), counter.getAndIncrement())
            val reply = if(params.values.exists(_.toLowerCase.contains("invalid"))) jsonReplyInvalid
                        else jsonReply
            Thread.sleep(1000)
            complete(reply)
//            complete(StatusCodes.Forbidden -> "message")
          }
        }
      }

    val portNumber = 12500

    val bindingFuture = Http().bindAndHandle(route, "localhost", portNumber)

    println(s"Server online at http://localhost:$portNumber/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
    // and shutdown when done
  }
}