package flows

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.Logger
import geocoding.AddressParser
import model.{AddressParsingResult, GoogleApiResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object JsonParser {

  private val logger = Logger("json-parser")

  private def parse(googleResopnse: GoogleApiResponse)
           (implicit executionContext: ExecutionContext)
  : Future[AddressParsingResult] = Future{
      val result = Try(AddressParser.parseAddressFromJsonResponse(googleResopnse.googleResponse))
      googleResopnse -> result
  }

  def buildFlow(parallelism: Int)(implicit executionContext: ExecutionContext)
      : Flow[GoogleApiResponse, AddressParsingResult, NotUsed] =
    Flow[GoogleApiResponse].mapAsync(parallelism){googleApiResponse: GoogleApiResponse =>
      parse(googleApiResponse)(executionContext)
    }



}
