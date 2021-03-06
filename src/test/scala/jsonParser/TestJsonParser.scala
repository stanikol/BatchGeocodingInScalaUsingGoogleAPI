package jsonParser

import akka.actor.ActorSystem
import akka.stream
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import flows.GoogleApi
import model.{GeoCode, GoogleApiKey, GoogleApiResponse}
import fakeGeoApi.test_responses.OdessaUkraine
import geocoding.AddressParser
import geocoding.AddressParser.GoogleGeocoderFatalError
import org.scalatest._

class  TestJsonParser extends AsyncWordSpec with Matchers with BeforeAndAfterAll  {
  implicit val system = ActorSystem("ComponentLogicTest")
  implicit val materializer = ActorMaterializer()

  import geocoding.AddressParser

  "An AddressParser.parseAddressFromJsonResponse " must {

    "parse good test responses" in {
      val testRespond = AddressParser.parseAddressFromJsonResponse(fakeGeoApi.test_responses.OdessaUkraine.jsonText)
      assert(testRespond.isDefined)
    }

    "Throw `GoogleGeocoderFatalError` with bad test response" in {
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
