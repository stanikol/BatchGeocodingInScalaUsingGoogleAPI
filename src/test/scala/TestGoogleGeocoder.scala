//import akka.actor.ActorSystem
//import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
//import akka_parser.flows.GoogleApiFlow.GoogleApiResult
//import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
//
//import concurrent.duration._
//
//class TestGoogleGeocoder() extends TestKit(ActorSystem()) with ImplicitSender
//  with WordSpecLike with Matchers with BeforeAndAfterAll {
//  import GoogleGeocoder._
//
//  override def afterAll {
//    TestKit.shutdownActorSystem(system)
//  }
//
//  "An GoogleGeocoder actor with valid google api key" must {
////googleApiKey: String, maxOpenRequests: Int, maxFatalErrors: Int, db: ActorRef, addressParser: ActorRef, parseAddress: Boolean
//
//    val googleGeoApiActor = system.actorOf(GoogleGeocoder.props("AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc", 10, 5, TestProbe().testActor, TestProbe().testActor, true))
//
//    "replay with error message when unknown message received" in {
//      googleGeoApiActor ! "hello world"
//      receiveOne(1.second).asInstanceOf[GoogleGeocoder.GoogleApiResult].result.isLeft
//    }
//
//    "send google response when asked for valid address" in {
//      googleGeoApiActor ! GeoCode(-1, "Av du Rond-Point 1, Lausanne, Switzerland")
//      val answer = receiveOne(3.second).asInstanceOf[GoogleApiResult]
//      answer.result.isRight
//    }
//
////    "send google response when asked for invalid address" in {
////      googleGeoApiActor ! GeoCode(-1, "Derebasovskaya, Odessa, Ukraine")
////      val answer = receiveOne(3.second).asInstanceOf[GoogleApiResult]
////      answer.result.isRight
////    }
//  }
//
//  "An GoogleGeocoder actor with INVALID google api key" must {
//    //googleApiKey: String, maxOpenRequests: Int, maxFatalErrors: Int, db: ActorRef, addressParser: ActorRef, parseAddress: Boolean
//
//    val googleGeoApiActor = system.actorOf(GoogleGeocoder.props("SomeInvalidKey", 10, 5, TestProbe().ref, TestProbe().ref, true))
//
//    "replay with error" in {
//      googleGeoApiActor ! GeoCode(-1, "Av du Rond-Point 1, Lausanne, Switzerland")
//      receiveOne(1.second).asInstanceOf[GoogleGeocoder.GoogleApiResult].result.isLeft
//    }
//
//  }
//}