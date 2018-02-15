package akka_parser.flows

import akka.stream.scaladsl.Flow
import akka_parser.model.GoogleApiResponse
import akka_parser.old_parser.AddressParser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object JsonParser {

  def parse(googleResopnse: GoogleApiResponse)
           (implicit executionContext: ExecutionContext) = {
    Future(Try{
      AddressParser.parseAddressFromJsonResponse(googleResopnse.googleResponse)
    })
  }

  def buildFlow(parallelism: Int)(implicit executionContext: ExecutionContext) =
    Flow[GoogleApiResponse].mapAsync(parallelism)(r => parse(r)(executionContext))



}
