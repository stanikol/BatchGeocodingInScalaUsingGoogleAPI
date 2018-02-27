# steps to test the program locally:


1. run a web server on a directory which contains the attached geocode-sample.json file, by executing the following:
$ python -m SimpleHTTPServer 12500

open this url on your web browser, to test it works:
http://localhost:12500/geocode-sample.json


2. edit the file AddressParser.scala, and replace:
  def url(googleApiKey: String, unformattedAddress: String): String =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(unformattedAddress, "UTF-8")}&key=${URLEncoder.encode(googleApiKey, "UTF-8")}"
by
  def url(googleApiKey: String, unformattedAddress: String): String =
    s"http://localhost:12500/geocode-sample.json"


3. create a mysql database.
install mysql on you local machine, and execute this query:

create database test default character set utf8mb4 collate utf8mb4_unicode_ci;
use test
drop table if exists addresses;
create table addresses (
  id int not null auto_increment primary key,
  unformattedAddress varchar(500) not null,
  ts timestamp default current_timestamp on update current_timestamp,
  googleResponse longtext,
  parseGoogleResponseStatus longtext,
  numResults int,
  formattedAddress varchar(500),
  lat float(10,6), lng float(10,6), mainType varchar(200), types longtext, viewportArea float,
  administrative_area_level_1 varchar(200), administrative_area_level_2 varchar(200), administrative_area_level_3 varchar(200), administrative_area_level_4 varchar(200), administrative_area_level_5 varchar(200), airport varchar(200), country varchar(200), establishment varchar(200), floor varchar(200), locality varchar(200), natural_feature varchar(200), neighborhood varchar(200), park varchar(200), point_of_interest varchar(200), post_box varchar(200), postal_code varchar(200), postal_code_prefix varchar(200), postal_code_suffix varchar(200), postal_town varchar(200), premise varchar(200), route varchar(200), street_address varchar(200), street_number varchar(200), sublocality varchar(200), sublocality_level_1 varchar(200), sublocality_level_2 varchar(200), sublocality_level_3 varchar(200), sublocality_level_4 varchar(200), sublocality_level_5 varchar(200), subpremise varchar(200), ward varchar(200),
  unique index(unformattedAddress), index(ts), index(googleResponse(100)), index(parseGoogleResponseStatus(100)), index(numResults), index(formattedAddress),
  index(lat), index(lng), index(mainType), index(types(100)), index(viewportArea),
  index(administrative_area_level_1), index(administrative_area_level_2), index(administrative_area_level_3), index(administrative_area_level_4), index(administrative_area_level_5), index(airport), index(country), index(establishment), index(floor), index(locality), index(natural_feature), index(neighborhood), index(park), index(point_of_interest), index(post_box), index(postal_code), index(postal_code_prefix), index(postal_code_suffix), index(postal_town), index(premise), index(route), index(street_address), index(street_number), index(sublocality), index(sublocality_level_1), index(sublocality_level_2), index(sublocality_level_3), index(sublocality_level_4), index(sublocality_level_5), index(subpremise), index(ward)
) engine = InnoDB default character set = utf8mb4 collate = utf8mb4_bin row_format=dynamic;

DELIMITER $$
CREATE PROCEDURE InsertAddresses(IN NumRows INT)
    BEGIN
        DECLARE i INT;
        SET i = 1;
        START TRANSACTION;
        WHILE i <= NumRows DO
            INSERT INTO addresses (unformattedAddress) VALUES (concat('address_', i));
            SET i = i + 1;
        END WHILE;
        COMMIT;
    END$$
DELIMITER ;
CALL InsertAddresses(1000000);

select count(*) from addresses;  # this should be 1000000


4. run the program
$ export dbUrl="jdbc:mysql://SERVER_HOST/DATABASE?user=USER&password=PASSWORD&useSSL=false&useUnicode=yes&characterEncoding=utf8"
$ export googleApiKey="not_used"
$ sbt "runMain BatchParserCmd --op=googleQueryAndParse --maxEntries=1000000 --maxGoogleAPIOpenRequests=10 --maxGoogleAPIFatalErrors=5 --googleApiKey="$googleApiKey" --dbUrl="$dbUrl" --tableName=addresses"


Hi, David.
(1) I added streaming support when reading from db. So now database reading is streamed row by row.
(2) FT object is removed.
(3) Database writing now reuse one shared connection. It also closes nicely.
(4) GoogleApi, when connection is lost or other TCP error occurs, now constantly retries to query google api again and again.
(5) Code refined in a way that `git diff` can show what is changed.
I also tried to add some extra working hours, but probably I failed because they are still not in my working diary, so I wrote message to upwork support.
I am waiting for your feedback.
Sincerely,
Stan
