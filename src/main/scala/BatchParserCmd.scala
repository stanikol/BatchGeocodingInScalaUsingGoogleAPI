import akka.actor.ActorSystem

import scala.io.StdIn

object BatchParserCmd {
  def main(args: Array[String]) {
    val maxGoogleQueries = args(0).toInt
    val maxOpenRequests = args(1).toInt
    val maxFatalErrors = args(2).toInt
    val googleApiKey = args(3)
    val dbUrl = args(4)
    val tableName = args(5)

    run(maxGoogleQueries, googleApiKey, maxOpenRequests, maxFatalErrors, dbUrl, tableName)
  }

  def run(maxGoogleQueries: Int, googleApiKey: String, maxOpenRequests: Int, maxFatalErrors: Int, dbUrl: String, tableName: String) {
    val conn = Utils.getDbConnection(dbUrl)
    val unformattedAddresses: List[String] = try {
      DB.getAddressesWithEmptyGoogleResponseFromDatabase(tableName, maxGoogleQueries)(conn)
    } finally { conn.close() }

    println(s"num unformattedAddresses to query: ${unformattedAddresses.length}")

    val system: ActorSystem = ActorSystem("System")
    try {
      val db = system.actorOf(DB.props(dbUrl, tableName), "DB")
      val addressParser = system.actorOf(AddressParserActor.props(db), "AddressParser")
      val googleGeocoder = system.actorOf(GoogleGeocoder.props(googleApiKey, maxOpenRequests: Int, maxFatalErrors: Int, db, addressParser), "GoogleAPI")

      unformattedAddresses.foreach(e => googleGeocoder ! GoogleGeocoder.GeoCode(e))

      println(">>> Press ENTER to exit <<<")
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }
}
