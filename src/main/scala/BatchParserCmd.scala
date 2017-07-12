import anorm.{SqlParser, _}

import scala.util.Try

object BatchParserCmd {
  def main(args: Array[String]) {
    val googleApiKey = args(0)
    val dbUrl = args(1)

    println("+++ START.")

    implicit val conn = Utils.getDbConnection(dbUrl)

    def parseAddressAndSaveToDatabase(unformattedAddress: String) {
      println(s"parseAddressAndSaveToDatabase: $unformattedAddress")

      val googleResponse = AddressParser.queryGoogle(googleApiKey, unformattedAddress)
      val parsedAddress = AddressParser.parseAddressFromJsonResponse(googleResponse)

      import parsedAddress._
      val r: Int = SQL"update addresses set googleResponse=$googleResponse, exactMatch=$exactMath, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)}, formattedAddress=$formattedAddress where addressToQuery=$unformattedAddress"
        .executeUpdate()
      if (r != 1) println(s"error on $unformattedAddress")
    }

    val addresses: List[String] =
      SQL"select addressToQuery from addresses where googleResponse is null limit 1000".as(SqlParser.str(1).*)

    addresses.foreach(a => Try(parseAddressAndSaveToDatabase(a)))

    println("+++ END.")
  }
}
