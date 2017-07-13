import play.api.libs.json._
import java.net.URLEncoder

object AddressParser {
  // google json response
  case class AddressComponent(long_name: String, short_name: String, types: List[String])
  case class Location(lat: Float, lng: Float)
  case class Geometry(location: Option[Location], location_type: String)
  case class Result(address_components: List[AddressComponent], geometry: Geometry, formatted_address: String, place_id: String, types: List[String])
  case class Response(results: List[Result], status: String)

  // information extracted
  case class ParsedAddress(exactMath: Boolean, locality: Option[String], areaLevel1: Option[String], areaLevel2: Option[String], areaLevel3: Option[String], postalCode: Option[String], country: Option[String], location: Option[Location], formattedAddress: String)
  case class QueryAndResult(unformattedAddress: String, googleResponse: String, parsedAddress: ParsedAddress)

  // these "formats" define a default parser for the google response based on the field names
  implicit private val addressComponentFormats = Json.format[AddressComponent]
  implicit private val locationFormats = Json.format[Location]
  implicit private val geometryFormats = Json.format[Geometry]
  implicit private val resultFormats = Json.format[Result]
  implicit private val ResponseFormats = Json.format[Response]

  def parseAddress(googleApiKey: String, unformattedAddress: String): QueryAndResult = {
    val googleResponse = new String(Utils.download(url(googleApiKey, unformattedAddress)))
    val parsedAddress = parseAddressFromJsonResponse(googleResponse)
    QueryAndResult(unformattedAddress, googleResponse, parsedAddress)
  }

  def url(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"

  def parseAddressFromJsonResponse(googleResponseString: String): ParsedAddress = {
    val response: Response = Json.parse(googleResponseString).validate[Response].get
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
