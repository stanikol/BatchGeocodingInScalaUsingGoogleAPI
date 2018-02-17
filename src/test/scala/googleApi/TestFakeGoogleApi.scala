package googleApi

import akka.actor.ActorSystem
import akka.stream
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka_parser.model.{GeoCode, GoogleApiKey, GoogleApiResponse}
import com.typesafe.config.ConfigFactory
import fakeGeoApi.test_responses.OdessaUkraine
import org.scalatest._

class TestFakeGoogleApi extends AsyncWordSpec with Matchers with BeforeAndAfterAll  {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //  implicit val executionContext = system.dispatcher


  import akka_parser.flows.GoogleApiFlow

  val googleApi = new GoogleApiFlow{
    override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
      "http://localhost:12500/test"
  }
  import googleApi.buildFlow

  override def afterAll {
//    TestKit.shutdownActorSystem(system)
        system.terminate()
  }

  val config = ConfigFactory.load()
  val dbUrl = config.getString("dbUrl")
  val tableName = "addresses"
  val validGoogleApiKey = GoogleApiKey(config.getString("googleApiKey"))
//  val validGoogleApiKey = GoogleApiKey("AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc")
  val invalidGoogleApiKey = GoogleApiKey("invalid-key")

  "An GoogleApiFlow " must {

    val odessa = GeoCode(-1, "Odessa, Ukraine")

    "answer with OK result with valid GoogleApiKey" in {
      val googleApiFlow = buildFlow(dbUrl, tableName, validGoogleApiKey, 32, 5)
      val testLength = 333
      val testStream = stream.scaladsl.Source.repeat(odessa).take(testLength).via(googleApiFlow)
      testStream.runWith(Sink.seq).map{results =>
        val r = results.map {
          case GoogleApiResponse(_id, body) =>
            body == OdessaUkraine.jsonText && _id == -1
          case _ => false
        }
        assert(r.count(identity) == testLength)
      }
    }

  }

}