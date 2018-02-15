package fakeGeoApi.test_responses

object OdessaUkraine {
    val jsonText =
      """{
        |   "results" : [
        |      {
        |         "address_components" : [
        |            {
        |               "long_name" : "Odesa",
        |               "short_name" : "Odesa",
        |               "types" : [ "locality", "political" ]
        |            },
        |            {
        |               "long_name" : "Odes'ka city council",
        |               "short_name" : "Odes'ka city council",
        |               "types" : [ "administrative_area_level_3", "political" ]
        |            },
        |            {
        |               "long_name" : "Odessa Oblast",
        |               "short_name" : "Odessa Oblast",
        |               "types" : [ "administrative_area_level_1", "political" ]
        |            },
        |            {
        |               "long_name" : "Ukraine",
        |               "short_name" : "UA",
        |               "types" : [ "country", "political" ]
        |            },
        |            {
        |               "long_name" : "65000",
        |               "short_name" : "65000",
        |               "types" : [ "postal_code" ]
        |            }
        |         ],
        |         "formatted_address" : "Odesa, Odessa Oblast, Ukraine, 65000",
        |         "geometry" : {
        |            "bounds" : {
        |               "northeast" : {
        |                  "lat" : 46.60042199999999,
        |                  "lng" : 30.8118901
        |               },
        |               "southwest" : {
        |                  "lat" : 46.319522,
        |                  "lng" : 30.6116849
        |               }
        |            },
        |            "location" : {
        |               "lat" : 46.482526,
        |               "lng" : 30.7233095
        |            },
        |            "location_type" : "APPROXIMATE",
        |            "viewport" : {
        |               "northeast" : {
        |                  "lat" : 46.60042199999999,
        |                  "lng" : 30.8118901
        |               },
        |               "southwest" : {
        |                  "lat" : 46.319522,
        |                  "lng" : 30.6116849
        |               }
        |            }
        |         },
        |         "place_id" : "ChIJQ0yGC4oxxkARbBfyjOKPnxI",
        |         "types" : [ "locality", "political" ]
        |      }
        |   ],
        |   "status" : "OK"
        |}
        |""".stripMargin
}
