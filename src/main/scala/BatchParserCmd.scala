import java.sql.Connection

import AddressParser.ParsedAddress
import Utils.ws
import anorm.{SqlParser, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class BatchParserCmd(googleApiKey: String, implicit val conn: Connection) {
  private def getAddressesFromDatabase: List[String] =
    SQL"select addressToQuery from addresses where googleResponse is null limit 1000".as(SqlParser.str(1).*)

  private def queryGoogle(unformattedAddress: String) = {
    println(s"+++ queryGoogle: $unformattedAddress")
    ws.url(AddressParser.url(googleApiKey, unformattedAddress))
      .withFollowRedirects(true)
      .get()
      .map(_.body)
  }

  private def saveToDatabase(unformattedAddress: String, googleResponse: String, parsedAddress: ParsedAddress) {
    println(s"+++ saveToDatabase: $unformattedAddress, $parsedAddress")
    import parsedAddress._
    val r: Int = SQL"update addresses set googleResponse=$googleResponse, exactMatch=$exactMath, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)}, formattedAddress=$formattedAddress where addressToQuery=$unformattedAddress"
      .executeUpdate()
    if (r != 1) println(s"error on $unformattedAddress")
  }

  private def parseAddressAndSaveToDatabase(unformattedAddress: String): Future[Unit] = {
    println(s"+++ parseAddressAndSaveToDatabase: $unformattedAddress")
    queryGoogle(unformattedAddress)
      .map(googleResponse => (googleResponse, AddressParser.parseAddressFromJsonResponse(googleResponse)))
      .map { case(googleResponse, parsedAddress) => saveToDatabase(unformattedAddress, googleResponse, parsedAddress) }
  }

  def run() {
    val futures: List[Future[Unit]] =
      getAddressesFromDatabase.map(parseAddressAndSaveToDatabase)

    Await.result(Future.sequence(futures), Duration.Inf)
  }
}


object BatchParserCmd {
  def main(args: Array[String]) {
    try {
      val googleApiKey = args(0)
      val dbUrl = args(1)

      println("+++ START.")

      val conn = Utils.getDbConnection(dbUrl)

      new BatchParserCmd(googleApiKey, conn).run()

      println("+++ END.")
    } finally {
      Utils.wsTerminate()
    }
  }
}
