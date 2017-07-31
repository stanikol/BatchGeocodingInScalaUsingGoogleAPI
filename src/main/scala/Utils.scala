import java.sql.Connection

import anorm.{Row, SimpleSql}

object Utils {
  def getDbConnection(dbUrl: String): Connection = {
    Class.forName("com.mysql.jdbc.Driver").newInstance()
    java.sql.DriverManager.getConnection(dbUrl)
  }

  def getDbConnection: Connection =
    getDbConnection(getProperty("dbUrl").get)

  def getProperty(name: String): Option[String] =
    Option(System.getProperty(name)).orElse(Option(System.getenv(name)))

  def executeOneRowUpdate(sql: SimpleSql[Row])(implicit conn: Connection) {
    val numUpdatedRows = sql.executeUpdate()
    if (numUpdatedRows != 1) throw new Exception(s"error saving to database. numUpdatedRows $numUpdatedRows != 1")
  }

  def textSample(text: Any): String =
    text.toString.replaceAll("\\s+", " ").take(300)
}
