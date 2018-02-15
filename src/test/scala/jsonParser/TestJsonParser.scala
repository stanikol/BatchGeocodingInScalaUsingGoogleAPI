package jsonParser

import akka.actor.ActorSystem
import akka.stream
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import akka_parser.flows.GoogleApi
import akka_parser.model.{GeoCode, GoogleApiKey, GoogleApiResponse}
import akka_parser.old_parser.AddressParser.GoogleGeocoderFatalError
import fakeGeoApi.test_responses.OdessaUkraine
import org.scalatest._

class  TestJsonParser extends AsyncWordSpec with Matchers with BeforeAndAfterAll  {
  implicit val system = ActorSystem("ComponentLogicTest")
  implicit val materializer = ActorMaterializer()

  import akka_parser.old_parser.AddressParser

  "An AddressParser.parseAddressFromJsonResponse " must {

    "parse good test responses" in {
      val testRespond = AddressParser.parseAddressFromJsonResponse(fakeGeoApi.test_responses.OdessaUkraine.jsonText)
      assert(testRespond.isDefined)
    }

    "and return None on bad" in {
      assertThrows[GoogleGeocoderFatalError](AddressParser.parseAddressFromJsonResponse(fakeGeoApi.test_responses.InvalidApiKey.jsonText))
    }

  }

  "An AddressParser.findGoogleGeocoderFatalErrorFromJsonResponse " must {

    "not find errors in good test response" in {
      val testRespond = AddressParser.findGoogleGeocoderFatalErrorFromJsonResponse(fakeGeoApi.test_responses.OdessaUkraine.jsonText)
      assert(testRespond.isEmpty)
    }

    "find errors in bad test response" in {
      val testRespond = AddressParser.findGoogleGeocoderFatalErrorFromJsonResponse(fakeGeoApi.test_responses.InvalidApiKey.jsonText)
      assert(testRespond.isDefined)
    }

  }

}
