import play.api.libs.json._
import java.net.URLEncoder
import java.sql.Connection

import anorm.{SqlParser, _}

import scala.util.Try


// input
case class AddressComponent(long_name: String, short_name: String, types: List[String])
case class Location(lat: Double, lng: Double)
case class Geometry(location: Option[Location], location_type: String)
case class Result(address_components: List[AddressComponent], geometry: Geometry, formatted_address: String, place_id: String, types: List[String])
case class Response(results: List[Result], status: String)

// output
case class Address(text: String, response: String, exactMath: Boolean, locality: Option[String], areaLevel1: Option[String], areaLevel2: Option[String], areaLevel3: Option[String], postalCode: Option[String], country: Option[String], location: Option[Location])


/*
dbUrl=jdbc:mysql://cdm6-143.epfl.ch/patstat_2015a?user=mysqluser&password=__PASSWORD__&useSSL=false&useUnicode=yes&characterEncoding=utf8
dbUrl=jdbc:postgresql://localhost/test?user=fred&password=__PASSWORD__&ssl=true

sql:
create table addresses(text varchar(500) unique, response text, exactMatch int, locality varchar(255), areaLevel1 varchar(255), areaLevel2 varchar(255), areaLevel3 varchar(255), postalCode varchar(100), country varchar(100), lat decimal, lng decimal, index(exactMatch), index(locality), index(areaLevel1), index(areaLevel2), index(areaLevel3), index(postalCode), index(country), index(lat), index(lng))
insert into addresses (text) select distinct(concat(address_freeform, ', ', person_ctry_code)) from TLS226_PERSON_ORIG;
select text, exactMatch, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, lat, lng from addresses where response is not null;
*/

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
      val locality = result.address_components.find(_.types.contains("locality")).map(_.long_name)
      val areaLevel1 = result.address_components.find(_.types.contains("administrative_area_level_1")).map(_.long_name)
      val areaLevel2 = result.address_components.find(_.types.contains("administrative_area_level_2")).map(_.long_name)
      val areaLevel3 = result.address_components.find(_.types.contains("administrative_area_level_3")).map(_.long_name)
      val postalCode = result.address_components.find(_.types.contains("postal_code")).map(_.long_name)
      val country = result.address_components.find(_.types.contains("country")).map(_.short_name)
      val location = result.geometry.location

      Address(address, responseText, exactMath, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, location)
    }
  }

  def parseAddressAndSaveToDatabase(addressText: String)(implicit conn: Connection) {
    println(s"parseAddressAndSaveToDatabase: $addressText")
    parseAddress(addressText).foreach { address =>
      import address._
      val r: Int = SQL"update addresses set response=$response, exactMatch=$exactMath, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)} where text=$addressText"
        .executeUpdate()
      if (r != 1) println(s"error on $addressText")
    }
  }

  def main(args: Array[String]) {
    println("+++ START.")

    implicit val conn = Utils.getDbConnection
    val addresses: List[String] =
      SQL"select text from addresses where response is null limit 1000".as(SqlParser.str(1).*)

    addresses.foreach(a => Try(parseAddressAndSaveToDatabase(a)))

    println("+++ END.")
  }
}
