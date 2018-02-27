package flows

import java.net.URLEncoder
import java.sql.Connection
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import model._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class GoogleApi extends StrictLogging{

  protected def buildUrl(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"

  def buildFlow(connection: Connection,
                tableName: String,
                googleApiKey: GoogleApiKey,
                maxGoogleAPIOpenRequests: Int,
                throttleGoogleAPIOpenRequests: FiniteDuration,
                maxGoogleAPIFatalErrors: Int,
                maxEntries: Int)
               (implicit
                executionContext: ExecutionContext,
                actorSystem: ActorSystem,
                materialize: Materializer): Source[GoogleApiResponse, NotUsed] = {

    val http = Http(actorSystem)

    val queryGoogleApiFlow: Flow[GeoCode, GoogleApiResult, _] = Flow[GeoCode].mapAsync(maxGoogleAPIOpenRequests) { geoCode: GeoCode =>
      val uri = buildUrl(googleApiKey.value, geoCode.unformattedAddress)
      // request google api
      http.singleRequest(request = HttpRequest(uri = uri)).flatMap {
        case resp@HttpResponse(status, headers, entity, protocol) if (status == StatusCodes.OK) =>
          entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            .map(_.utf8String)
            .map(r => Right(GoogleApiResponse(geoCode.id, r)))
        case resp@HttpResponse(status, headers, entity, protocol) if (status != StatusCodes.OK) =>
          resp.discardEntityBytes()
          val msg = s"Bad status code $status for $uri!"
          logger.error(msg)
          Future.successful(Left(msg))
      }
        .recover {
          case error: akka.stream.StreamTcpException =>
            val msg = s"Error ${error.getMessage} for $uri!"
            logger.error(msg)
            throw new TimeoutException(msg)
          case error =>
            val msg = s"Error ${error.getMessage} for $uri!"
            logger.error(msg)
            Left(msg)
        }
    }

    //
    val killSwitch = KillSwitches.shared("numFatalErrors")
    val numFatalErrors = new AtomicInteger(maxGoogleAPIFatalErrors - 1)
    val countAndFilterFatalErrors: Flow[GoogleApiResult, GoogleApiResponse, NotUsed] =
      Flow[GoogleApiResult].map {
        case Right(r) => List(r)
        case Left(error) =>
          val errorsLeft = numFatalErrors.getAndDecrement()
          logger.error(s"fatalError #{} errorsLeft={}: {}!",
            maxGoogleAPIFatalErrors - errorsLeft, errorsLeft, error)
          if (errorsLeft <= 0) {
            logger.error(s"MaxFatalErrors reached. stopping {self.path.name}")
            //                system.terminate()
            killSwitch.shutdown()
          }
          List.empty
      }.mapConcat(identity)

    val googleApiResponse: Source[GoogleApiResult, NotUsed] =
      DAO.getAddressesWithEmptyGoogleResponseFromDatabase(connection, tableName, maxEntries)
        .throttle(maxGoogleAPIOpenRequests, throttleGoogleAPIOpenRequests, 0, ThrottleMode.Shaping)
        .via(queryGoogleApiFlow)
        .mapMaterializedValue(_ => NotUsed)

    googleApiResponse
      .recoverWithRetries(-1, { case _ => googleApiResponse })
      .via(killSwitch.flow)
      .via(countAndFilterFatalErrors)
      .alsoTo(Sink.foreachParallel(maxGoogleAPIOpenRequests)(
        DAO.saveGoogleResponse(connection, tableName)
      ))

    }

}



