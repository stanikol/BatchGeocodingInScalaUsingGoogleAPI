import java.sql.Connection

import AddressParser.{OverQueryLimitGoogleMapsApiException, ParsedAddress}
import Utils._
import anorm.{SqlParser, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class BatchParserCmd(googleApiKey: String, implicit val conn: Connection) {
  var overQueryLimit = false

  // query google

  private def getAddressesWithEmptyGoogleResponseFromDatabase(maxGoogleQueries: Int): List[String] =
    SQL"select unformattedAddress from addresses where googleResponse is null limit $maxGoogleQueries".as(SqlParser.str(1).*)

  private def queryGoogle(unformattedAddress: String): Future[String] = {
    if (overQueryLimit) throw new OverQueryLimitGoogleMapsApiException()  // todo: better way to achieve this
    println(s"+++ queryGoogle: $unformattedAddress")
    ws.url(AddressParser.url(googleApiKey, unformattedAddress))
      .withFollowRedirects(true)
      .get()
      .map(_.body)
  }

  private def saveGoogleResponseToDatabase(unformattedAddress: String, googleResponse: String) {
    println(s"+++ saveGoogleResponseToDatabase: $unformattedAddress")
    executeOneRowUpdate(SQL"update addresses set googleResponse=$googleResponse, parseGoogleResponseStatus=null, exactMatch=null, locality=null, areaLevel1=null, areaLevel2=null, areaLevel3=null, postalCode=null, country=null, lat=null, lng=null, formattedAddress=null where unformattedAddress=$unformattedAddress")
  }

  private def queryGoogleAndSaveToDatabaseAndParse(unformattedAddress: String): Future[Unit] = {
    queryGoogle(unformattedAddress)
      .map { googleResponse =>
        saveGoogleResponseToDatabase(unformattedAddress, googleResponse)
        parseAddressAndSaveToDatabase(unformattedAddress, googleResponse)
      }
      .recover {
        case t: OverQueryLimitGoogleMapsApiException => overQueryLimit = true; ()
        case t: Throwable => saveErrorToDatabase(unformattedAddress, t); ()
      }
  }

  // parse google response

  private def getAddressesWithEmptyParseGoogleResponseStatusFromDatabase: List[(String, String)] =
    SQL"select unformattedAddress, googleResponse from addresses where googleResponse is not null and parseGoogleResponseStatus is null"
      .as((SqlParser.str(1) ~ SqlParser.str(2)).*).map(SqlParser.flatten)

  private def saveParsedAddressToDatabase(unformattedAddress: String, googleResponse: String, parsedAddress: ParsedAddress) {
    println(s"+++ saveParsedAddressToDatabase: $unformattedAddress, $parsedAddress")
    import parsedAddress._
    executeOneRowUpdate(SQL"update addresses set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', exactMatch=$exactMath, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)}, formattedAddress=$formattedAddress where unformattedAddress=$unformattedAddress")
  }

  private def saveErrorToDatabase(unformattedAddress: String, exception: Throwable) {
    println(s"+++ saveErrorToDatabase: $unformattedAddress, $exception")
    executeOneRowUpdate(SQL"update addresses set parseGoogleResponseStatus=${exception.toString} where unformattedAddress=$unformattedAddress")
  }

  private def parseAddressAndSaveToDatabase(unformattedAddress: String, googleResponse: String) {
    println(s"+++ parseAddressAndSaveToDatabase: $unformattedAddress")

    try {
        val parsedAddress = AddressParser.parseAddressFromJsonResponse(googleResponse)
        saveParsedAddressToDatabase(unformattedAddress, googleResponse, parsedAddress)
      } catch {
        case t: OverQueryLimitGoogleMapsApiException => overQueryLimit = true; ()
        case t: Throwable => saveErrorToDatabase(unformattedAddress, t); ()
      }
  }

  private def parseAddressesAndSaveToDatabase() {
    getAddressesWithEmptyParseGoogleResponseStatusFromDatabase.foreach { case (unformattedAddress, googleResponse) =>
      parseAddressAndSaveToDatabase(unformattedAddress, googleResponse)
    }
  }

  // we can set parseGoogleResponseStatus to null to recompute parseAddressFromJsonResponse, without the need to re-query google (which costs money)
  // we compute parseAddressFromJsonResponse just after each google query,
  // but we call also parseAddressesAndSaveToDatabase at the beginning and at the end
  def run(maxGoogleQueries: Int) {
    val futures: List[Future[Unit]] =
      Future(parseAddressesAndSaveToDatabase()) :: getAddressesWithEmptyGoogleResponseFromDatabase(maxGoogleQueries).map(queryGoogleAndSaveToDatabaseAndParse)

    Await.result(Future.sequence(futures), Duration.Inf)

    parseAddressesAndSaveToDatabase()
  }
}

object BatchParserCmd {
  def main(args: Array[String]) {
    try {
      val maxGoogleQueries = args(0).toInt
      val googleApiKey = args(1)
      val dbUrl = args(2)

      println("+++ START.")

      val conn = Utils.getDbConnection(dbUrl)

      new BatchParserCmd(googleApiKey, conn).run(maxGoogleQueries)

      println("+++ END.")
    } finally {
      Utils.wsTerminate()
    }
  }
}
