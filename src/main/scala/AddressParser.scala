import java.net.URLEncoder

import play.api.libs.json._

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
                            exactMath: Boolean,
                            locality: Option[String],
                            areaLevel1: Option[String], areaLevel2: Option[String], areaLevel3: Option[String],
                            postalCode: Option[String],
                            country: Option[String],
                            location: Option[Location],
                            formattedAddress: String)

  case class QueryAndResult(unformattedAddress: String, googleResponse: String, parsedAddress: ParsedAddress)

  class OverQueryLimitGoogleMapsApiException(message: String) extends Exception(message)

  val OVER_QUERY_LIMIT_STATUS = "OVER_QUERY_LIMIT"

  // these "formats" define a default parser for the google response based on the field names
  implicit private val addressComponentFormats = Json.format[AddressComponent]
  implicit private val locationFormats = Json.format[Location]
  implicit private val geometryFormats = Json.format[Geometry]
  implicit private val resultFormats = Json.format[Result]
  implicit private val responseFormats = Json.format[Response]
  implicit private val statusResponseFormats = Json.format[StatusResponse]

  def parseAddress(googleApiKey: String, unformattedAddress: String): QueryAndResult = {
    val googleResponse = Utils.download(url(googleApiKey, unformattedAddress))
    val parsedAddress = parseAddressFromJsonResponse(googleResponse)
    QueryAndResult(unformattedAddress, googleResponse, parsedAddress)
  }

  def url(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"

  def checkOverQueryLimitFromJsonResponse(googleResponseString: String) {
    val response: StatusResponse = Json.parse(googleResponseString).validate[StatusResponse].get
    if (response.status == OVER_QUERY_LIMIT_STATUS)
      throw new OverQueryLimitGoogleMapsApiException(response.error_message.getOrElse(""))
  }

  def parseAddressFromJsonResponse(googleResponseString: String): ParsedAddress = {
    val response: Response = Json.parse(googleResponseString).validate[Response].get

    if (response.status == OVER_QUERY_LIMIT_STATUS)
      throw new OverQueryLimitGoogleMapsApiException(response.error_message.getOrElse(""))

    val exactMath = response.results.length == 1 && response.status == "OK"

    response.results.headOption match {
      case Some(result) =>
        def addressComponent(typeId: String) = result.address_components.find(_.types.contains(typeId))

        val locality = addressComponent("locality").map(_.long_name)
        val areaLevel1 = addressComponent("administrative_area_level_1").map(_.long_name)
        val areaLevel2 = addressComponent("administrative_area_level_2").map(_.long_name)
        val areaLevel3 = addressComponent("administrative_area_level_3").map(_.long_name)
        val postalCode = addressComponent("postal_code").map(_.long_name)
        val country = addressComponent("country").map(_.short_name)
        val location = result.geometry.location
        val formattedAddress = result.formatted_address

        ParsedAddress(exactMath, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, location, formattedAddress)
      case None => throw new Exception("zero results")
    }
  }
}
