package fakeGeoApi.test_responses

object GeocodeSampleRequestDenied {
  val jsonText =
    """{
      |   "error_message" : "The provided API key is invalid.",
      |   "results" : [],
      |   "status" : "REQUEST_DENIED"
      |}
    """.stripMargin

}
