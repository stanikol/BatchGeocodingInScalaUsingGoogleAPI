package akka_parser

import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka_parser.BatchParserCmd2.Config
import akka_parser.flows.{GoogleApiCall, JsonParser, SaveAddressParsingResult, SaveApiResponseResult}
import akka_parser.model._
import akka_parser.old_parser.Utils
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

object AkkaParser extends LazyLogging {

  def main(config: Config): Unit = {
    implicit val system: ActorSystem = ActorSystem("System")
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    def terminateActorSystem = {
      println("==> TERMINATING ACTOR SYSTEM ...")
      system.terminate()
    }

    if(config.op == "parseOnly"){
      for{
        connection <- FT.pure(Utils.getDbConnection(config.dbUrl))
        googleResponses <- FT(DAO.getUnparsedGoogleResponsesFromDatabase(connection, config.tableName, config.maxEntries))
        _ = println(s"num googleResponses: ${googleResponses.length}")
      } yield {
        val sourceUnparsedGoogleResponsesFromDatabase: Source[GoogleApiResponse, NotUsed] =
          Source(googleResponses.map((GoogleApiResponse.apply _).tupled))
        val parserFlow: Flow[GoogleApiResponse, AddressParsingResult, NotUsed] =
          JsonParser.buildFlow(100)
        //
        sourceUnparsedGoogleResponsesFromDatabase
          .via(parserFlow)
            .via(SaveAddressParsingResult.buildFlow(config.dbUrl, config.tableName)(10))
              .runWith(Sink.ignore)
                .map(_=>terminateActorSystem)
      }
    } else if(config.op == "googleQueryOnly" || config.op == "googleQueryAndParse") {
      for{
        connection <- FT.pure(Utils.getDbConnection(config.dbUrl))
        addressesWithEmptyGoogleResponseFromDatabase <- FT(DAO.getAddressesWithEmptyGoogleResponseFromDatabase(connection, config.tableName, config.maxEntries))
        _ = println(s"num unformattedAddresses to query: ${addressesWithEmptyGoogleResponseFromDatabase.length}")
      } yield {
        val googleApi = new GoogleApiCall {
          override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
            s"http://localhost:12500/test?a=${URLEncoder.encode(unformattedAddress, "utf-8")}"
        }
        val googleApiFlow: Flow[GeoCode, GoogleApiResult, _] =
          googleApi buildFlow(GoogleApiKey(config.googleApiKey), config.maxGoogleAPIOpenRequests)
        //
        val numFatalErrors = new AtomicInteger(config.maxGoogleAPIFatalErrors - 1)
        val countFatalErrors: Flow[GoogleApiResult, GoogleApiResponse, NotUsed] =
          Flow[GoogleApiResult].map {
            case Right(r) => List(r)
            case Left(_) =>
              val errorsLeft = numFatalErrors.getAndDecrement()
              logger.info(s"fatalError #${config.maxGoogleAPIFatalErrors - errorsLeft} errorsLeft=$errorsLeft")
              if (errorsLeft <= 0) {
                logger.info(s"MaxFatalErrors reached. stopping {self.path.name}")
                system.terminate()
              }
              List.empty
          }.mapConcat(identity)
        //
        val sourceGoogleApiResponse: Source[GoogleApiResponse, NotUsed] =
          Source(addressesWithEmptyGoogleResponseFromDatabase.map((GeoCode.apply _).tupled))
            .via(googleApiFlow)
            .alsoTo(SaveApiResponseResult.buildSink(config.dbUrl, config.tableName))
            .via(countFatalErrors)
        //
        if(config.op == "googleQueryOnly"){
            sourceGoogleApiResponse
                  .runWith(Sink.ignore)
                    .map(_=>terminateActorSystem)
        } else {
          sourceGoogleApiResponse
            .via(JsonParser.buildFlow(3))
            .via(SaveAddressParsingResult.buildFlow(config.dbUrl, config.tableName)(10))
            .runWith(Sink.ignore)
            .map(_=>terminateActorSystem)

        }
      }
    } else {
      println(s"Unknown operation ${config.op}!")
      system.terminate()
    }
  }

}
