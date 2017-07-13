
import java.sql.Connection

import akka.actor._
import akka.stream._
import akka.util.ByteString
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object Utils {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val ws = AhcWSClient()

  def getBody(future: Future[WSResponse]): ByteString = {
    val response: WSResponse = Await.result(future, Duration.Inf)
    if (response.status != 200)
      throw new Exception(response.statusText)
    response.bodyAsBytes
  }

  def download(url: String): Array[Byte] =
    getBody(ws.url(url).withFollowRedirects(true).get()).toArray

  def wsTerminate() {
    Utils.system.terminate()
    Utils.ws.close()
  }

  def ignoreFutureException(f: Future[Unit])(implicit ec: ExecutionContext): Future[Unit] =
    f.recover { case t: Throwable => t.printStackTrace(); () }

  def getDbConnection(dbUrl: String): Connection = {
    Class.forName("com.mysql.jdbc.Driver").newInstance()
    java.sql.DriverManager.getConnection(dbUrl)
  }

  def getDbConnection: Connection =
    getDbConnection(getProperty("dbUrl").get)

  def getProperty(name: String): Option[String] =
    Option(System.getProperty(name)).orElse(Option(System.getenv(name)))
}
