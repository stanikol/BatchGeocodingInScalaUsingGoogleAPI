package flows

import java.sql.Connection

import akka.Done
import akka.stream.scaladsl.Sink
import model.{DAO, GoogleApiResponse, GoogleApiResult}
import akka_parser.old_parser.Utils

import scala.concurrent.{ExecutionContext, Future}

object SaveApiResponseResultSink {

  def buildSink(dbConnection: Connection, tableName: String)
              (implicit executionContext: ExecutionContext): Sink[GoogleApiResponse, Future[Done]] =
    Sink.foreach{googleApiResponse =>
      DAO.saveGoogleResponse(dbConnection, tableName)(googleApiResponse)
    }

}
