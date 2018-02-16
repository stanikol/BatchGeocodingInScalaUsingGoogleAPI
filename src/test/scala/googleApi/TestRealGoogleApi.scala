package googleApi

import akka.actor.ActorSystem
import akka.stream
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import akka_parser.model.{GeoCode, GoogleApiKey, GoogleApiResponse}
import org.scalatest._
import fakeGeoApi.test_responses.{InvalidApiKey, OdessaUkraine}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class TestRealGoogleApi extends AsyncWordSpec with Matchers with BeforeAndAfterAll  {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
//  implicit val executionContext = system.dispatcher


  import akka_parser.flows.GoogleApiCall
  val googleApi = new GoogleApiCall
  import googleApi.buildFlow

  override def afterAll {
    TestKit.shutdownActorSystem(system)
//    system.terminate()
  }

  val validGoogleApiKey = GoogleApiKey("AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc")
  val invalidGoogleApiKey = GoogleApiKey("invalid-key")

  "An GoogleApiCall " must {

    val odessa = GeoCode(-1, "Odessa, Ukraine")

    "answer with bad result with invalid GoogleApiKey" in {
      val googleApiFlow = buildFlow(invalidGoogleApiKey, 1)
      val testStream = stream.scaladsl.Source.single(odessa).via(googleApiFlow)
      testStream.runWith(Sink.head).map{
        case Right(GoogleApiResponse(_id, body)) =>
          assert(body == InvalidApiKey.jsonText && _id == -1)
        case _ => assert(false)
      }
    }

    "answer with OK result with valid GoogleApiKey" in {
      val googleApiFlow = buildFlow(validGoogleApiKey, 1)
      val testStream = stream.scaladsl.Source.single(odessa).via(googleApiFlow)
      testStream.runWith(Sink.head).map{
        case Right(GoogleApiResponse(_id, body)) =>
          assert(body == OdessaUkraine.jsonText && _id == -1)
        case _ => assert(false)
      }
    }

  }

//  "GoogleApiCall.flow" must {
//
//    val testGoogleApiFlow = new GoogleApiCall{
//      override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
//        "http://localhost:12500/test"
//    }.buildFlow(validGoogleApiKey, 1)
//
//    "fake server is running" in {
//        val r = Await.result(
//          Source.single(GeoCode(-1, "Invalid Address"))
//                  .via(testGoogleApiFlow)
//                      .runWith(Sink.head)
//          ,
//          Duration.Inf
//        )
//        assert(r.isRight)
//    }

//    "ask server" in {
//        Source.single(GeoCode(-1, "Invalid Address"))
//          .via(testGoogleApiFlow)
//          .runWith(Sink.head).map(r=>assert(false))
////          .runWith(Sink.head).map(r=>assert(r.isLeft))
//    }


//  }

}