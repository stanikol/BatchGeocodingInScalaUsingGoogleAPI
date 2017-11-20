import akka.actor.ActorSystem

import scala.io.StdIn

object BatchParserCmd {
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
      c.copy(op = x)).text("googleQueryAndParse or parseOnly")

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

        require(config.op == "googleQueryAndParse" || config.op == "parseOnly")

        val system: ActorSystem = ActorSystem("System")
        try {
          if (config.op == "googleQueryAndParse") {
            googleQueryAndParse(system, config.maxEntries, config.googleApiKey, config.maxGoogleAPIOpenRequests, config.maxGoogleAPIFatalErrors, config.dbUrl, config.tableName)
          } else {
            parseOnly(system, config.maxEntries, config.dbUrl, config.tableName)
          }
          println(">>> Press ENTER to exit <<<")
          StdIn.readLine()
        } finally {
          system.terminate()
        }
      case None => sys.exit(1)
    }
  }

  def googleQueryAndParse(system: ActorSystem, maxEntries: Int, googleApiKey: String, maxOpenRequests: Int, maxFatalErrors: Int, dbUrl: String, tableName: String) {
    val conn = Utils.getDbConnection(dbUrl)
    val unformattedAddresses: List[(Int, String)] = try {
      DB.getAddressesWithEmptyGoogleResponseFromDatabase(tableName, maxEntries)(conn)
    } finally { conn.close() }

    println(s"num unformattedAddresses to query: ${unformattedAddresses.length}")

    val db = system.actorOf(DB.props(dbUrl, tableName), "DB")
    val addressParser = system.actorOf(AddressParserActor.props(db), "AddressParser")
    val googleGeocoder = system.actorOf(GoogleGeocoder.props(googleApiKey, maxOpenRequests: Int, maxFatalErrors: Int, db, addressParser), "GoogleAPI")

    unformattedAddresses.foreach { case (id, unformattedAddress) => googleGeocoder ! GoogleGeocoder.GeoCode(id, unformattedAddress) }
  }

  def parseOnly(system: ActorSystem, maxEntries: Int, dbUrl: String, tableName: String) {
    val conn = Utils.getDbConnection(dbUrl)
    val googleResponses: List[(Int, String)] = try {
      DB.getUnparsedGoogleResponsesFromDatabase(tableName, maxEntries)(conn)
    } finally { conn.close() }

    println(s"num googleResponses: ${googleResponses.length}")

    val db = system.actorOf(DB.props(dbUrl, tableName), "DB")
    val addressParser = system.actorOf(AddressParserActor.props(db), "AddressParser")

    googleResponses.foreach { case (id, googleResponse) => addressParser ! AddressParserActor.ParseAddress(id, googleResponse) }
  }
}
