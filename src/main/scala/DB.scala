import java.security.InvalidParameterException
import java.sql.Connection

import AddressParser.ParsedAddress
import Utils._
import akka.actor.{Actor, ActorLogging, Props}
import anorm.{SqlParser, _}

object DB {
  def props(dbUrl: String, tableName: String): Props = Props(new DB(dbUrl, tableName))
  final case class SaveGoogleResponse(id: Int, googleResponse: String)
  final case class SaveGoogleResponseAndAddress(id: Int, googleResponse: String, parsedAddress: ParsedAddress)
  final case class SaveGoogleResponseAndEmptyResult(id: Int, googleResponse: String)
  final case class SaveError(id: Int, exception: Throwable)

  def getAddressesWithEmptyGoogleResponseFromDatabase(tableName: String, maxEntries: Int)(implicit conn: Connection): List[(Int, String)] =
    SQL"select id, unformattedAddress from #$tableName where googleResponse is null limit $maxEntries".as((SqlParser.int(1) ~ SqlParser.str(2)).map(SqlParser.flatten).*)

  def getUnparsedGoogleResponsesFromDatabase(tableName: String, maxEntries: Int)(implicit conn: Connection): List[(Int, String)] =
    SQL"select id, googleResponse from #$tableName where googleResponse is not null and parseGoogleResponseStatus is null limit $maxEntries"
      .as((SqlParser.int(1) ~ SqlParser.str(2)).*).map(SqlParser.flatten)

  val addressComponentTypesUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = {$t}").mkString(", ")

  val addressComponentTypesNullUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = null").mkString(", ")

  def createTableStmt(tableName: String, addressLength: Int = 500, addressComponentsLength: Int = 200, maxLongTextIndexLength: Int = 100, unformattedAddressUnique: Boolean = true, maxIndexLength: Option[Int] = None): String = {
    if (unformattedAddressUnique && maxIndexLength.isDefined) throw new InvalidParameterException("unformattedAddressUnique && maxIndexLength.isDefined")
    val ls = maxIndexLength.map(l => s"($l)").getOrElse("")
    val unique = if (unformattedAddressUnique) "unique" else ""
    s"""
      |create table $tableName (
      |  id int not null auto_increment primary key,
      |  unformattedAddress varchar($addressLength) not null,
      |  ts timestamp default current_timestamp on update current_timestamp,
      |  googleResponse longtext,
      |  parseGoogleResponseStatus longtext,
      |  numResults int,
      |  formattedAddress varchar($addressLength),
      |  lat float(10,6), lng float(10,6), mainType varchar($addressComponentsLength), types longtext, viewportArea float,
      |  ${AddressParser.addressComponentTypes.map(c => s"$c varchar($addressComponentsLength)").mkString(", ")},
      |  $unique index(unformattedAddress$ls), index(ts), index(googleResponse($maxLongTextIndexLength)), index(parseGoogleResponseStatus($maxLongTextIndexLength)), index(numResults), index(formattedAddress$ls),
      |  index(lat), index(lng), index(mainType$ls), index(types($maxLongTextIndexLength)), index(viewportArea),
      |  ${AddressParser.addressComponentTypes.map(c => s"index($c$ls)").mkString(", ")}
      |) engine = InnoDB default character set = utf8mb4 collate = utf8mb4_bin row_format=dynamic
    """.stripMargin
  }
}

class DB(dbUrl: String, tableName: String) extends Actor with ActorLogging {
  import DB._
  implicit val conn: Connection = Utils.getDbConnection(dbUrl)

  def receive = {
    case SaveGoogleResponse(id, googleResponse) =>
      log.info(s"SaveGoogleResponse for #$id: ${textSample(googleResponse)}")
      // todo: this might overwrite the SaveGoogleResponseAndAddress
      executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id")

    case SaveGoogleResponseAndAddress(id, googleResponse, parsedAddress) =>
      log.info(s"SaveGoogleResponseAndAddress for #$id: ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
      import parsedAddress._

      val params: Seq[NamedParameter] =
        (AddressParser.addressComponentTypes.map(t => (t, parsedAddress.addressComponents.get(t))).toMap +
          ("googleResponse" -> Some(googleResponse), "numResults" -> Some(numResults.toString), "lat" -> location.map(_.lat.toString), "lng" -> location.map(_.lng.toString), "mainType" -> mainType, "types" -> Some(types.mkString(", ")), "viewportArea" -> viewportArea.map(_.toString), "formattedAddress" -> Some(formattedAddress), "id" -> Some(id.toString))
          ).map {case (k,v) => NamedParameter(k, v)}.toSeq

      executeOneRowUpdate(SQL(s"update $tableName set googleResponse={googleResponse}, parseGoogleResponseStatus='OK', numResults={numResults}, $addressComponentTypesUpdateStmt, lat={lat}, lng={lng}, mainType={mainType}, types={types}, viewportArea={viewportArea}, formattedAddress={formattedAddress} where id={id}").on(params:_*))

    case SaveGoogleResponseAndEmptyResult(id, googleResponse) =>
      log.info(s"SaveGoogleResponseAndEmptyResult for #$id: ${textSample(googleResponse)}")
      executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id")

    case SaveError(id, exception) =>
      log.info(s"SaveError: $id, $exception")
      executeOneRowUpdate(SQL"update #$tableName set parseGoogleResponseStatus=${exception.toString} where id=$id")
  }
}
