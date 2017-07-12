import play.api.libs.json._
import java.net.URLEncoder
import java.sql.Connection

import anorm.{SqlParser, _}

import scala.util.Try


// google json response
case class AddressComponent(long_name: String, short_name: String, types: List[String])
case class Location(lat: Float, lng: Float)
case class Geometry(location: Option[Location], location_type: String)
case class Result(address_components: List[AddressComponent], geometry: Geometry, formatted_address: String, place_id: String, types: List[String])
case class Response(results: List[Result], status: String)

// information extracted
case class Address(addressToQuery: String, googleResponse: String, exactMath: Boolean, locality: Option[String], areaLevel1: Option[String], areaLevel2: Option[String], areaLevel3: Option[String], postalCode: Option[String], country: Option[String], location: Option[Location], formattedAddress: String)

object Test {
  implicit val addressComponentFormats = Json.format[AddressComponent]
  implicit val locationFormats = Json.format[Location]
  implicit val geometryFormats = Json.format[Geometry]
  implicit val resultFormats = Json.format[Result]
  implicit val ResponseFormats = Json.format[Response]


  def parseAddress(address: String): Option[Address] = {
    val url = s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=AIzaSyAOcEY_1Y6q47MN-7SlZL8xQuHeIgwVaY8"
    val responseText = new String(Utils.download(url))
    val response = Json.parse(responseText).validate[Response].get

    val exactMath = response.results.length == 1 && response.status == "OK"

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

      Address(address, responseText, exactMath, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, location, formattedAddress)
    }
  }

  def parseAddressAndSaveToDatabase(address: String)(implicit conn: Connection) {
    println(s"parseAddressAndSaveToDatabase: $address")
    parseAddress(address).foreach { parsedAddress =>
      import parsedAddress._
      val r: Int = SQL"update addresses set googleResponse=$googleResponse, exactMatch=$exactMath, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)}, formattedAddress=$formattedAddress where addressToQuery=$address"
        .executeUpdate()
      if (r != 1) println(s"error on $address")
    }
  }

  def main(args: Array[String]) {
    println("+++ START.")

    implicit val conn = Utils.getDbConnection
    val addresses: List[String] =
      SQL"select addressToQuery from addresses where googleResponse is null limit 1000".as(SqlParser.str(1).*)

    addresses.foreach(a => Try(parseAddressAndSaveToDatabase(a)))

    println("+++ END.")
  }
}
