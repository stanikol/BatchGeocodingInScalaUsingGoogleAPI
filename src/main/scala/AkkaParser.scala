
import java.net.URLEncoder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import BatchParserCmd.Config
import model.{_}
import akka_parser.old_parser.Utils
import com.typesafe.scalalogging.LazyLogging
import flows.{AddressParserFlow, GoogleApiFlow, SaveAddressParsingResultFlow}

import scala.concurrent.Future

object AkkaParser extends LazyLogging {

  def main(config: Config): Unit = {
    implicit val system: ActorSystem = ActorSystem("System")
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val dbConnection = Utils.getDbConnection(config.dbUrl)

    def terminateSystem() = {
      println("TERMINATING ACTOR SYSTEM ...")
      system.terminate()
      dbConnection.close()
    }
    
    if(config.op == "parseOnly"){
        val sourceUnparsedGoogleResponsesFromDatabase: Source[GoogleApiResponse, Future[Int]] =
            DAO.getSourceOfUnparsedGoogleResponsesFromDatabase(dbConnection, config.tableName, config.maxEntries)
        val parserFlow: Flow[GoogleApiResponse, AddressParsingResult, NotUsed] =
          AddressParserFlow.buildFlow(100)
        //
        sourceUnparsedGoogleResponsesFromDatabase
          .via(parserFlow)
            .via(SaveAddressParsingResultFlow.buildFlow(dbConnection, config.tableName)(10))
              .runWith(Sink.ignore)
                .map(_=>terminateSystem())
    } else if(config.op == "googleQueryOnly" || config.op == "googleQueryAndParse") {
//        val googleApi = new GoogleApiFlow
        val googleApi = new GoogleApiFlow {
          override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
            s"http://localhost:12500/test?a=${URLEncoder.encode(unformattedAddress, "utf-8")}"
        }
        val googleApiFlow: Flow[GeoCode, GoogleApiResponse, _] =
          googleApi buildFlow(dbConnection,
                              config.tableName,
                              GoogleApiKey(config.googleApiKey),
                              config.maxGoogleAPIOpenRequests,
                              config.maxGoogleAPIFatalErrors)
        //
        val sourceGoogleApiQueryAndSave: Source[GoogleApiResponse, NotUsed] =
          DAO.getSourceAddressesWithEmptyGoogleResponseFromDatabase(dbConnection, config.tableName, config.maxEntries)
            .via(googleApiFlow)
              .mapMaterializedValue(_=> NotUsed)
        val sourceRetrying =
          sourceGoogleApiQueryAndSave
            .recoverWithRetries(-1, {case _ => sourceGoogleApiQueryAndSave})
        //
        if(config.op == "googleQueryOnly"){
          sourceRetrying
                  .runWith(Sink.ignore)
                    .map(_=>terminateSystem())
        } else {
          sourceRetrying
            .via(AddressParserFlow.buildFlow(3))
            .via(SaveAddressParsingResultFlow.buildFlow(dbConnection, config.tableName)(10))
            .runWith(Sink.ignore)
            .map(_=>terminateSystem())

        }
    } else {
      println(s"Unknown operation ${config.op}!")
      terminateSystem()
    }
  }

}
