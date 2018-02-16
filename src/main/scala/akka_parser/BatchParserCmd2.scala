package akka_parser

import java.net.URLEncoder

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka_parser.flows.{GoogleApi, JsonParser}
import akka_parser.model.{DAO, FT, GeoCode}
import akka_parser.old_parser.{AddressParser, Utils}

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success, Try}
import akka_parser.model._
import akka_parser.old_parser.AddressParser.ParsedAddress

object BatchParserCmd2 {
  case class Config(
                   op: String = "",
                   maxEntries: Int = 100,
                   maxGoogleAPIOpenRequests: Int = 10,
                   maxGoogleAPIFatalErrors: Int = 5,
                     googleApiKey: String = "",
                     dbUrl: String = "",
                     tableName: String = ""
                   )

  val parser = new scopt.OptionParser[Config]("BatchParserCmd") {
    override def showUsageOnError = true

    opt[String]("op").required.action((x, c) =>
      c.copy(op = x)).text("googleQueryAndParse, googleQueryOnly or parseOnly")

    opt[Int]("maxEntries").required.action((x, c) =>
      c.copy(maxEntries = x)).text("maxEntries")

    opt[Int]("maxGoogleAPIOpenRequests").optional.action((x, c) =>
      c.copy(maxGoogleAPIOpenRequests = x)).text("maxGoogleAPIOpenRequests")

    opt[Int]("maxGoogleAPIFatalErrors").optional.action((x, c) =>
      c.copy(maxGoogleAPIFatalErrors = x)).text("maxGoogleAPIFatalErrors")

    opt[String]("googleApiKey").optional.action((x, c) =>
      c.copy(googleApiKey = x)).text("googleApiKey")

    opt[String]("dbUrl").action((x, c) =>
      c.copy(dbUrl = x)).text("dbUrl")

    opt[String]("tableName").action((x, c) =>
      c.copy(tableName = x)).text("tableName")

    version("version")
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config) =>
        println("+++ config: " + config)

        require(config.op == "googleQueryAndParse" || config.op == "googleQueryOnly" || config.op == "parseOnly")

        implicit val system: ActorSystem = ActorSystem("System")
        implicit val materializer: Materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher

