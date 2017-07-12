object ParserCmd {
  def main(args: Array[String]) {
    val googleApiKey = args(0)
    val unformattedAddress = args(1)

    println("+++ START.")

    val address = AddressParser.parseAddress(googleApiKey, unformattedAddress)
    println(address)

    println("+++ END.")
  }
}
