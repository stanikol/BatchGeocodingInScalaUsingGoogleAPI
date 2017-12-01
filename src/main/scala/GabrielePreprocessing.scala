import java.sql.Connection

import anorm._
import com.univocity.parsers.tsv.{TsvWriter, TsvWriterSettings}

/*
script for Gabriele geocoding task.
given a list of addresses (address + country), it uses a simple formatter ("$address, $country") to query google maps.
from the responses with zero results, it uses another formatter (only $address) to query google maps.
and finally, from those still zero results, it uses another formatter (remove brackets, remove case postal, and others) to query google maps.
it creates also a mapping table with the results.


create table geocoding_gabriele_list (
  STATE_OF_RESIDENCE_CODE varchar(100),
  ADDRESS_TEXT varchar(500),
  index(STATE_OF_RESIDENCE_CODE), index(ADDRESS_TEXT(100))
) engine = InnoDB default character set = utf8mb4 collate = utf8mb4_unicode_ci;

# example entries
insert into geocoding_gabriele_list (ADDRESS_TEXT, STATE_OF_RESIDENCE_CODE) values
("- CH-1042 Bioley-Orjulaz", "CH"),
("15 Karamfil Str., ent.G, fl.6, ap.82, 9000 Varna", "BG"),
("- Intellectual Property Via Pergolesi, 25 I-20124 Milano", "IT"),
("1 Le Marchant Street P.O. Box 186 St. Peter Port Guernsey", "GB"),
("1 & 4, avenue Bois PrEau F-92500 Rueil Malmaison", "FR"),
("Pennygate Bineham Lane Yeovilton Somerset BA22 8EZ", "GB"),
("10 Impasse du Lavanchon F-38760 Antony", "FR");


$ sbt "runMain GabrieleGeocoding <addressTable> <geocodingTable> <mappingTable> <dbUrl>"
$ sbt "runMain GabrieleGeocoding geocoding_gabriele_list geocoding_gabriele geocoding_gabriele_mapping <dbUrl>"
you need to create an empty geocoding_gabriele before running the program. the program shows you an example create table stmt for it.
the GabrieleGeocoding program adds entries to the geocoding_gabriele table, and asks you to run the BatchParserCmd on those.

when done, you run this GabrieleGeocoding program again, and it creates the mappingTable table, and adds more entries to geocoding_gabriele.
you run BatchParserCmd on those again, and you keep this loop until all the geocodeQueryFormatters (3 in this case) have been executed.
*/

object GabrieleGeocoding extends App {
  new GabrieleGeocoding(args(0), args(1), args(2), args(3)).run()
}

class GabrieleGeocoding(addressTable: String, geocodingTable: String, mappingTable: String, dbUrl: String) {
  println("example create stmt: \n" + DB.createTableStmt(geocodingTable, addressLength = 500, addressComponentsLength = 200, maxLongTextIndexLength = 100, maxIndexLength = Some(100)))

  implicit val conn: Connection = Utils.getDbConnection(dbUrl)

  type UnformattedAddress = String
  case class Address (country: String, address: String)
  case class Geocoded(unformattedAddress: UnformattedAddress, numResults: Int)

