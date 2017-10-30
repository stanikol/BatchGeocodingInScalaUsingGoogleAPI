import java.net.URLEncoder

import Utils.textSample
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

object AddressParser {
  // google json response
  case class AddressComponent(long_name: String, short_name: String, types: List[String])
  case class Location(lat: Float, lng: Float)
  case class Geometry(location: Option[Location], location_type: String)
  case class Result(address_components: List[AddressComponent], geometry: Geometry, formatted_address: String, place_id: String, types: List[String])
  case class Response(error_message: Option[String], results: List[Result], status: String)
  case class StatusResponse(error_message: Option[String], status: String)

  // information extracted
  case class ParsedAddress(
                            numResults: Int,
                            locality: Option[String],
                            areaLevel1: Option[String], areaLevel2: Option[String], areaLevel3: Option[String],
                            postalCode: Option[String],
                            country: Option[String],
                            location: Option[Location],
                            formattedAddress: String,
                            mainType: Option[String],
                            types: List[String]
                          )

  class GoogleGeocoderFatalError(message: String) extends Exception(message)

  // these "formats" define a default parser for the google response based on the field names
  implicit private val addressComponentFormats = Json.format[AddressComponent]
  implicit private val locationFormats = Json.format[Location]
  implicit private val geometryFormats = Json.format[Geometry]
  implicit private val resultFormats = Json.format[Result]
  implicit private val responseFormats = Json.format[Response]
  implicit private val statusResponseFormats = Json.format[StatusResponse]

  def url(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"

  def findGoogleGeocoderFatalErrorFromJsonResponse(googleResponseString: String): Option[GoogleGeocoderFatalError] = {
    Try(Json.parse(googleResponseString)) match {
      case Failure(e) => Some(new GoogleGeocoderFatalError(s"invalid response: ${textSample(googleResponseString)}: errors: ${textSample(e)}"))
      case Success(json) => json.validate[StatusResponse] match {
        case JsSuccess(response, _) => findGoogleGeocoderFatalErrorFromJsonResponse(response.status, response.error_message)
        case JsError(errors) => Some(new GoogleGeocoderFatalError(s"invalid response: ${textSample(googleResponseString)}: errors: ${textSample(errors)}"))
      }
    }
  }

  def findGoogleGeocoderFatalErrorFromJsonResponse(status: String, error_message: Option[String]): Option[GoogleGeocoderFatalError] =
    if (status == "OK" || status == "ZERO_RESULTS") None
    else Some(new GoogleGeocoderFatalError(status + ": " + error_message.getOrElse("")))

  // returns None if zero results, or Some(the first result).
  def parseAddressFromJsonResponse(googleResponseString: String): Option[ParsedAddress] = {
    val response: Response = Json.parse(googleResponseString).validate[Response].get

    findGoogleGeocoderFatalErrorFromJsonResponse(response.status, response.error_message).foreach(e => throw e)

    val numResults = response.results.length

    response.results.headOption.map { result =>
      def addressComponent(typeId: String) = result.address_components.find(_.types.contains(typeId))

      val locality = addressComponent("locality").map(_.long_name)
      val areaLevel1 = addressComponent("administrative_area_level_1").map(_.long_name)
      val areaLevel2 = addressComponent("administrative_area_level_2").map(_.long_name)
      val areaLevel3 = addressComponent("administrative_area_level_3").map(_.long_name)
      val postalCode = addressComponent("postal_code").map(_.long_name)
      val country = addressComponent("country").map(_.short_name)
      val location = result.geometry.location
      val formattedAddress = result.formatted_address
      val types = result.types
      val mainType = mainTypeOrder.find(types.contains)

      ParsedAddress(numResults, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, location, formattedAddress, mainType, types)
    }
  }

  // result types defined in https://developers.google.com/maps/documentation/geocoding/intro
  // ordered according to my definition of precission (political, colloquial_area were excluded)
  val mainTypeOrder = List(
    "street_address",
    "route",
    "postal_code",
    "ward",
    "sublocality_level_5",
    "sublocality_level_4",
    "sublocality_level_3",
    "sublocality_level_2",
    "sublocality_level_1",
    "sublocality",
    "locality",
    "natural_feature",
    "airport",
    "point_of_interest",
    "park",
    "intersection",
    "subpremise",
    "premise",
    "neighborhood",
    "administrative_area_level_5",
    "administrative_area_level_4",
    "administrative_area_level_3",
    "administrative_area_level_2",
    "administrative_area_level_1",
    "country")
}
