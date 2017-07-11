import play.api.libs.json._
import java.net.URLEncoder

case class AddressComponent(long_name: String, short_name: String, types: List[String])
case class Location(lat: Double, lng: Double)
case class Geometry(location: Location, location_type: String)
case class Result(address_components: List[AddressComponent], geometry: Geometry, formatted_address: String, place_id: String, types: List[String])
case class Response(results: List[Result], status: String)


case class Address(text: String, areaLevel1: String, areaLevel2: String, areaLevel3: String, postalCode: String, location: Location)

object Test {
  implicit val addressComponentFormats = Json.format[AddressComponent]
  implicit val locationFormats = Json.format[Location]
  implicit val geometryFormats = Json.format[Geometry]
  implicit val resultFormats = Json.format[Result]
  implicit val ResponseFormats = Json.format[Response]


  def parseAddress(address: String): Address = {
    val url = s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=AIzaSyAOcEY_1Y6q47MN-7SlZL8xQuHeIgwVaY8"
    val text = new String(Utils.download(url))
    val response = Json.parse(text).validate[Response].get

    val areaLevel1 = response.results(0).address_components.filter(_.types.contains("administrative_area_level_1")).apply(0).long_name
    val areaLevel2 = response.results(0).address_components.filter(_.types.contains("administrative_area_level_2")).apply(0).long_name
    val areaLevel3 = "" //response.results(0).address_components.filter(_.types.contains("administrative_area_level_1")).apply(0).long_name
    val postalCode = response.results(0).address_components.filter(_.types.contains("postal_code")).apply(0).long_name
    val location = response.results(0).geometry.location

    Address(address, areaLevel1, areaLevel2, areaLevel3, postalCode, location)
  }

  def main(args: Array[String]) {
    println("+++ START.")

    val address = parseAddress("Rue de Geneve 81, Lausanne, Switzerland")
    println(address)

    println("+++ END.")
  }
}
