package akka_parser.flows

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink}
import akka_parser.model
import akka_parser.model.{AddressParsingResult, DAO, FT, GoogleApiResult}
import akka_parser.old_parser.Utils
import old_parser.DB

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SaveApiResponseResult {

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
