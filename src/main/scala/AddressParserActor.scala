import akka.actor.{Actor, ActorRef, Props}

object AddressParserActor {
  def props(db: ActorRef): Props = Props(new AddressParserActor(db))
  final case class ParseAddress(id: Int, response: String)
}

class AddressParserActor(db: ActorRef) extends Actor {
  import AddressParserActor._
  import DB._

  def receive = {
    case ParseAddress(id, googleResponse) =>
      try {
        val parsedAddress: Option[AddressParser.ParsedAddress] = AddressParser.parseAddressFromJsonResponse(googleResponse)
        parsedAddress match {
          case Some(a) => db ! SaveGoogleResponseAndAddress(id, googleResponse, a)
          case None => db ! SaveGoogleResponseAndEmptyResult(id, googleResponse)
        }
      } catch {
        case e: Throwable => db ! SaveError(id, e)
      }
  }
}
