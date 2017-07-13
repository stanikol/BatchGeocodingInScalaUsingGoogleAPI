object ParserCmd extends App {
  try {
    val googleApiKey = args(0)
    val unformattedAddress = args(1)

    println("+++ START.")

    val queryAndResult = AddressParser.parseAddress(googleApiKey, unformattedAddress)
    println(queryAndResult)

    println("+++ END.")

  } finally {
    Utils.wsTerminate()
  }
}
