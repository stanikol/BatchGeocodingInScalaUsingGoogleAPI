import sext._

object ParserCmd {
  def run(googleApiKey: String, unformattedAddress: String) {
    val queryAndResult = AddressParser.parseAddress(googleApiKey, unformattedAddress)
    println(queryAndResult.valueTreeString)
  }

  def main(args: Array[String]) {
    try {
      val googleApiKey = args(0)
      val unformattedAddress = args(1)

      run (googleApiKey, unformattedAddress)

    } finally {
      Utils.wsTerminate()
    }
  }
}
