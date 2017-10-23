import sext._

object ParserCmd {
  def run(googleApiKey: String, unformattedAddress: String) {
    println(s"unformattedAddress: $unformattedAddress")

    val queryUrl = AddressParser.url(googleApiKey, unformattedAddress)
    println(s"query url: $queryUrl")

    val googleResponse: String = Utils.download(queryUrl)
    println(s"googleResponse: $googleResponse")

    val parsedAddress = AddressParser.parseAddressFromJsonResponse(googleResponse)
    println(s"parsedAddress: ${parsedAddress.valueTreeString}")
  }

  def main(args: Array[String]) {
    val googleApiKey = args(0)
    val unformattedAddress = args(1)

    run (googleApiKey, unformattedAddress)
  }
}
