package akka_parser.model

import java.sql.Connection

import akka_parser.old_parser.AddressParser
import akka_parser.old_parser.AddressParser.ParsedAddress
import akka_parser.old_parser.Utils._
import anorm._
import com.typesafe.scalalogging.Logger
import old_parser.DB
import old_parser.DB._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
object DAO {

  private val logger = Logger(LoggerFactory.getLogger("dao"))

  def getAddressesWithEmptyGoogleResponseFromDatabase(conn: Connection, tableName: String, maxEntries: Int)
                                                     (implicit executionContext: ExecutionContext)
                                                    : Future[Try[List[(Int, String)]]] = Future{Try{
      DB.getAddressesWithEmptyGoogleResponseFromDatabase(tableName, maxEntries)(conn)
  }}

  def getUnparsedGoogleResponsesFromDatabase(conn: Connection, tableName: String, maxEntries: Int)
                                            (implicit executionContext: ExecutionContext)
                                          : Future[Try[List[(Int, String)]]] = Future{Try{
      DB.getUnparsedGoogleResponsesFromDatabase(tableName, maxEntries)(conn)
  }}

  def saveGoogleResponse(conn: Connection, tableName: String)
                        (googleApiResponse: GoogleApiResponse)
                        (implicit executionContext: ExecutionContext) = Future{Try{
    val GoogleApiResponse(id, googleResponse) = googleApiResponse
    logger.info(s"SaveGoogleResponse for #$id: ${textSample(googleResponse)}")
    // todo: this might overwrite the SaveGoogleResponseAndAddress
    executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, #${addressComponentTypesNullUpdateStmt}, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id")(conn)
  }}

  def saveGoogleResponseAndAddress(conn: Connection, tableName: String)
                                  (googleApiResponse: GoogleApiResponse, parsedAddress: ParsedAddress)
                                  (implicit executionContext: ExecutionContext) = Future{Try{
    val GoogleApiResponse(id: Int, googleResponse: String) = googleApiResponse
    logger.info(s"SaveGoogleResponseAndAddress for #$id: ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
    import parsedAddress._

    val params: Seq[NamedParameter] =
      (AddressParser.addressComponentTypes.map(t => (t, parsedAddress.addressComponents.get(t))).toMap +
        ("googleResponse" -> Some(googleResponse), "numResults" -> Some(numResults.toString), "lat" -> location.map(_.lat.toString), "lng" -> location.map(_.lng.toString), "mainType" -> mainType, "types" -> Some(types.mkString(", ")), "viewportArea" -> viewportArea.map(_.toString), "formattedAddress" -> Some(formattedAddress), "id" -> Some(id.toString))
        ).map { case (k, v) => NamedParameter(k, v) }.toSeq

    executeOneRowUpdate(SQL(s"update $tableName set googleResponse={googleResponse}, parseGoogleResponseStatus='OK', numResults={numResults}, $addressComponentTypesUpdateStmt, lat={lat}, lng={lng}, mainType={mainType}, types={types}, viewportArea={viewportArea}, formattedAddress={formattedAddress} where id={id}").on(params: _*))(conn)
  }}

  def saveGoogleResponseAndEmptyResult(conn: Connection, tableName: String)
                                      (googleApiResponse: GoogleApiResponse)
                                      (implicit executionContext: ExecutionContext) = Future{Try{
    val GoogleApiResponse(id, googleResponse) = googleApiResponse
    logger.info(s"SaveGoogleResponseAndEmptyResult for #$id: ${textSample(googleResponse)}")
    executeOneRowUpdate(SQL"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id")(conn)
  }}

  def saveError(conn: Connection, tableName: String)
               (id:Int, exception: String)
               (implicit executionContext: ExecutionContext) = Future{Try{
    logger.info(s"SaveError: $id, $exception")
    executeOneRowUpdate(SQL"update #$tableName set parseGoogleResponseStatus=${exception.toString} where id=$id")(conn)
  }}


}
