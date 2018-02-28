package geocoding
import java.net.URLEncoder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.scalalogging.LazyLogging
import flows.{DAOS, GoogleApi, JsonParser}
import geocoding.BatchParserCmd.Config
import model._

import scala.concurrent.duration.FiniteDuration

object AkkaParser extends LazyLogging {

  def main(config: Config,
           throttleGoogleAPIOpenRequests: FiniteDuration,
           jsonParserParallelism: Int,
           saveAddressParsingResultParallelism: Int): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val daos = new DAOS(config.dbUrl)

    def terminateSystem() = {
      println("TERMINATING ACTOR SYSTEM ...")
      Http().shutdownAllConnectionPools()
      system.terminate()
    }

    if(config.op == "parseOnly"){
        daos.getUnparsedGoogleResponsesFromDatabase(config.tableName, config.maxEntries)
          .via(JsonParser.buildFlow(jsonParserParallelism))
            .runWith(daos.saveAddressParsingResult(config.tableName))
              .foreach(_ => terminateSystem())
    } else if(config.op == "googleQueryOnly" || config.op == "googleQueryAndParse") {
        val googleApi = new GoogleApi
//        val googleApi = new GoogleApi {
//          override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
//            s"http://localhost:12500/test?a=${URLEncoder.encode(unformattedAddress, "utf-8")}"
//        }
        val googleApiResponse: Source[GoogleApiResponse, NotUsed] =
          googleApi buildFlow(daos,
                              config.tableName,
                              GoogleApiKey(config.googleApiKey),
                              config.maxGoogleAPIOpenRequests,
                              throttleGoogleAPIOpenRequests,
                              config.maxGoogleAPIFatalErrors,
                              config.maxEntries
          )
        //
        if(config.op == "googleQueryOnly"){
          googleApiResponse
                  .runWith(Sink.ignore)
                    .foreach(_=>terminateSystem())
        } else /* config.op == "googleQueryAndParse" */{
          googleApiResponse
            .via(JsonParser.buildFlow(jsonParserParallelism))
              .runWith(daos.saveAddressParsingResult(config.tableName))
                .foreach(_=>terminateSystem())

        }

//        println(s"Press RETURN to stop...")
//        StdIn.readLine()
//        terminateSystem()
    } else {
      println(s"Unknown operation ${config.op}!")
      terminateSystem()
    }
  }

}
