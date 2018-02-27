
import geocoding.AddressParser.ParsedAddress

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

package object model {

  type Result[T] = Either[String, T]
  type GoogleApiResult = Result[GoogleApiResponse]
  type AddressParsingResult = (GoogleApiResponse, Try[Option[ParsedAddress]])
//  type SaveApiResponseResultSink = Result[ResultOk.type]
//  type SaveAddressResult = Result[ResultOk.type]

  case class GeoCode(id: Int, unformattedAddress: String)

  case class GoogleApiKey(value: String)

  case class GoogleApiResponse(id: Int, googleResponse: String)

//  case object ResultOk

}
