package flows

import java.sql.Connection

import akka.NotUsed
import akka.stream.scaladsl.Flow
import model.{AddressParsingResult, DAO, GoogleApiResponse}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object SaveAddressParsingResult {

  def buildFlow(connection: Connection, tableName: String)
               (parallelism: Int)
               (implicit executionContext: ExecutionContext): Flow[AddressParsingResult, Try[Unit], NotUsed] =
    Flow[AddressParsingResult].mapAsync(parallelism) {
        case (googleApiResponse: GoogleApiResponse, Success(Some(parsedAddress))) =>
          DAO.saveGoogleResponseAndAddress(connection, tableName)(googleApiResponse, parsedAddress)
        case (googleApiResponse: GoogleApiResponse, Success(None)) =>
          DAO.saveGoogleResponseAndEmptyResult(connection, tableName)(googleApiResponse)
        case (googleApiResponse: GoogleApiResponse, Failure(error)) =>
          DAO.saveError(connection, tableName)(googleApiResponse.id, error.getMessage)
  }

}
