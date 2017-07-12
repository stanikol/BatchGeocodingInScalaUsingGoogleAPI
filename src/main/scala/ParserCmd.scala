object ParserCmd {
  def main(args: Array[String]) {
    val googleApiKey = args(0)
    val unformattedAddress = args(1)

    println("+++ START.")

    val addressParser = new AddressParser(googleApiKey)

    val address = addressParser.parseAddress(unformattedAddress)
    println(address)

    println("+++ END.")
  }
}
