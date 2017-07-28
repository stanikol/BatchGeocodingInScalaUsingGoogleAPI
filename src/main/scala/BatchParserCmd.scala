import akka.actor.ActorSystem

import scala.io.StdIn

class BatchParserCmd(googleApiKey: String, dbUrl: String) {
  def run(maxGoogleQueries: Int) {

    val conn = Utils.getDbConnection(dbUrl)
    val unformattedAddresses: List[String] = try {
      DB.getAddressesWithEmptyGoogleResponseFromDatabase(maxGoogleQueries)(conn)
    } finally { conn.close() }

    println(s"num unformattedAddresses to query: ${unformattedAddresses.length}")

    val system: ActorSystem = ActorSystem("System")
    try {
      val db = system.actorOf(DB.props(dbUrl), "DB")
      val addressParser = system.actorOf(AddressParserActor.props(db), "AddressParser")
      val googleGeocoder = system.actorOf(GoogleGeocoder.props(googleApiKey, db, addressParser), "GoogleAPI")

      unformattedAddresses.foreach(e => googleGeocoder ! GoogleGeocoder.GeoCode(e))

      println(">>> Press ENTER to exit <<<")
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }
}

object BatchParserCmd {
  def main(args: Array[String]) {
    val maxGoogleQueries = args(0).toInt
    val googleApiKey = args(1)
    val dbUrl = args(2)

    new BatchParserCmd(googleApiKey, dbUrl).run(maxGoogleQueries)
  }
}
