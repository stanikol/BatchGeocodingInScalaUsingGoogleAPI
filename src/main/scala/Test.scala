import play.api.libs.json._
import java.net.URLEncoder

// input
case class AddressComponent(long_name: String, short_name: String, types: List[String])
case class Location(lat: Double, lng: Double)
case class Geometry(location: Option[Location], location_type: String)
case class Result(address_components: List[AddressComponent], geometry: Geometry, formatted_address: String, place_id: String, types: List[String])
case class Response(results: List[Result], status: String)

// output
case class Address(text: String, exactMath: Boolean, areaLevel1: Option[String], areaLevel2: Option[String], areaLevel3: Option[String], postalCode: Option[String], location: Option[Location])

object Test {
  implicit val addressComponentFormats = Json.format[AddressComponent]
  implicit val locationFormats = Json.format[Location]
  implicit val geometryFormats = Json.format[Geometry]
  implicit val resultFormats = Json.format[Result]
  implicit val ResponseFormats = Json.format[Response]


  def parseAddress(address: String): Option[Address] = {
    val url = s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=AIzaSyAOcEY_1Y6q47MN-7SlZL8xQuHeIgwVaY8"
    val text = new String(Utils.download(url))
    val response = Json.parse(text).validate[Response].get

    val exactMath = response.results.length == 1 && response.status == "OK"

    response.results.headOption.map { result =>
      val areaLevel1 = result.address_components.find(_.types.contains("administrative_area_level_1")).map(_.long_name)
      val areaLevel2 = result.address_components.find(_.types.contains("administrative_area_level_2")).map(_.long_name)
      val areaLevel3 = result.address_components.find(_.types.contains("administrative_area_level_3")).map(_.long_name)
      val postalCode = result.address_components.find(_.types.contains("postal_code")).map(_.long_name)
      val location = result.geometry.location

      Address(address, exactMath, areaLevel1, areaLevel2, areaLevel3, postalCode, location)
    }
  }

  def main(args: Array[String]) {
    println("+++ START.")

    val address = parseAddress("Rue de Geneve 81, Lausanne, Switzerland")
    println(address)

    println("+++ END.")
  }
}
