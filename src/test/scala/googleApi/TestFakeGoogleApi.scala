package googleApi

import akka.actor.ActorSystem
import akka.stream
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka_parser.model.{GeoCode, GoogleApiKey, GoogleApiResponse}
import fakeGeoApi.test_responses.OdessaUkraine
import org.scalatest._

class TestFakeGoogleApi extends AsyncWordSpec with Matchers with BeforeAndAfterAll  {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //  implicit val executionContext = system.dispatcher


  import akka_parser.flows.GoogleApiCall

  val googleApi = new GoogleApiCall{
    override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
      "http://localhost:12500/test"
  }
  import googleApi.buildFlow

  override def afterAll {
//    TestKit.shutdownActorSystem(system)
        system.terminate()
  }

  val validGoogleApiKey = GoogleApiKey("AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc")
  val invalidGoogleApiKey = GoogleApiKey("invalid-key")

  "An GoogleApiCall " must {

    val odessa = GeoCode(-1, "Odessa, Ukraine")

    "answer with OK result with valid GoogleApiKey" in {
      val googleApiFlow = buildFlow(validGoogleApiKey, 32)
      val testLength = 333
      val testStream = stream.scaladsl.Source.repeat(odessa).take(testLength).via(googleApiFlow)
      testStream.runWith(Sink.seq).map{results =>
        val r = results.map {
          case Right(GoogleApiResponse(_id, body)) =>
            body == OdessaUkraine.jsonText && _id == -1
          case _ => false
        }
        assert(r.count(identity) == testLength)
      }
    }

  }

}