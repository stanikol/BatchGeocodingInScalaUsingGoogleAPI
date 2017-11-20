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

  val addressComponentTypesUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = {$t}").mkString(", ")

  val addressComponentTypesNullUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = null").mkString(", ")

  val createTableStmt: String =
    s"""
      |create table addresses(
      |unformattedAddress varchar(500) primary key,
      |ts timestamp default current_timestamp on update current_timestamp,
      |googleResponse text,
      |parseGoogleResponseStatus text,
      |numResults int,
      |formattedAddress varchar(500),
      |lat float(10,6), lng float(10,6), mainType varchar(100), types text, viewportArea float,
      |${AddressParser.addressComponentTypes.map(c => s"$c varchar(100)").mkString(", ")},
      |index(ts), index(googleResponse(100)), index(parseGoogleResponseStatus(100)), index(numResults), index(formattedAddress),
      |index(lat), index(lng), index(mainType), index(types(100)), index(viewportArea),
      |${AddressParser.addressComponentTypes.map(c => s"index($c)").mkString(", ")}
      |) engine = InnoDB default character set = utf8mb4 collate = utf8mb4_unicode_ci
    """.stripMargin
}

class DB(dbUrl: String, tableName: String) extends Actor with ActorLogging {
  import DB._
  implicit val conn: Connection = Utils.getDbConnection(dbUrl)

  def receive = {
    case SaveGoogleResponse(unformattedAddress, googleResponse) =>
      log.info(s"SaveGoogleResponse: $unformattedAddress, ${textSample(googleResponse)}")
      // todo: this might overwrite the SaveGoogleResponseAndAddress
      executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where unformattedAddress=$unformattedAddress")

    case SaveGoogleResponseAndAddress(unformattedAddress, googleResponse, parsedAddress) =>
      log.info(s"SaveGoogleResponseAndAddress: $unformattedAddress, ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
      import parsedAddress._

      val params: Seq[NamedParameter] =
        (AddressParser.addressComponentTypes.map(t => (t, parsedAddress.addressComponents.get(t))).toMap +
          ("googleResponse" -> Some(googleResponse), "numResults" -> Some(numResults.toString), "lat" -> location.map(_.lat.toString), "lng" -> location.map(_.lng.toString), "mainType" -> mainType, "types" -> Some(types.mkString(", ")), "viewportArea" -> viewportArea.map(_.toString), "formattedAddress" -> Some(formattedAddress), "unformattedAddress" -> Some(unformattedAddress))
          ).map {case (k,v) => NamedParameter(k, v)}.toSeq

      executeOneRowUpdate(SQL(s"update $tableName set googleResponse={googleResponse}, parseGoogleResponseStatus='OK', numResults={numResults}, $addressComponentTypesUpdateStmt, lat={lat}, lng={lng}, mainType={mainType}, types={types}, viewportArea={viewportArea}, formattedAddress={formattedAddress} where unformattedAddress={unformattedAddress}").on(params:_*))

    case SaveGoogleResponseAndEmptyResult(unformattedAddress, googleResponse) =>
      log.info(s"SaveGoogleResponseAndEmptyResult: $unformattedAddress, ${textSample(googleResponse)}")
      executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where unformattedAddress=$unformattedAddress")

    case SaveError(unformattedAddress: String, exception: Throwable) =>
      log.info(s"SaveError: $unformattedAddress, $exception")
      executeOneRowUpdate(SQL"update #$tableName set parseGoogleResponseStatus=${exception.toString} where unformattedAddress=$unformattedAddress")
  }
}
