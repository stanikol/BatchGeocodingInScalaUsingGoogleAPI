import sext._

object ParserCmd extends App {
  try {
    val googleApiKey = args(0)
    val unformattedAddress = args(1)

    val queryAndResult = AddressParser.parseAddress(googleApiKey, unformattedAddress)
    println(queryAndResult.valueTreeString)

  } finally {
    Utils.wsTerminate()
  }
}