  def run() {
    val addresses: Set[Address] = getAddressesFromDb

    val geocodeQueryFormatters: List[Address => UnformattedAddress] =
      List(simpleGeocodeQuery _, ruleE2GeocodeQuery _, basicRulesGeocodeQuery _)

    var successGeocodedAddresses: Map[Address, UnformattedAddress] = Map.empty
    var remainingAddresses: Set[Address] = addresses

    for (geocodeQueryFormatter <- geocodeQueryFormatters) {
      val remainingAddresses = addresses -- successGeocodedAddresses.keys

      val remainingAddressesFormatted: Set[(Address, UnformattedAddress)] =
        remainingAddresses.map(address => (address, geocodeQueryFormatter(address)))

      addUnformattedAddresses(remainingAddressesFormatted.map(_._2))

      val numResults: Map[UnformattedAddress, Int] = getNumResults
      // numResults.foreach(println)

      val thisSuccessGeocodedSet: Map[Address, UnformattedAddress] =
        remainingAddressesFormatted.flatMap { case (address: Address, unformattedAddress: UnformattedAddress) =>
          // why getOrElse(..., 0)? because it's possible that addUnformattedAddresses has not added this address depending on the encoding used
          // example: `select "hello" collate utf8_unicode_ci = "héllo" collate utf8_unicode_ci` returns true
          val g = numResults.getOrElse(unformattedAddress, 0)
          if (g > 0) Some(address, unformattedAddress) else None
        }.toMap


      successGeocodedAddresses = successGeocodedAddresses ++ thisSuccessGeocodedSet
    }

    updateSuccessGeocodedAddresses(successGeocodedAddresses)

    println(s"""
        |you might want to execute the following sql queries:
        |
        |create table ${geocodingTable}_details like david.$geocodingTable;
        |insert into ${geocodingTable}_details select * from $geocodingTable where unformattedAddress in (select unformattedAddress from $mappingTable);
        |
        |select count(*), numResults from
        |$addressTable t
        |left join $mappingTable m on (t.STATE_OF_RESIDENCE_CODE = m.STATE_OF_RESIDENCE_CODE and t.ADDRESS_TEXT = m.ADDRESS_TEXT)
        |left join ${geocodingTable}_details d on (m.unformattedAddress = d.unformattedAddress)
        |group by numResults;
      """.stripMargin)

    conn.close()
  }


  def addUnformattedAddresses(unformattedAddresses: Set[String]) {
    println(s"addUnformattedAddresses: ${unformattedAddresses.size}")
    // unformattedAddresses.foreach(println)

    if (unformattedAddresses.nonEmpty) {
      SQL"drop table if exists tmp_#$geocodingTable".executeUpdate()
      SQL"create table tmp_#$geocodingTable (unformattedAddress varchar(500), index(unformattedAddress(100)))".executeUpdate()

      val seq = unformattedAddresses.toList.map(a => Seq[NamedParameter]("unformattedAddress" -> a))

      BatchSql(s"insert into tmp_$geocodingTable (unformattedAddress) values({unformattedAddress})", seq.head, seq.tail: _*).execute()

      val numRowsAdded: Int = SQL"insert into #$geocodingTable (unformattedAddress) select distinct(unformattedAddress) from tmp_#$geocodingTable where unformattedAddress not in (select unformattedAddress from #$geocodingTable)".executeUpdate()
      println(s"numRowsAdded: $numRowsAdded")
      if (numRowsAdded > 0) {
        throw new Exception(s"Attention: run the geocoder now.\nand use this query: select count(*) count, numResults from $geocodingTable group by numResults order by count desc;")
      }
    }
  }

  def updateSuccessGeocodedAddresses(successGeocodedAddresses: Map[Address, UnformattedAddress]): Unit = {
    println(s"updateSuccessGeocodedAddresses: ${successGeocodedAddresses.size}")
    SQL"drop table if exists #$mappingTable".executeUpdate()
    SQL"create table #$mappingTable like #$addressTable".executeUpdate()
    SQL"alter table #$mappingTable add unformattedAddress varchar(500), add index(unformattedAddress(100))".executeUpdate()

    val seq = successGeocodedAddresses.toList.map(a => Seq[NamedParameter]("country" -> a._1.country, "address" -> a._1.address, "unformattedAddress" -> a._2))

    val numInserts = BatchSql(s"insert into $mappingTable (STATE_OF_RESIDENCE_CODE, ADDRESS_TEXT, unformattedAddress) values({country}, {address}, {unformattedAddress})", seq.head, seq.tail: _*)
      .execute().sum

    println(s"numInserts: $numInserts")
    if (successGeocodedAddresses.size != numInserts)
      println("warning: successGeocodedAddresses.size != numInserts")
  }


