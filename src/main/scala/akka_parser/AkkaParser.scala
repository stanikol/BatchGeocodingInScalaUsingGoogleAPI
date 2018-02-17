package akka_parser

import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches, Materializer}
import akka_parser.BatchParserCmd2.Config
import akka_parser.flows.{GoogleApiFlow, AddressParserFlow, SaveAddressParsingResultFlow, SaveApiResponseResultSink}
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
          AddressParserFlow.buildFlow(100)
        //
        sourceUnparsedGoogleResponsesFromDatabase
          .via(parserFlow)
            .via(SaveAddressParsingResultFlow.buildFlow(config.dbUrl, config.tableName)(10))
              .runWith(Sink.ignore)
                .map(_=>terminateActorSystem)
      }
    } else if(config.op == "googleQueryOnly" || config.op == "googleQueryAndParse") {
      for{
        connection <- FT.pure(Utils.getDbConnection(config.dbUrl))
        addressesWithEmptyGoogleResponseFromDatabase <- FT(DAO.getAddressesWithEmptyGoogleResponseFromDatabase(connection, config.tableName, config.maxEntries))
        _ = println(s"num unformattedAddresses to query: ${addressesWithEmptyGoogleResponseFromDatabase.length}")
      } yield {
        val googleApi = new GoogleApiFlow
//        val googleApi = new GoogleApiFlow {
//          override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
//            s"http://localhost:12500/test?a=${URLEncoder.encode(unformattedAddress, "utf-8")}"
//        }
        val googleApiFlow: Flow[GeoCode, GoogleApiResponse, _] =
          googleApi buildFlow(config.dbUrl,
                              config.tableName,
                              GoogleApiKey(config.googleApiKey),
                              config.maxGoogleAPIOpenRequests,
                              config.maxGoogleAPIFatalErrors)
        //
        val sourceGoogleApiQueryAndSave: Source[GoogleApiResponse, NotUsed] =
            Source(addressesWithEmptyGoogleResponseFromDatabase.map((GeoCode.apply _).tupled))
              .via(googleApiFlow)
        //
        if(config.op == "googleQueryOnly"){
            sourceGoogleApiQueryAndSave
                  .runWith(Sink.ignore)
                    .map(_=>terminateActorSystem)
        } else {
          sourceGoogleApiQueryAndSave
            .via(AddressParserFlow.buildFlow(3))
            .via(SaveAddressParsingResultFlow.buildFlow(config.dbUrl, config.tableName)(10))
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
