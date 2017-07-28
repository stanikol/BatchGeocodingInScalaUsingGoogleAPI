import DB.SaveGoogleResponse
import Utils.textSample
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString

object GoogleGeocoder {
  def props(googleApiKey: String, db: ActorRef, addressParser: ActorRef): Props = Props(new GoogleGeocoder(googleApiKey, db, addressParser))

  final case class GeoCode(unformattedAddress: String)
}

class GoogleGeocoder(googleApiKey: String, db: ActorRef, addressParser: ActorRef) extends Actor with ActorLogging {
  import GoogleGeocoder._
  import AddressParserActor._
  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)

  var numFatalErrors = 0
  val MaxFatalErrors = 5

  def receive = {
    case GeoCode(unformattedAddress) =>
      log.info(s"GeoCode $unformattedAddress")
      val url = AddressParser.url(googleApiKey, unformattedAddress)
      http
        .singleRequest(HttpRequest(uri = url))
        .map(r => (unformattedAddress, r))
        .pipeTo(self)

    case (unformattedAddress: String, resp @ HttpResponse(StatusCodes.OK, headers, entity, _)) =>
      log.info(s"Success response coming for $unformattedAddress")
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        val googleResponse = body.utf8String
        log.info(s"Success response for $unformattedAddress: ${textSample(googleResponse)}")

        AddressParser.findGoogleGeocoderFatalErrorFromJsonResponse(googleResponse) match {
          case Some(googleGeocoderFatalError) => log.info(googleGeocoderFatalError.toString); fatalError()
          case None =>
            db ! SaveGoogleResponse(unformattedAddress, googleResponse)
            addressParser ! ParseAddress(unformattedAddress, googleResponse)
        }
      }

    case (unformattedAddress: String, resp @ HttpResponse(code, _, _, _)) =>
      log.info(s"Request failed, response code: $code for $unformattedAddress")
      resp.discardEntityBytes()
      fatalError()
  }

  def fatalError() {
    numFatalErrors = numFatalErrors + 1
    if (numFatalErrors > MaxFatalErrors) {
      log.info(s"MaxFatalErrors reached. stopping ${self.path.name}")
      context.stop(self)
    }
  }
}