  def simpleGeocodeQuery(address: Address): UnformattedAddress =
    address.address + ", " + address.country

  def ruleE2GeocodeQuery(address: Address): UnformattedAddress =
    address.address

  def basicRulesGeocodeQuery(address: Address): UnformattedAddress =
    GabrielePreprocessing.transform(address.address + ", " + address.country)

  def getAddressesFromDb(implicit conn: Connection): Set[Address] = {
    println("addressesFromDb")
    import anorm.{ Macro, RowParser }
    val parser: RowParser[Address] = Macro.namedParser[Address]
    SQL"select ADDRESS_TEXT as address, STATE_OF_RESIDENCE_CODE as country from #$addressTable".as(parser.*).toSet
  }

  def getNumResults(implicit conn: Connection): Map[UnformattedAddress, Int] = {
    println("getNumResults")
    val fix = "where numResults is not null"
    SQL"select unformattedAddress, numResults from #$geocodingTable #$fix"
      .as((SqlParser.str(1) ~ SqlParser.int(2)).*).map(SqlParser.flatten).toMap
  }
}

object GabrielePreprocessing {
  def transform(text: String): String =
    gbPostalCode(text).getOrElse(transform1(text))

  val transform1: String => String =
      cleanSpaces _ andThen
      toLowerCase andThen
      removeAmpersandNumber andThen
      removeBrackets andThen
      removeQuotes andThen
      removeCasePostale andThen
      removeLettersAfterNumber andThen
      cleanSpaces andThen
      removeBorderPunctuation

  def cleanSpaces(text: String) =
    text.replaceAll("[\\h\\s\\v]+", " ").trim

  def removeBorderPunctuation(text: String) =
    text.replaceAll("^[ \\p{Punct}]+", "").replaceAll("[ \\p{Punct}]+$", "")

  def toLowerCase(text:String) = text.toLowerCase

  // Eliminate & and the number after it
  // 1 & 2 Coates Castle Coates, West Sussex, RH 10 1EU, GB
  def removeAmpersandNumber(src: String) =
    src.replaceAll("(?<![^ ])& +\\d+\\b", "")

  // Eliminate the text between brackets
  // 1 (2F2) Cargil Court Edinburgh Midlothian EH5 3HE, GB
  // (A British Company) 110 Park Street London W1Y 3RB, GB
  // (a Luxembourg Company) Zweigniederlassung St. Gallen Kreuzackerstrasse 8 CH-8000 St. Gallen, CH
  // note: it does not work with embedded brackets
  def removeBrackets(src: String) =
    src.replaceAll("\\([^)]*\\)", " ")

  // Eliminate the text between '' and "" and '''
  // 'Garnedd' Tanysgrafell Bethesda Bangor, Gwynedd LL57 4AJ, GB
  // "Acorn Cottage" The Close Llanfairfechan Gwynedd, GB
  // 'Abergare'' 4 Ferry Road Rhu Dunbartonshire G84 8NF, GB <<< does this really happen?
  // note: it does not work with embedded brackets
  def removeQuotes(src: String) =
    src.replaceAll("'[^']*'", " ").replaceAll("\"[^\"]*\"", " ")

  // Eliminate “case postable” and the number after it
  // 22, rue du Bois-du-Lan Case postale 84 CH-1217 Meyrin 2, CH
  def removeCasePostale(src: String) =
    src.replaceAll("\\bcase postale \\d+\\b", " ")

  // Removing letters after the number
  // 24A Scawen Road (Ground Floor) London SE8 5AE, GB
  // 24A Warrender Road London N19 5EF, HU
  // 24b Midwinter Avenue Milton Heights Abingdon OX14 4XB, GB
  // 24bis Impasse des Mimosas La Madrague de Gignac F-13820 EnsuEs la Redonne, FR
  // 24C Moss Road Ballymaguigan Magherafelt County Londonderry BT45 6LJ, GB
  // 24c10 rue Simon Jallade F-69110 Ste Foy-Les-Lyon, FR
  // 25 (4F2) Montpelier Edinburgh EH10 4LY, GB
  def removeLettersAfterNumber(src: String) =
    src.replaceAll("\\b(\\d+)[a-z][a-z\\d]+\\b", "$1")

