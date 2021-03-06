//package model
//
//import java.sql.Connection
//
//import geocoding.AddressParser.ParsedAddress
//import akka.stream.Materializer
//import akka.stream.scaladsl.Source
//import anorm._
//import geocoding.Utils._
//import com.typesafe.scalalogging.Logger
//import geocoding.AddressParser
//import org.slf4j.LoggerFactory
//
//import scala.concurrent.{ExecutionContext, Future}
//import scala.util.Try
//object DAO {
//
//
//  private val logger = Logger(LoggerFactory.getLogger("dao"))
//
//  def getAddressesWithEmptyGoogleResponseFromDatabase(conn: Connection, tableName: String, maxEntries: Int)
//                                                     (implicit materializer: Materializer): Source[GeoCode, Future[Int]] = {
//    implicit val connection = conn
//    val sql = SQL"select id, unformattedAddress from #$tableName where googleResponse is null limit $maxEntries"
//    val parser = (SqlParser.int(1) ~ SqlParser.str(2)).map(r => GeoCode(r._1, r._2))
//    AkkaStream.source(sql, parser, ColumnAliaser.empty)
//  }
//
//
//  def getUnparsedGoogleResponsesFromDatabase(conn: Connection, tableName: String, maxEntries: Int)
//                                            (implicit materializer: Materializer): Source[GoogleApiResponse, Future[Int]] = {
//    implicit val connection = conn
//    val sql = SQL"select id, googleResponse from #$tableName where googleResponse is not null and parseGoogleResponseStatus is null limit $maxEntries"
//    val parser = (SqlParser.int(1) ~ SqlParser.str(2)).map(r => GoogleApiResponse(r._1, r._2))
//    AkkaStream.source(sql, parser, ColumnAliaser.empty)
//  }
//
//  def saveGoogleResponse(conn: Connection, tableName: String)
//                        (googleApiResponse: GoogleApiResponse)
//                        (implicit executionContext: ExecutionContext) = Future{Try{
//    val GoogleApiResponse(id, googleResponse) = googleApiResponse
//    logger.info(s"SaveGoogleResponse for #$id: ${textSample(googleResponse)}")
//    // todo: this might overwrite the SaveGoogleResponseAndAddress
//    executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, #${addressComponentTypesNullUpdateStmt}, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id")(conn)
//  }}
//
//  def saveGoogleResponseAndAddress(conn: Connection, tableName: String)
//                                  (googleApiResponse: GoogleApiResponse, parsedAddress: ParsedAddress)
//                                  (implicit executionContext: ExecutionContext) = Future{Try{
//    val GoogleApiResponse(id: Int, googleResponse: String) = googleApiResponse
//    logger.info(s"SaveGoogleResponseAndAddress for #$id: ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
//    import parsedAddress._
//
//    val params: Seq[NamedParameter] =
//      (AddressParser.addressComponentTypes.map(t => (t, parsedAddress.addressComponents.get(t))).toMap +
//        ("googleResponse" -> Some(googleResponse), "numResults" -> Some(numResults.toString), "lat" -> location.map(_.lat.toString), "lng" -> location.map(_.lng.toString), "mainType" -> mainType, "types" -> Some(types.mkString(", ")), "viewportArea" -> viewportArea.map(_.toString), "formattedAddress" -> Some(formattedAddress), "id" -> Some(id.toString))
//        ).map { case (k, v) => NamedParameter(k, v) }.toSeq
//
//    executeOneRowUpdate(SQL(s"update $tableName set googleResponse={googleResponse}, parseGoogleResponseStatus='OK', numResults={numResults}, $addressComponentTypesUpdateStmt, lat={lat}, lng={lng}, mainType={mainType}, types={types}, viewportArea={viewportArea}, formattedAddress={formattedAddress} where id={id}").on(params: _*))(conn)
//  }}
//
//  def saveGoogleResponseAndEmptyResult(conn: Connection, tableName: String)
//                                      (googleApiResponse: GoogleApiResponse)
//                                      (implicit executionContext: ExecutionContext) = Future{Try{
//    val GoogleApiResponse(id, googleResponse) = googleApiResponse
//    logger.info(s"SaveGoogleResponseAndEmptyResult for #$id: ${textSample(googleResponse)}")
//    executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id")(conn)
//  }}
//
//  def saveError(conn: Connection, tableName: String)
//               (id:Int, exception: String)
//               (implicit executionContext: ExecutionContext) = Future{Try{
//    logger.info(s"SaveError: $id, $exception")
//    executeOneRowUpdate(SQL"update #$tableName set parseGoogleResponseStatus=${exception.toString} where id=$id")(conn)
//  }}
//
//
//  val addressComponentTypesUpdateStmt: String =
//    AddressParser.addressComponentTypes.map(t => s"$t = {$t}").mkString(", ")
//
//  val addressComponentTypesNullUpdateStmt: String =
//    AddressParser.addressComponentTypes.map(t => s"$t = null").mkString(", ")
//
//}
