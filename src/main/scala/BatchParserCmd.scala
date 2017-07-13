import java.sql.Connection

import AddressParser.ParsedAddress
import Utils._
import anorm.{SqlParser, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class BatchParserCmd(googleApiKey: String, implicit val conn: Connection) {
  private def getAddressesFromDatabase: List[String] =
    SQL"select addressToQuery from addresses where googleResponse is null limit 10".as(SqlParser.str(1).*)

  private def queryGoogle(unformattedAddress: String): Future[String] = {
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
    if (r != 1) throw new Exception(s"error saving to database. num rows modified $r != 1")
  }

  private def parseAddressAndSaveToDatabase(unformattedAddress: String): Future[Unit] = {
    println(s"+++ parseAddressAndSaveToDatabase: $unformattedAddress")

    queryGoogle(unformattedAddress)
      .map { googleResponse =>
        val parsedAddress = AddressParser.parseAddressFromJsonResponse(googleResponse)
        saveToDatabase(unformattedAddress, googleResponse, parsedAddress)
      }
      .recover { case t: Throwable => println(s"+++ error on address = '$unformattedAddress', e = $t"); () }
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
