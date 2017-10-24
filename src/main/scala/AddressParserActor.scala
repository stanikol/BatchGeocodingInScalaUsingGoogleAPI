import akka.actor.{Actor, ActorRef, Props}

object AddressParserActor {
  def props(db: ActorRef): Props = Props(new AddressParserActor(db))
  final case class ParseAddress(url: String, response: String)
}

class AddressParserActor(db: ActorRef) extends Actor {
  import AddressParserActor._
  import DB._

  def receive = {
    case ParseAddress(unformattedAddress, googleResponse) =>
      try {
        val parsedAddress: Option[AddressParser.ParsedAddress] = AddressParser.parseAddressFromJsonResponse(googleResponse)
        parsedAddress match {
          case Some(a) => db ! SaveGoogleResponseAndAddress(unformattedAddress, googleResponse, a)
          case None => db ! SaveGoogleResponseAndEmptyResult(unformattedAddress, googleResponse)
        }
      } catch {
        case e: Throwable => db ! SaveError(unformattedAddress, e)
      }
  }
}
