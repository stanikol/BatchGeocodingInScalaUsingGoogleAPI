//package googleApi
//
//import akka.actor.ActorSystem
//import akka.stream
//import akka.stream.ActorMaterializer
//import akka.stream.scaladsl.{Sink, Source}
//import akka.testkit.TestKit
//import model.{GeoCode, GoogleApiKey, GoogleApiResponse}
//import akka_parser.old_parser.Utils
//import com.typesafe.config.ConfigFactory
//import org.scalatest._
//import fakeGeoApi.test_responses.{InvalidApiKey, OdessaUkraine}
//
//import scala.concurrent.Await
//import scala.concurrent.duration.Duration
//
//class TestRealGoogleApi extends AsyncWordSpec with Matchers with BeforeAndAfterAll  {
//
//  implicit val system = ActorSystem()
//  implicit val materializer = ActorMaterializer()
////  implicit val executionContext = system.dispatcher
//
//
//  import flows.GoogleApi
//  val googleApi = new GoogleApi
//  import googleApi.buildFlow
//
//  override def afterAll {
//    TestKit.shutdownActorSystem(system)
////    system.terminate()
//  }
//
//  val config = ConfigFactory.load()
//  val dbUrl = config.getString("dbUrl")
//  val dbConn = Utils.getDbConnection(dbUrl)
//  val tableName = "addresses"
//  val validGoogleApiKey = GoogleApiKey(config.getString("googleApiKey"))
//  val invalidGoogleApiKey = GoogleApiKey("invalid-key")
//
//  "An GoogleApi " must {
//
//    val odessa = GeoCode(-1, "Odessa, Ukraine")
//
//    "answer with bad result with invalid GoogleApiKey" in {
//      val googleApiFlow = buildFlow(dbConn, tableName, invalidGoogleApiKey, 1, 5, 1000000)
//      val testStream = stream.scaladsl.Source.single(odessa).via(googleApiFlow)
//      testStream.runWith(Sink.head).map{
//        case GoogleApiResponse(_id, body) =>
//          assert(body == InvalidApiKey.jsonText && _id == -1)
//        case _ => assert(false)
//      }
//    }
//
//    "answer with OK result with valid GoogleApiKey" in {
//      val googleApiFlow = buildFlow(dbConn, tableName, validGoogleApiKey, 1, 5, 1000000)
//      val testStream = stream.scaladsl.Source.single(odessa).via(googleApiFlow)
//      testStream.runWith(Sink.head).map{
//        case GoogleApiResponse(_id, body) =>
//          assert(body == OdessaUkraine.jsonText && _id == -1)
//      }
//    }
//
//  }
//
////  "GoogleApi.flow" must {
////
////    val testGoogleApiFlow = new GoogleApi{
////      override def buildUrl(googleApiKey: String, unformattedAddress: String): String =
////        "http://localhost:12500/test"
////    }.buildFlow(validGoogleApiKey, 1)
////
////    "fake server is running" in {
////        val r = Await.result(
////          Source.single(GeoCode(-1, "Invalid Address"))
////                  .via(testGoogleApiFlow)
////                      .runWith(Sink.head)
////          ,
////          Duration.Inf
////        )
////        assert(r.isRight)
////    }
//
////    "ask server" in {
////        Source.single(GeoCode(-1, "Invalid Address"))
////          .via(testGoogleApiFlow)
////          .runWith(Sink.head).map(r=>assert(false))
//////          .runWith(Sink.head).map(r=>assert(r.isLeft))
////    }
//
//
////  }
//
//}