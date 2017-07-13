# BatchAddressParserUsingGoogleApi

This is a simple Scala program to parse a list of addresses using google maps api.

This is the main function:
```
def parseAddress(address: String): Option[Address]
```

- It builds the proper URL google maps query with the requested address
- It uses Play WS to download the page
- It uses Play Json to parse the json response
- We extract the data we need and we build an Address case class

We also use a database with a list of addresses to query and a place to save the results.
We use anorm to work with the database.


# Example
Given this example address: Av du Rond-Point 1, Lausanne, Switzerland

We make this google query:
```
https://maps.googleapis.com/maps/api/geocode/json?address=Av%20du%20Rond-Point%201,%20Lausanne,%20Switzerland&key=__API_KEY__
```
replace __API_KEY__`

We get this JSON response:
```
{
   "results" : [
      {
         "address_components" : [
            {
               "long_name" : "81",
               "short_name" : "81",
               "types" : [ "street_number" ]
            },
            {
               "long_name" : "Rue de Genève",
               "short_name" : "Rue de Genève",
               "types" : [ "route" ]
            },
            {
               "long_name" : "Lausanne",
               "short_name" : "Lausanne",
               "types" : [ "locality", "political" ]
            },
            {
               "long_name" : "Lausanne",
               "short_name" : "Lausanne",
               "types" : [ "administrative_area_level_2", "political" ]
            },
            {
               "long_name" : "Vaud",
               "short_name" : "VD",
               "types" : [ "administrative_area_level_1", "political" ]
            },
            {
               "long_name" : "Switzerland",
               "short_name" : "CH",
               "types" : [ "country", "political" ]
            },
            {
               "long_name" : "1004",
               "short_name" : "1004",
               "types" : [ "postal_code" ]
            }
         ],
         "formatted_address" : "Rue de Genève 81, 1004 Lausanne, Switzerland",
         "geometry" : {
            "bounds" : {
               "northeast" : {
                  "lat" : 46.5249294,
                  "lng" : 6.613929199999999
               },
               "southwest" : {
                  "lat" : 46.52491699999999,
                  "lng" : 6.613923799999999
               }
            },
            "location" : {
               "lat" : 46.5249294,
               "lng" : 6.613929199999999
            },
            "location_type" : "RANGE_INTERPOLATED",
            "viewport" : {
               "northeast" : {
                  "lat" : 46.52627218029149,
                  "lng" : 6.615275480291501
               },
               "southwest" : {
                  "lat" : 46.5235742197085,
                  "lng" : 6.612577519708497
               }
            }
         },
         "place_id" : "EihSdWUgZGUgR2Vuw6h2ZSA4MSwgMTAwNCBMYXVzYW5uZSwgU3Vpc3Nl",
         "types" : [ "street_address" ]
      }
   ],
   "status" : "OK"
}
```

And we are interested in extracting the following information:
```
  exactMath: true       // if results.length == 1 && status == "OK"
  locality: Lausanne
  areaLevel1: Vaud
  areaLevel2: Lausanne
  areaLevel3: -
  postalCode: 1006
  country: CH
  location:
    lat: 46.5152258
    lng: 6.628633
  formattedAddress: Rue de Genève 81, 1004 Lausanne, Switzerland
```

We have a sql database, with a list of addresses to query and a place to save the extracted information.
We a have a table called addresses.
The table contains a these fields:
```
- unformattedAddress
- googleResponse
- exactMatch
- locality
- areaLevel1
...
```

We use `float(10,6)` for storing lat and lng, as proposed by the [google api example](https://developers.google.com/maps/solutions/store-locator/clothing-store-locator?csw=1).

We can create the table as follows:
```
mysql> create table addresses(unformattedAddress varchar(500) primary key, googleResponse text, parseGoogleResponseStatus text, exactMatch int, locality varchar(255), areaLevel1 varchar(255), areaLevel2 varchar(255), areaLevel3 varchar(255), postalCode varchar(100), country varchar(100), lat float(10,6), lng float(10,6), formattedAddress varchar(500), index(exactMatch), index(googleResponse(100)), index(parseGoogleResponseStatus(100)), index(locality), index(areaLevel1), index(areaLevel2), index(areaLevel3), index(postalCode), index(country), index(lat), index(lng), index(formattedAddress));
```

we can insert values into the table as follows:
```
mysql> insert into addresses (unformattedAddress) values ('Av du Rond-Point 1, Lausanne, Switzerland');
mysql> insert into addresses (unformattedAddress) values ('Statue, Palayam, Thiruvananthapuram, Kerala 695001, India');
...
```
or from another table/query, such as from patstat:
```
mysql> insert into addresses (unformattedAddress) select distinct(concat(address_freeform, ', ', person_ctry_code)) from TLS226_PERSON_ORIG;
```

and show the table:
```
mysql> create view addresses_ as select unformattedAddress, concat(left(replace(googleResponse, '\n', ' '), 20), '...') googleResponse, parseGoogleResponseStatus, exactMatch, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, lat, lng, formattedAddress from addresses;
mysql> select * from addresses_;
mysql> select * from addresses_ where googleResponse is not null;
```


# Run the program
## Database connection details
To connect to the database, we need to specify this URL, depending on whether it is mysql or postgresql, or something else:
```
$ export dbUrl=jdbc:mysql://__SERVER_HOST__/__DATABASE__?user=__USER__&password=__PASSWORD__&useSSL=false&useUnicode=yes&characterEncoding=utf8
$ export dbUrl=jdbc:postgresql://__SERVER_HOST__/__DATABASE__?user=__USER__&password=__PASSWORD__&ssl=true
```

## Run
```
$ sbt run $dbUrl
```


# Query the results
```
mysql> create view addresses_ as select unformattedAddress, concat(left(replace(googleResponse, '\n', ' '), 20), '...') googleResponse, exactMatch, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, lat, lng, formattedAddress from addresses;
mysql> select * from addresses_;                                                                                                                                                                                                                                        
+-----------------------------------------------------------+-------------------------+------------+--------------------+------------+--------------------+------------+------------+---------+-----------+-----------+--------------------------------------------------------------+
| unformattedAddress                                            | googleResponse          | exactMatch | locality           | areaLevel1 | areaLevel2         | areaLevel3 | postalCode | country | lat       | lng       | formattedAddress                                             |
+-----------------------------------------------------------+-------------------------+------------+--------------------+------------+--------------------+------------+------------+---------+-----------+-----------+--------------------------------------------------------------+
| Av du Rond-Point 1, Lausanne, Switzerland                 | {    "results" : [  ... |          1 | Lausanne           | Vaud       | Lausanne           | NULL       | 1006       | CH      | 46.515224 |  6.628633 | Avenue du Rond-Point 1, 1006 Lausanne, Switzerland           |
| Statue, Palayam, Thiruvananthapuram, Kerala 695001, India | {    "results" : [  ... |          1 | Thiruvananthapuram | Kerala     | Thiruvananthapuram | NULL       | 695001     | IN      |  8.498256 | 76.947220 | Statue Rd, Palayam, Thiruvananthapuram, Kerala 695001, India |
+-----------------------------------------------------------+-------------------------+------------+--------------------+------------+--------------------+------------+------------+---------+-----------+-----------+--------------------------------------------------------------+
2 rows in set (0.00 sec)
```
