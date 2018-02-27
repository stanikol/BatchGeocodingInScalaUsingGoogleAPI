
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
        AkkaParser.main(config)
      case None =>
        println("Invalid arguments!")
        sys.exit(-1)
    }
  }

}