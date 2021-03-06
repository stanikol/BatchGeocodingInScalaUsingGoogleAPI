package fakeGeoApi.test_responses

object GeocodeSampleOk {
  val jsonText = """{
               |  "results" : [
               |    {
               |      "address_components" : [
               |        {
               |          "long_name" : "81",
               |          "short_name" : "81",
               |          "types" : [ "street_number" ]
               |        },
               |        {
               |          "long_name" : "Rue de Genève",
               |          "short_name" : "Rue de Genève",
               |          "types" : [ "route" ]
               |        },
               |        {
               |          "long_name" : "Lausanne",
               |          "short_name" : "Lausanne",
               |          "types" : [ "locality", "political" ]
               |        },
               |        {
               |          "long_name" : "Lausanne",
               |          "short_name" : "Lausanne",
               |          "types" : [ "administrative_area_level_2", "political" ]
               |        },
               |        {
               |          "long_name" : "Vaud",
               |          "short_name" : "VD",
               |          "types" : [ "administrative_area_level_1", "political" ]
               |        },
               |        {
               |          "long_name" : "Switzerland",
               |          "short_name" : "CH",
               |          "types" : [ "country", "political" ]
               |        },
               |        {
               |          "long_name" : "1004",
               |          "short_name" : "1004",
               |          "types" : [ "postal_code" ]
               |        }
               |      ],
               |      "formatted_address" : "Rue de Genève 81, 1004 Lausanne, Switzerland",
               |      "geometry" : {
               |        "location" : {
               |          "lat" : 46.5249294,
               |          "lng" : 6.613929199999999
               |        },
               |        "location_type" : "RANGE_INTERPOLATED",
               |        "viewport" : {
               |          "northeast" : {
               |            "lat" : 46.52627838029149,
               |            "lng" : 6.615278180291502
               |          },
               |          "southwest" : {
               |            "lat" : 46.5235804197085,
               |            "lng" : 6.612580219708497
               |          }
               |        }
               |      },
               |      "place_id" : "EihSdWUgZGUgR2Vuw6h2ZSA4MSwgMTAwNCBMYXVzYW5uZSwgU3Vpc3Nl",
               |      "types" : [ "street_address" ]
               |    }
               |  ],
               |  "status" : "OK"
               |}
               |""".stripMargin


}
