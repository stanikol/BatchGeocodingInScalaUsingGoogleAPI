import play.api.libs.json._
import java.net.URLEncoder

case class AddressComponent (long_name: String, short_name: String, types: List[String])
case class Result(address_components: List[AddressComponent], formatted_address: String, place_id: String, types: List[String])
case class Response(results: List[Result], status: String)

object Test {
  implicit val addressComponentFormats = Json.format[AddressComponent]
  implicit val resultFormats = Json.format[Result]
  implicit val ResponseFormats = Json.format[Response]


  def parseAddress(address: String): Response = {
    val url = s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=AIzaSyAOcEY_1Y6q47MN-7SlZL8xQuHeIgwVaY8"
    val text = new String(Utils.download(url))
    Json.parse(text).validate[Response].get
  }

  def main(args: Array[String]) {
    println("+++ START.")

    val response = parseAddress("Rue de Geneve 81, Lausanne, Switzerland")

    println(response.status)
    println(response.results(0).address_components(0).types)
    println(response.results(0).formatted_address)


    println("+++ END.")
  }
}
