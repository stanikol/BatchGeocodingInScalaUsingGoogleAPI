import java.sql.Connection

import AddressParser.ParsedAddress
import Utils._
import akka.actor.{Actor, ActorLogging, Props}
import anorm.{SqlParser, _}

object DB {
  def props(dbUrl: String): Props = Props(new DB(dbUrl))
  final case class SaveGoogleResponse(unformattedAddress: String, googleResponse: String)
  final case class SaveGoogleResponseAndAddress(unformattedAddress: String, googleResponse: String, parsedAddress: ParsedAddress)
  final case class SaveGoogleResponseAndEmptyResult(unformattedAddress: String, googleResponse: String)
  final case class SaveError(unformattedAddress: String, exception: Throwable)

  def getAddressesWithEmptyGoogleResponseFromDatabase(maxGoogleQueries: Int)(implicit conn: Connection): List[String] =
    SQL"select unformattedAddress from addresses where googleResponse is null limit $maxGoogleQueries".as(SqlParser.str(1).*)

  def getAddressesWithEmptyParseGoogleResponseStatusFromDatabase(implicit conn: Connection): List[(String, String)] =
    SQL"select unformattedAddress, googleResponse from addresses where googleResponse is not null and parseGoogleResponseStatus is null"
      .as((SqlParser.str(1) ~ SqlParser.str(2)).*).map(SqlParser.flatten)
}

class DB(dbUrl: String) extends Actor with ActorLogging {
  import DB._
  implicit val conn: Connection = Utils.getDbConnection(dbUrl)

  def receive = {
    case SaveGoogleResponse(unformattedAddress, googleResponse) =>
      log.info(s"SaveGoogleResponse: $unformattedAddress, ${textSample(googleResponse)}")
      // todo: this might overwrite the SaveGoogleResponseAndAddress
      executeOneRowUpdate(SQL"update addresses set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, locality=null, areaLevel1=null, areaLevel2=null, areaLevel3=null, postalCode=null, country=null, lat=null, lng=null, formattedAddress=null where unformattedAddress=$unformattedAddress")

    case SaveGoogleResponseAndAddress(unformattedAddress, googleResponse, parsedAddress) =>
      log.info(s"SaveGoogleResponseAndAddress: $unformattedAddress, ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
      import parsedAddress._
      executeOneRowUpdate(SQL"update addresses set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=$numResults, locality=$locality, areaLevel1=$areaLevel1, areaLevel2=$areaLevel2, areaLevel3=$areaLevel3, postalCode=$postalCode, country=$country, lat=${location.map(_.lat)}, lng=${location.map(_.lng)}, formattedAddress=$formattedAddress where unformattedAddress=$unformattedAddress")

    case SaveGoogleResponseAndEmptyResult(unformattedAddress, googleResponse) =>
      log.info(s"SaveGoogleResponseAndEmptyResult: $unformattedAddress, ${textSample(googleResponse)}")
      executeOneRowUpdate(SQL"update addresses set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, locality=null, areaLevel1=null, areaLevel2=null, areaLevel3=null, postalCode=null, country=null, lat=null, lng=null, formattedAddress=null where unformattedAddress=$unformattedAddress")

    case SaveError(unformattedAddress: String, exception: Throwable) =>
      log.info(s"SaveError: $unformattedAddress, $exception")
      executeOneRowUpdate(SQL"update addresses set parseGoogleResponseStatus=${exception.toString} where unformattedAddress=$unformattedAddress")
  }
}
