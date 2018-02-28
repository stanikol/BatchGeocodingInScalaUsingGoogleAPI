package flows

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl.{Source, _}
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import geocoding.AddressParser
import geocoding.Utils._
import model.{AddressParsingResult, GeoCode, GoogleApiResponse}
import slick.jdbc.GetResult

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class DAOS (dbUrl:String)(implicit actorSystem: ActorSystem){

  private val slickDbConfig =
    ConfigFactory.parseString(s"""db.properties.url = "${dbUrl}"""")
      .withFallback(ConfigFactory.load().getConfig("slick-database"))

  implicit val slickSession = SlickSession.forConfig(slickDbConfig)
  actorSystem.registerOnTermination(() => slickSession.close())
  import slickSession.profile.api._

  implicit val getGeoCode = GetResult(r => GeoCode(r.nextInt, r.nextString))
  implicit val getGoogleApiResponse = GetResult(r => GoogleApiResponse(r.nextInt, r.nextString))


  private val logger = Logger("daos")

  def getAddressesWithEmptyGoogleResponseFromDatabase(tableName: String, maxEntries: Int)
                                                     (implicit materializer: Materializer): Source[GeoCode, NotUsed] = {
    val sql = sql"select id, unformattedAddress from #$tableName where googleResponse is null limit $maxEntries".as[GeoCode]
    Slick.source(sql)
  }


  def getUnparsedGoogleResponsesFromDatabase(tableName: String, maxEntries: Int)
                                            (implicit materializer: Materializer): Source[GoogleApiResponse, NotUsed] = {
    val sql = sql"select id, googleResponse from #$tableName where googleResponse is not null and parseGoogleResponseStatus is null limit $maxEntries".as[GoogleApiResponse]
    Slick.source(sql)
  }

  def saveGoogleResponse(tableName: String)
                        : Sink[GoogleApiResponse, Future[Done]] = Slick.sink{
    case GoogleApiResponse(id, googleResponse) =>
      // todo: this might overwrite the SaveGoogleResponseAndAddress
      logger.info(s"SaveGoogleResponse for #$id: ${textSample(googleResponse)}")
      sqlu"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus=null, numResults=null, #${addressComponentTypesNullUpdateStmt}, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id"

  }

  def saveAddressParsingResult(tableName: String)
                              : Sink[AddressParsingResult, Future[Done]] =
      Slick.sink {
          case (GoogleApiResponse(id, googleResponse), Success(Some(parsedAddress))) =>
            logger.info(s"SaveGoogleResponseAndAddress for #$id: ${textSample(googleResponse)}, ${textSample(parsedAddress.toString)}")
            import parsedAddress._
            val p: Map[String, Option[String]] =
              AddressParser.addressComponentTypes.map(t => (t, parsedAddress.addressComponents.get(t))).toMap ++
                Map("googleResponse" -> Some(googleResponse),
                  "numResults" -> Some(numResults.toString),
                  "lat" -> location.map(_.lat.toString),
                  "lng" -> location.map(_.lng.toString),
                  "mainType" -> mainType,
                  "types" -> Some(types.mkString(", ")),
                  "viewportArea" -> viewportArea.map(_.toString),
                  "formattedAddress" -> Some(formattedAddress),
                  "id" -> Some(id.toString))
            sqlu"update #$tableName set googleResponse=${p("googleResponse")}, parseGoogleResponseStatus='OK', numResults=${p("numResults")}, #$addressComponentTypesUpdateStmt, lat=${p("lat")}, lng=${p("lng")}, mainType=${p("mainType")}, types=${p("types")}, viewportArea=${p("viewportArea")}, formattedAddress=${p("formattedAddress")} where id=${id}"

          case (GoogleApiResponse(id, googleResponse), Success(None)) =>
            logger.info(s"SaveGoogleResponseAndEmptyResult for #$id: ${textSample(googleResponse)}")
            sqlu"update #$tableName set googleResponse=$googleResponse, parseGoogleResponseStatus='OK', numResults=0, #$addressComponentTypesNullUpdateStmt, lat=null, lng=null, mainType=null, types=null, viewportArea=null, formattedAddress=null where id=$id"

          case (GoogleApiResponse(id, googleResponse), Failure(exception)) =>
            logger.info(s"SaveError: $id, $exception")
            sqlu"update #$tableName set parseGoogleResponseStatus=${exception.getMessage} where id=$id"
    }

  private val addressComponentTypesUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = '$t'").mkString(", ")

  private val addressComponentTypesNullUpdateStmt: String =
    AddressParser.addressComponentTypes.map(t => s"$t = null").mkString(", ")

}
