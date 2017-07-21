# BatchGeocodingInScalaUsingGoogleAPI

This is a simple Scala program for parsing a list of addresses using google maps api.

The main function for parsing a single address is implemented on `src/main/scala/AddressParser.scala`:
- It builds the proper URL google maps query with the requested address
- It uses Play WS to download the page
- It uses Play Json to parse the json response
- It extracts the data we need and it builds an Address case class

We also use a database with a list of addresses to query and a place to save the results.


# Requirements
You need to install:
- [Java JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/)
- [SBT](http://www.scala-sbt.org/) (build tool for Scala)
- Google Maps Geocoding API key. Get your free account [here](https://developers.google.com/maps/documentation/geocoding/start#get-a-key).
  It will be something like `AIzaSyBwG-Zo6me1yd6V2_ZO7L-3K8A0U1122LA`
- Optional: [IntelliJ](https://www.jetbrains.com/idea/) IDE with the Scala plugin


# Example: parse one address
```
$ export googleApiKey="AIzaSyBwG-Zo6me1yd6V2_ZO7L-3K8A0U1122LA"   # update with your valid api key
$ sbt "runMain ParserCmd $googleApiKey \"Av du Rond-Point 1, Lausanne, Switzerland\""

- parsedAddress:
| - numResults: 1
| - locality: Lausanne
| - areaLevel1: Vaud
| - areaLevel2: Lausanne
| - areaLevel3: -
| - postalCode: 1006
| - country: CH
| - location:
| | - lat: 46.515224
| | - lng: 6.628633
| - formattedAddress: Avenue du Rond-Point 1, 1006 Lausanne, Switzerland
```


# How it works
Given this example address: "Av du Rond-Point 1, Lausanne, Switzerland"

## url
We construct this google query url (replace your valid api key):
```
https://maps.googleapis.com/maps/api/geocode/json?address=Av%20du%20Rond-Point%201,%20Lausanne,%20Switzerland&key=AIzaSyBwG-Zo6me1yd6V2_ZO7L-3K8A0U1122LA
```

## json response
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

## `src/main/scala/AddressParser.scala`
It parses the json response using the play json library.
It uses the implicit formats, which use the name of the case class fields to extract the data from the json response, and it ignores the rest of the json response.
Then it extracts the data of interest (for instance, it finds the `address_component` with at least one type "administrative_area_level_1", and it maps it to `areaLevel1`).
It builds an `Address` case class (defined also at `AddressParser.scala`) as shown in the previous "Example: parse one address" section.

This was enough for our use case, but it is easy to modify `AddressParser.scala` for extracting more info from the json response.


# Batch geocoding
We have a sql database, with a list of addresses to query and a place to save the extracted information.
We have a table called `addresses`, and it contains these fields:
```
- unformattedAddress
- googleResponse
- parseGoogleResponseStatus
- numResults
- locality
- areaLevel1
- areaLevel2
...
```

## create the database
We can create the table as follows:
```
mysql> 
drop table addresses;
create table addresses(
unformattedAddress varchar(500) primary key, 
googleResponse text, 
parseGoogleResponseStatus text, 
numResults int, 
locality varchar(200), areaLevel1 varchar(200), areaLevel2 varchar(200), areaLevel3 varchar(200), postalCode varchar(100), country varchar(100), lat float(10,6), lng float(10,6), 
formattedAddress varchar(500), 
index(numResults), index(googleResponse(100)), index(parseGoogleResponseStatus(100)), index(locality), index(areaLevel1), index(areaLevel2), index(areaLevel3), index(postalCode), index(country), index(lat), index(lng), index(formattedAddress)
);
```

Note: we use `float(10,6)` for storing lat and lng, as proposed by the [google api example](https://developers.google.com/maps/solutions/store-locator/clothing-store-locator?csw=1).
Note: make sure you use `utf8mb4` encoding by creating your database as follows: `create database yourdb default character set = utf8mb4 collate = utf8mb4_unicode_ci;`.
Note: if you get an `ERROR 1709 (HY000): Index column size too large. The maximum column size is 767 bytes.`, update your mysql server to the latest version.


We can insert the addresses to query as follows:
```
mysql> insert into addresses (unformattedAddress) values ('Av du Rond-Point 1, Lausanne, Switzerland');
mysql> insert into addresses (unformattedAddress) values ('Statue, Palayam, Thiruvananthapuram, Kerala 695001, India');
...
```
or from another table/query, such as from patstat:
```
mysql> insert ignore into addresses (unformattedAddress) select distinct(concat(address_freeform, ', ', person_ctry_code)) from tls226_person_orig where nullif(address_freeform, '') is not null;
```


## Run the program
### Database connection details
To connect to the database, we need to specify a JDBC URL. It looks as follows, depending on whether it is mysql, postgresql or something else:
```
$ export dbUrl=jdbc:mysql://SERVER_HOST/DATABASE?user=USER&password=PASSWORD&useSSL=false&useUnicode=yes&characterEncoding=utf8
$ export dbUrl=jdbc:postgresql://SERVER_HOST/DATABASE?user=USER&password=PASSWORD&ssl=true
```

### Run
```
$ export googleApiKey="AIzaSyBwG-Zo6me1yd6V2_ZO7L-3K8A0U1122LA"   # update with your valid api key
$ sbt "runMain BatchParserCmd 20 $googleApiKey $dbUrl"
```
Here, `20` is the max number of google queries to do. It's best to try with a small number first.
The program will also stop if the max number of queries to the google api is exceeded (2500 request per day for the free account).
You can then execute this same command the following day, and it will resume the process (it will not re-download what it has queried already). 


### Query the results
```
mysql> create view addresses_ as select unformattedAddress, concat(left(replace(googleResponse, '\n', ' '), 20), '...') googleResponse, parseGoogleResponseStatus, numResults, locality, areaLevel1, areaLevel2, areaLevel3, postalCode, country, lat, lng, formattedAddress from addresses;
mysql> select * from addresses_;                                                                                                                                                                                                                                        
+------------------------------------------------------------------+-------------------------+-----------------------------------+------------+---------------+--------------------+------------------+-------------+------------+---------+-----------+------------+---------------------------------------------------+
| unformattedAddress                                               | googleResponse          | parseGoogleResponseStatus         | numResults | locality      | areaLevel1         | areaLevel2       | areaLevel3  | postalCode | country | lat       | lng        | formattedAddress                                  |
+------------------------------------------------------------------+-------------------------+-----------------------------------+------------+---------------+--------------------+------------------+-------------+------------+---------+-----------+------------+---------------------------------------------------+
|  & ORANGE HOME;;88 ELM STREET;;TORONTO, ONTARIO M5G 1X8, CA      | {    "results" : [  ... | OK                                |          1 | Toronto       | Ontario            | Toronto Division | NULL        | M5G 1H1    | CA      | 43.657314 | -79.383270 | 35 Elm St, Toronto, ON M5G 1H1, Canada            |
|  (UMDNJ);;30 BERGEN STREET;;NEWARK, NJ 07107, US                 | {    "results" : [  ... | OK                                |          1 | Newark        | New Jersey         | Essex County     | NULL        | 07103      | US      | 40.743530 | -74.190727 | 50 Bergen St, Newark, NJ 07103, USA               |
|  269 00 BAOSTAD SVERIGE, SE                                      | {    "results" : [  ... | OK                                |          2 | NULL          | Skåne län          | NULL             | NULL        | 269 62     | SE      | 56.390842 | 12.782355  | Järnvägsgatan 41, 269 62 Grevie, Sweden           |
|  'INBIO';;UL. B.KOMMUNISTICHESKAYA, 27;;MOSCOW, 109004, SU       | {    "results" : [],... | java.lang.Exception: zero results |       NULL | NULL          | NULL               | NULL             | NULL        | NULL       | NULL    |      NULL |       NULL | NULL                                              |
|  (GSF);;INGOLSTAEDTER LANDSTR. 1;;D-8042 NEUHERBERG, DE          | {    "results" : [],... | java.lang.Exception: zero results |       NULL | NULL          | NULL               | NULL             | NULL        | NULL       | NULL    |      NULL |       NULL | NULL                                              |
|  IN DER TEXTILWIRTSCHAFT;;WICHNERGASSE 9;;A-6800 FELDKIRCH, AT   | NULL                    | NULL                              |       NULL | NULL          | NULL               | NULL             | NULL        | NULL       | NULL    |      NULL |       NULL | NULL                                              |
...
```


### See stats
This is an example results after querying 250 addresses:
```
$ sbt "runMain BatchParserCmd 250 $googleApiKey $dbUrl"

mysql> select count(*), googleResponse is not null as googleResponseStored, parseGoogleResponseStatus, numResults from addresses_ group by googleResponseStored, parseGoogleResponseStatus, numResults;
+----------+----------------------+-----------------------------------+------------+
| count(*) | googleResponseStored | parseGoogleResponseStatus         | numResults |
+----------+----------------------+-----------------------------------+------------+
|   860838 |                    0 | NULL                              |       NULL |
|       58 |                    1 | java.lang.Exception: zero results |       NULL |
|       13 |                    1 | OK                                |          2 |
|      179 |                    1 | OK                                |          1 |
+----------+----------------------+-----------------------------------+------------+
4 rows in set (3.37 sec)
```

It says that:
- 179 cases are perfect (queried and parsed successfully).
- 13 cases, where google provided more than one result because the address is ambiguous (we take only the first result) 
- 58 cases, where google did not find any result
- 860'838 cases that we still didn't query.

We can resume the program and query 100 more addresses:
```
$ sbt "runMain BatchParserCmd 100 $googleApiKey $dbUrl"

mysql> select count(*), googleResponse is not null as googleResponseStored, parseGoogleResponseStatus, numResults from addresses_ group by googleResponseStored, parseGoogleResponseStatus, numResults;
+----------+----------------------+-----------------------------------+------------+
| count(*) | googleResponseStored | parseGoogleResponseStatus         | numResults |
+----------+----------------------+-----------------------------------+------------+
|   860738 |                    0 | NULL                              |       NULL |
|       68 |                    1 | java.lang.Exception: zero results |       NULL |
|       19 |                    1 | OK                                |          2 |
|      263 |                    1 | OK                                |          1 |
+----------+----------------------+-----------------------------------+------------+
4 rows in set (3.67 sec)
```

and so on...

# Re-excuting the parser without re-querying google
We keep a copy of the google responses in the database on the column `googleResponse`.
We do not need to re-query google in the case we change the code of `AddressParser` (to fix an issue, or to extract more data from the json response).

```
mysql> update addresses set parseGoogleResponseStatus = null, numResults = null;
Query OK, 274 rows affected (2.80 sec)
Rows matched: 861088  Changed: 274  Warnings: 0

mysql> select count(*), googleResponse is not null as googleResponseStored, parseGoogleResponseStatus, numResults from addresses_ group by googleResponseStored, parseGoogleResponseStatus, numResults;
+----------+----------------------+---------------------------+------------+
| count(*) | googleResponseStored | parseGoogleResponseStatus | numResults |
+----------+----------------------+---------------------------+------------+
|   860738 |                    0 | NULL                      |       NULL |
|      350 |                    1 | NULL                      |       NULL |
+----------+----------------------+---------------------------+------------+
2 rows in set (3.66 sec)
```

This tells us that we have the google response for 350 queries.
Now we can re-execute the parser again for those 350 addresses (without querying google).

```
$ sbt "runMain BatchParserCmd 0 $googleApiKey $dbUrl"

mysql> select count(*), googleResponse is not null as googleResponseStored, parseGoogleResponseStatus, numResults from addresses_ group by googleResponseStored, parseGoogleResponseStatus, numResults;
+----------+----------------------+-----------------------------------+------------+
| count(*) | googleResponseStored | parseGoogleResponseStatus         | numResults |
+----------+----------------------+-----------------------------------+------------+
|   860738 |                    0 | NULL                              |       NULL |
|       68 |                    1 | java.lang.Exception: zero results |       NULL |
|       19 |                    1 | OK                                |          2 |
|      263 |                    1 | OK                                |          1 |
+----------+----------------------+-----------------------------------+------------+
4 rows in set (3.61 sec)
```

In case we want to force querying google again for any reason, we do as follows:
```
mysql> update addresses set googleResponse = null, parseGoogleResponseStatus = null, numResults = null;
$ sbt "runMain BatchParserCmd 250 $googleApiKey $dbUrl"
```


# Customizing the parser
In order to extract more data from the google json response, you need to 
- modify the case classes `Result` and `ParsedAddress` (on `AddressParser.scala`)
- modify the function `parseAddressFromJsonResponse(googleResponseString: String): ParsedAddress` (on `AddressParser.scala`) 
- modify the function `saveGoogleResponseToDatabase(unformattedAddress: String, googleResponse: String)` (on `BatchParserCmd.scala`)