        if(config.op == "parseOnly"){
            for{
              connection <- FT.pure(Utils.getDbConnection(config.dbUrl))
              googleResponses <- FT(DAO.getUnparsedGoogleResponsesFromDatabase(connection, config.tableName, config.maxEntries))
              _ = println(s"num googleResponses: ${googleResponses.length}")
            } yield {
              val src: Source[GoogleApiResponse, NotUsed] =
                Source(googleResponses.map((GoogleApiResponse.apply _).tupled))
              val parserFlow: Flow[GoogleApiResponse, AddressParsingResult, NotUsed] =
                JsonParser.buildFlow(100)
              val saveSink: Sink[AddressParsingResult, Future[Done]] = Sink.foreach[AddressParsingResult]{
                case (googleApiResponse: GoogleApiResponse, Success(Some(parsedAddress))) =>
                  //TODO: SNC: Probably we should save to db only `parsedAddress` !?
                  DAO.saveGoogleResponseAndAddress(connection, config.tableName)(googleApiResponse, parsedAddress)
                case (googleApiResponse: GoogleApiResponse, Success(None)) =>
                  DAO.saveGoogleResponseAndEmptyResult(connection, config.tableName)(googleApiResponse)
                case (googleApiResponse: GoogleApiResponse, Failure(error)) =>
                  DAO.saveError(connection, config.tableName)(googleApiResponse.id, error.getMessage)
              }
              //
              src.via(parserFlow).runWith(saveSink).map{_=>
                system.terminate()
              }
            }
        } else if(config.op == "googleQueryAndParse" || config.op == "googleQueryOnly") {
          for{
            connection <- FT.pure(Utils.getDbConnection(config.dbUrl))
            unformattedAddresses <- FT(DAO.getAddressesWithEmptyGoogleResponseFromDatabase(connection, config.tableName, config.maxEntries))
            _ = println(s"num unformattedAddresses to query: ${unformattedAddresses.length}")
          } yield {
            val src: Source[GeoCode, NotUsed] =
              Source(unformattedAddresses.map((GeoCode.apply _).tupled))
            val googleApi = new GoogleApi{
              override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
                s"http://localhost:12500/test?a=${URLEncoder.encode(unformattedAddress, "utf-8")}"
            }
            val googleApiFlow: Flow[GeoCode, GoogleApiResult, _] =
              googleApi buildFlow(GoogleApiKey(config.googleApiKey), config.maxGoogleAPIOpenRequests)
            val parserFlow: Flow[GoogleApiResponse, AddressParsingResult, NotUsed] =
              JsonParser.buildFlow(1)
            val sinkSaveToDb: Flow[AddressParsingResult, Try[Unit], NotUsed] = Flow[AddressParsingResult].mapAsync(1) { apr =>
//            val sinkSaveToDb: Sink[AddressParsingResult, Future[Done]] = Sink.foreach[AddressParsingResult] { apr =>
              val connection = Utils.getDbConnection(config.dbUrl)
              val f = (apr match {
                case (googleApiResponse: GoogleApiResponse, Success(Some(parsedAddress))) =>
                  //TODO: SNC: Probably we should save to db only `parsedAddress` !?
                  DAO.saveGoogleResponseAndAddress(connection, config.tableName)(googleApiResponse, parsedAddress)
                case (googleApiResponse: GoogleApiResponse, Success(None)) =>
                  DAO.saveGoogleResponseAndEmptyResult(connection, config.tableName)(googleApiResponse)
                case (googleApiResponse: GoogleApiResponse, Failure(error)) =>
                  DAO.saveError(connection, config.tableName)(googleApiResponse.id, error.getMessage)
              })
              f.onComplete(_=>connection.close())
              f

            }
            val sinkPrint = Sink.foreach[AddressParsingResult](println)

            src//.take(1)
              .via(googleApiFlow)
              .via(Flow[GoogleApiResult].filter {
                _.isRight
              }.map { case Right(r) => r })
              .via(parserFlow)
                .via(sinkSaveToDb)
              .runWith(Sink.foreach(println)).map { _ =>
                println("==> FINISHING")
                system.terminate()
              }
          }
        } else {
            println(s"Unknown operation ${config.op}!")
            system.terminate()
        }


//        try {
//          if (config.op == "googleQueryAndParse" || config.op == "googleQueryOnly") {
//            val parseAddress = config.op == "googleQueryAndParse"
////            googleQueryAndParse(system, config.maxEntries, config.googleApiKey, config.maxGoogleAPIOpenRequests, config.maxGoogleAPIFatalErrors, parseAddress, config.dbUrl, config.tableName)
//          } else {
//            parseOnly(system, config.maxEntries, config.dbUrl, config.tableName)
//          }
//          println(">>> Press ENTER to exit <<<")
//          StdIn.readLine()
//        } finally {
//          system.terminate()
//        }
      case None => sys.exit(-1)
    }
  }

//  def googleQueryAndParse(system: ActorSystem, maxEntries: Int, googleApiKey: String, maxOpenRequests: Int, maxFatalErrors: Int, parseAddress: Boolean, dbUrl: String, tableName: String) {
//    val conn = Utils.getDbConnection(dbUrl)
//    val unformattedAddresses: List[(Int, String)] = try {
//      DB.getAddressesWithEmptyGoogleResponseFromDatabase(tableName, maxEntries)(conn)
//    } finally { conn.close() }
//
//    println(s"num unformattedAddresses to query: ${unformattedAddresses.length}")
//
//    val db = system.actorOf(DB.props(dbUrl, tableName), "DB")
//    val addressParser = system.actorOf(AddressParserActor.props(db), "AddressParser")
//    val googleGeocoder = system.actorOf(GoogleGeocoder.props(googleApiKey, maxOpenRequests: Int, maxFatalErrors: Int, db, addressParser, parseAddress), "GoogleAPI")
//
//    unformattedAddresses.foreach { case (id, unformattedAddress) => googleGeocoder ! GoogleGeocoder.GeoCode(id, unformattedAddress) }
//  }

//  def parseOnly(system: ActorSystem, maxEntries: Int, dbUrl: String, tableName: String) {
//    val conn = Utils.getDbConnection(dbUrl)
//    val googleResponses: List[(Int, String)] = try {
//      DB.getUnparsedGoogleResponsesFromDatabase(tableName, maxEntries)(conn)
//    } finally { conn.close() }
//
//    println(s"num googleResponses: ${googleResponses.length}")
//
//    val db = system.actorOf(DB.props(dbUrl, tableName), "DB")
//    val addressParser = system.actorOf(AddressParserActor.props(db), "AddressParser")
//
//    googleResponses.foreach { case (id, googleResponse) => addressParser ! AddressParserActor.ParseAddress(id, googleResponse) }
//  }
}
