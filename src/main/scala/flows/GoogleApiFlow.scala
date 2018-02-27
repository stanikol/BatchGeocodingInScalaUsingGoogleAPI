package flows

import java.net.URLEncoder
import java.sql.Connection
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

import akka.pattern.after
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.stream._
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import model.{GeoCode, GoogleApiKey, GoogleApiResponse, GoogleApiResult}
import com.typesafe.scalalogging.StrictLogging

import concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class GoogleApiFlow extends StrictLogging{

  protected def buildUrl(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"

  def buildFlow(connection: Connection,
                tableName: String,
                googleApiKey: GoogleApiKey,
                maxGoogleAPIOpenRequests: Int,
                maxGoogleAPIFatalErrors: Int)
               (implicit
                executionContext: ExecutionContext,
                actorSystem: ActorSystem,
                materialize: Materializer) =
    new FlowBuilder(connection, tableName, googleApiKey, maxGoogleAPIOpenRequests, maxGoogleAPIFatalErrors).build


  private class FlowBuilder(connection: Connection,
                            tableName: String,
                            googleApiKey: GoogleApiKey,
                            maxGoogleAPIOpenRequests: Int,
                            maxGoogleAPIFatalErrors: Int)
                           (implicit executionContext: ExecutionContext,
                            actorSystem: ActorSystem,
                            materialize: Materializer)
  {

    val http = Http(actorSystem)

    val queryGoogleApiFlow: Flow[GeoCode, GoogleApiResult, _] = Flow[GeoCode].mapAsync(maxGoogleAPIOpenRequests){ geoCode: GeoCode =>
      val uri = buildUrl(googleApiKey.value, geoCode.unformattedAddress)

      val httpFuture = http.singleRequest(request = HttpRequest(uri = uri)).flatMap{
        case resp @ HttpResponse(status, headers, entity, protocol) if(status == StatusCodes.OK) =>
          entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            .map(_.utf8String)
            .map(r => Right(GoogleApiResponse(geoCode.id, r)))
        case resp @ HttpResponse(status, headers, entity, protocol) if(status != StatusCodes.OK) =>
          resp.discardEntityBytes()
          Future.successful(Left(s"Bad status code $status for $uri!"))
      }
      .recover{ case error: akka.stream.StreamTcpException =>
        val msg = s"Error ${error.getMessage} for $uri!"
        logger.error(msg)
        Left(msg)
          throw new TimeoutException(msg)
      }
    httpFuture
//      Future.firstCompletedOf(List(httpFuture,
//                                after(3.second, actorSystem.scheduler)(Future.failed(new TimeoutException(uri)))))
    }//.withAttributes(ActorAttributes.supervisionStrategy(decider))
//      .recoverWithRetries(-1, {case _ => Source.single(1)})

    def build: Flow[GeoCode, GoogleApiResponse, _] = {
      //
      val killSwitch = KillSwitches.shared("numFatalErrors")
      val numFatalErrors = new AtomicInteger(maxGoogleAPIFatalErrors - 1)
      val countFatalErrors: Flow[GoogleApiResult, GoogleApiResponse, NotUsed] =
        Flow[GoogleApiResult].map {
          case Right(r) => List(r)
          case Left(error) =>
            val errorsLeft = numFatalErrors.getAndDecrement()
            logger.error(s"fatalError #{} errorsLeft={}: {}!",
              maxGoogleAPIFatalErrors - errorsLeft, errorsLeft, error)
            if (errorsLeft <= 0) {
              logger.info(s"MaxFatalErrors reached. stopping {self.path.name}")
              //                system.terminate()
              killSwitch.shutdown()
            }
            List.empty
        }.mapConcat(identity)

      val flow: Flow[GeoCode, GoogleApiResponse, _] = queryGoogleApiFlow
//          .recoverWithRetries(-1, {case _ => queryGoogleApiFlow.initialDelay(10.second)})
        .via(killSwitch.flow)
        .via(countFatalErrors)
        .alsoTo(SaveApiResponseResultSink.buildSink(connection, tableName))

      flow
    }
  }


  val decider: Supervision.Decider = {
    case exept ⇒
      logger.error(exept.getMessage)
      Supervision.Restart
  }

}