  // https://en.wikipedia.org/wiki/Postcodes_in_the_United_Kingdom#Formatting
  // val gbPostcodeR = "\\b([Gg][Ii][Rr] 0[Aa]{2})|((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z])))) [0-9][A-Za-z]{2})\\b".r
  // a bit more tolerant to accept cases such as "RH 10 1EU" instead of the correct format "RH10 1EU"
  val gbPostcodeR = "\\b([Gg][Ii][Rr] 0[Aa]{2})|((([A-Za-z] ?[0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y] ?[0-9]{1,2})|(([A-Za-z] ?[0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y] ?[0-9]?[A-Za-z])))) [0-9][A-Za-z]{2})\\b".r

  // get the GB postal code or return None
  def gbPostalCode(text: String): Option[String] =
    if (text.matches("(?i).*, (GB|UK)$")) gbPostcodeR.findAllIn(text).toList.lastOption.map(c => c + ", GB")
    else None


  ///////////////////////////

  val testAddresses = List(
    "1 & 2 Coates Castle Coates, West Sussex, RH 10 1EU, GB",
    "1 & 4 avenue Bois PrEau F-92500 Rueil Malmaison, FR",
    "1 (2F2) Cargil Court Edinburgh Midlothian EH5 3HE, GB",
    "(A British Company) 110 Park Street London W1Y 3RB, GB",
    "(a Luxembourg Company) Zweigniederlassung St. Gallen Kreuzackerstrasse 8 CH-8000 St. Gallen, CH",
    "'Garnedd' Tanysgrafell Bethesda Bangor, Gwynedd LL57 4AJ, GB",
    "\"Acorn Cottage\" The Close Llanfairfechan Gwynedd, GB",
    "'Abergare'' 4 Ferry Road Rhu Dunbartonshire G84 8NF, GB",
    "1 Abbots Close Byron Park Knowle Solihull B93 9PP, GB",
    "1 Abbots Quay Monks Ferry Birkenhead Wirral CH41 5LH, GB",
    "22, rue du Bois-du-Lan Case postale 84 CH-1217 Meyrin 2, CH",
    "24A Scawen Road (Ground Floor) London SE8 5AE, GB",
    "24A Warrender Road London N19 5EF, HU",
    "24b Midwinter Avenue Milton Heights Abingdon OX14 4XB, GB",
    "24bis Impasse des Mimosas La Madrague de Gignac F-13820 EnsuEs la Redonne, FR",
    "24C Moss Road Ballymaguigan Magherafelt County Londonderry BT45 6LJ, GB",
    "24c10 rue Simon Jallade F-69110 Ste Foy-Les-Lyon, FR",
    "25 (4F2) Montpelier Edinburgh EH10 4LY, GB"
  )

  def addressesFromDb(dbUrl: String): List[String] = {
    implicit val conn: Connection = Utils.getDbConnection(dbUrl)
    val addresses: List[String] = SQL"select unformattedAddress from addresses_gabriele".as(SqlParser.str(1).*)
    conn.close()
    addresses
  }

  def main(args: Array[String]) {
    val dbUrl = args(0)
    val outCSVFile = args(1)

    val writer = new TsvWriter(new java.io.File(outCSVFile), new TsvWriterSettings())

    def process(src: String) {
      println(s"src: $src")
      val dst = transform(src)
      println(s"dst: $dst")
      println()
      writer.writeRow(Array(src, dst))
    }

    //testAddresses.foreach(process)

    addressesFromDb(dbUrl).foreach(process)

    writer.close()

    println("END")
  }
}

