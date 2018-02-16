package akka_parser.flows

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import akka_parser.model.{GeoCode, GoogleApiKey, GoogleApiResponse, GoogleApiResult}

import scala.concurrent.{ExecutionContext, Future}

class GoogleApiCall {

  protected def buildUrl(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/jsonText?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"

  def buildFlow(googleApiKey: GoogleApiKey,
                maxGoogleAPIOpenRequests: Int)
               (implicit
                executionContext: ExecutionContext,
                actorSystem: ActorSystem,
                materialize: Materializer) =
    new FlowBuilder(googleApiKey, maxGoogleAPIOpenRequests).build


  private class FlowBuilder(googleApiKey: GoogleApiKey, maxGoogleAPIOpenRequests: Int)
                   (implicit actorSystem: ActorSystem,
                    materialize: Materializer) {

    val http = Http(actorSystem)

    def build(implicit executionContext: ExecutionContext): Flow[GeoCode, GoogleApiResult, _] = Flow[GeoCode].mapAsync(maxGoogleAPIOpenRequests){ geoCode: GeoCode =>
      val uri = buildUrl(googleApiKey.value, geoCode.unformattedAddress)
      http.singleRequest(HttpRequest(uri = uri)).flatMap{
        case resp @ HttpResponse(status, headers, entity, protocol) if(status == StatusCodes.OK) =>
          entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            .map(_.utf8String)
            .map(r => Right(GoogleApiResponse(geoCode.id, r)))
        case resp @ HttpResponse(status, headers, entity, protocol) if(status != StatusCodes.OK) =>
          resp.discardEntityBytes()
          Future.successful(Left(s"Error $status"))
      }.recover{case error => Left(s"Error ${error.getMessage}")}
    }
  }

}



