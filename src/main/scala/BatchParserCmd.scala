import anorm.{SqlParser, _}

import scala.util.Try

object BatchParserCmd {
  def main(args: Array[String]) {
    val googleApiKey = args(0)
    val dbUrl = args(1)

    println("+++ START.")

    val addressParser = new AddressParser(googleApiKey)

    implicit val conn = Utils.getDbConnection(dbUrl)

    def parseAddressAndSaveToDatabase(address: String) {
      println(s"parseAddressAndSaveToDatabase: $address")
      addressParser.parseAddress(address).foreach { parsedAddress =>
        import parsedAddress._
        val r: Int = SQL"update addresses set googleResponse=$googleResponse, exactMatch=$exactMath, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)}, formattedAddress=$formattedAddress where addressToQuery=$address"
          .executeUpdate()
        if (r != 1) println(s"error on $address")
      }
    }

    val addresses: List[String] =
      SQL"select addressToQuery from addresses where googleResponse is null limit 1000".as(SqlParser.str(1).*)

    addresses.foreach(a => Try(parseAddressAndSaveToDatabase(a)))

    println("+++ END.")
  }
}
