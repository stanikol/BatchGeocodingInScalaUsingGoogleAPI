package akka_parser

import akka_parser.old_parser.AddressParser.ParsedAddress

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

package object model {

  type Result[T] = Either[String, T]
  type GoogleApiResult = Result[GoogleApiResponse]
  type SaveApiResponseResult = Result[ResultOk.type]
  type AddressParsingResult = Result[ParsedAddress]
  type SaveAddressResult = Result[ResultOk.type]

  case class GeoCode(id: Int, unformattedAddress: String)

  case class GoogleApiKey(value: String)

  case class GoogleApiResponse(id: Int, googleResponse: String)

  case object ResultOk

  case class FT[T](ft: Future[Try[T]]){
    def map[B](f: T => B)(implicit ec: ExecutionContext): FT[B] = FT.map(this)(f)
    def flatMap[B](f: T => FT[B])(implicit ec: ExecutionContext): FT[B] = FT.flatMap(this)(f)
  }

  object FT {
    def map[A, B](futureTry: FT[A])(f: A => B)(implicit ec: ExecutionContext): FT[B] = {
      val future: Future[Try[B]] = futureTry.ft.map {
        case Success(a) => Try(f(a))
        case Failure(error) => Failure.apply[B](error)
      }
      FT(future)
    }

    def flatMap[A, B](futureTry: FT[A])(f: A => FT[B])(implicit ec: ExecutionContext): FT[B] = FT {
      futureTry.ft.flatMap {
        case Success(a) => f(a).ft
        case Failure(error) => Future{Failure(error)}
      }
    }
  }


}
