import java.sql.Connection

import AddressParser.ParsedAddress
import Utils._
import akka.actor.{Actor, ActorLogging, Props}
import anorm.{SqlParser, _}

object DB {
  def props(dbUrl: String, tableName: String): Props = Props(new DB(dbUrl, tableName))
  final case class SaveGoogleResponse(unformattedAddress: String, googleResponse: String)
  final case class SaveGoogleResponseAndAddress(unformattedAddress: String, googleResponse: String, parsedAddress: ParsedAddress)
  final case class SaveGoogleResponseAndEmptyResult(unformattedAddress: String, googleResponse: String)
  final case class SaveError(unformattedAddress: String, exception: Throwable)

  def getAddressesWithEmptyGoogleResponseFromDatabase(tableName: String, maxEntries: Int)(implicit conn: Connection): List[String] =
    SQL"select unformattedAddress from #$tableName where googleResponse is null limit $maxEntries".as(SqlParser.str(1).*)

  def getAddressesWithEmptyParseGoogleResponseStatusFromDatabase(tableName: String, maxEntries: Int)(implicit conn: Connection): List[(String, String)] =
    SQL"select unformattedAddress, googleResponse from #$tableName where googleResponse is not null and parseGoogleResponseStatus is null limit $maxEntries"
      .as((SqlParser.str(1) ~ SqlParser.str(2)).*).map(SqlParser.flatten)
}

class DB(dbUrl: String, tableName: String) extends Actor with ActorLogging {
  import DB._
  implicit val conn: Connection = Utils.getDbConnection(dbUrl)

  val addressComponentTypesUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = {$t}").mkString(", ")

  val addressComponentTypesNullUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = null").mkString(", ")

  def receive = {
    case SaveGoogleResponse(unformattedAddress, googleResponse) =>
      log.info(s"SaveGoogleResponse: $unformattedAddress, ${textSample(googleResponse)}")
      // todo: this might overwrite the SaveGoogleResponseAndAddress
      executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, locality=null, areaLevel1=null, areaLevel2=null, areaLevel3=null, postalCode=null, country=null, lat=null, lng=null, mainType=null, types=null, formattedAddress=null where unformattedAddress=$unformattedAddress")

    case SaveGoogleResponseAndAddress(unformattedAddress, googleResponse, parsedAddress) =>
      log.info(s"SaveGoogleResponseAndAddress: $unformattedAddress, ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
      import parsedAddress._

      val params: Seq[NamedParameter] =
        (AddressParser.addressComponentTypes.map(t => (t, parsedAddress.addressComponents.get(t))).toMap +
          ("googleResponse" -> Some(googleResponse), "numResults" -> Some(numResults.toString), "lat" -> location.map(_.lat.toString), "lng" -> location.map(_.lng.toString), "mainType" -> mainType, "types" -> Some(types.mkString(", ")), "formattedAddress" -> Some(formattedAddress), "unformattedAddress" -> Some(unformattedAddress))
          ).map {case (k,v) => NamedParameter(k, v)}.toSeq

      executeOneRowUpdate(SQL(s"update $tableName set googleResponse={googleResponse}, parseGoogleResponseStatus='OK', numResults={numResults}, $addressComponentTypesUpdateStmt, lat={lat}, lng={lng}, mainType={mainType}, types={types}, formattedAddress={formattedAddress} where unformattedAddress={unformattedAddress}").on(params:_*))

    case SaveGoogleResponseAndEmptyResult(unformattedAddress, googleResponse) =>
      log.info(s"SaveGoogleResponseAndEmptyResult: $unformattedAddress, ${textSample(googleResponse)}")
      executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, formattedAddress=null where unformattedAddress=$unformattedAddress")

    case SaveError(unformattedAddress: String, exception: Throwable) =>
      log.info(s"SaveError: $unformattedAddress, $exception")
      executeOneRowUpdate(SQL"update #$tableName set parseGoogleResponseStatus=${exception.toString} where unformattedAddress=$unformattedAddress")
  }
}
