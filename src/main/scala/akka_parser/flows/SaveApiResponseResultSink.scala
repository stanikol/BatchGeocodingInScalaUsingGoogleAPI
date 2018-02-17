package akka_parser.flows

import akka.Done
import akka.stream.scaladsl.Sink
import akka_parser.model.{DAO, FT, GoogleApiResult}
import akka_parser.old_parser.Utils

import scala.concurrent.{ExecutionContext, Future}

object SaveApiResponseResultSink {

  def buildSink(dbUrl: String, tableName: String)
              (implicit executionContext: ExecutionContext): Sink[GoogleApiResult, Future[Done]] =
    Sink.foreach{
      case Right(googleApiResponse) =>
        for{
          c  <- FT.pure{Utils.getDbConnection(dbUrl)}
          _ <- FT(DAO.saveGoogleResponse(c, tableName)(googleApiResponse))
          _ <- FT.pure(c.close())
        } {()}
      case Left(error) =>
    }

}
