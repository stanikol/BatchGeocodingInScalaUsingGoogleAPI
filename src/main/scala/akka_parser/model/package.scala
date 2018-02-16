package akka_parser

import akka_parser.old_parser.AddressParser.ParsedAddress

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

package object model {

  type Result[T] = Either[String, T]
  type GoogleApiResult = Result[GoogleApiResponse]
  type AddressParsingResult = (GoogleApiResponse, Try[Option[ParsedAddress]])
//  type SaveApiResponseResult = Result[ResultOk.type]
//  type SaveAddressResult = Result[ResultOk.type]

  case class GeoCode(id: Int, unformattedAddress: String)

  case class GoogleApiKey(value: String)

  case class GoogleApiResponse(id: Int, googleResponse: String)

//  case object ResultOk

  case class FT[T](ft: Future[Try[T]]){
    def map[B](f: T => B)(implicit ec: ExecutionContext): FT[B] = FT.map(this)(f)

    def flatMap[B](f: T => FT[B])(implicit ec: ExecutionContext): FT[B] = FT.flatMap(this)(f)

    def foreach(f: T => Unit)(implicit ec: ExecutionContext): Unit = FT.foreach(this)(f)
  }

  object FT {
    def pure[A](a: => A)(implicit ec: ExecutionContext) = FT(Future(Try(a)))

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

    def foreach[A](futureTry: FT[A])(f: A => Unit)(implicit ec: ExecutionContext): Unit = {
      futureTry.ft.foreach {
        case Success(a) => f(a)
        case Failure(error) => ()
      }
    }


  }


}
