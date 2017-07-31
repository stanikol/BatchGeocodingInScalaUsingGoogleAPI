import DB.SaveGoogleResponse
import Utils.textSample
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
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

  val queue = new scala.collection.mutable.Queue[String]
  var currentRequests = 0
  val MaxCurrentRequest = 10

  var numRequests = 0

  def receive = {
    case GeoCode(unformattedAddress) =>
      log.info(s"GeoCode $unformattedAddress")
      if (currentRequests < MaxCurrentRequest)
        query(unformattedAddress)
      else
        queue += unformattedAddress

    case (unformattedAddress: String, resp @ HttpResponse(StatusCodes.OK, headers, entity, _)) =>
      log.info(s"Success response coming for $unformattedAddress")
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        currentRequests = currentRequests - 1
        queryNext()
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
      currentRequests = currentRequests - 1
      queryNext()
      fatalError()

    case f: Status.Failure =>
      log.info("failure" + textSample(f))

    case m =>
      log.info("unexpected message: " + textSample(m))
  }

  def query(unformattedAddress: String) {
    currentRequests = currentRequests + 1
    numRequests = numRequests + 1
    log.info(s"query #$numRequests: $unformattedAddress")
    val url = AddressParser.url(googleApiKey, unformattedAddress)
    http
      .singleRequest(HttpRequest(uri = url))
      .map(r => (unformattedAddress, r))
      .pipeTo(self)
  }

  def queryNext() {
    if (queue.nonEmpty) {
      query(queue.dequeue)
    }
  }

  def fatalError() {
    numFatalErrors = numFatalErrors + 1
    if (numFatalErrors > MaxFatalErrors) {
      log.info(s"MaxFatalErrors reached. stopping ${self.path.name}")
      context.stop(self)
    }
  }
}
