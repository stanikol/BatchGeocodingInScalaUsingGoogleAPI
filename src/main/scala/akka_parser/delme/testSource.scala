package akka_parser.delme

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka_parser.model.DAO
import akka_parser.old_parser.Utils
import com.typesafe.config.ConfigFactory

object testSource extends App {
  implicit val actorSystsem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystsem.dispatcher

  val cfg = ConfigFactory.load()
  val conn = Utils.getDbConnection(cfg.getString("dbUrl"))
  println(s"Conn is $conn")
  val src = DAO.getSourceOfUnparsedGoogleResponsesFromDatabase(conn, "addresses", 100000)

  val f = src.runWith(Sink.seq)
  f.onComplete{r =>
    conn.close()
    actorSystsem.terminate()
    printf(s"res is $r")
  }



}
