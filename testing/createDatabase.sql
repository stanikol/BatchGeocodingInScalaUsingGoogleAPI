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

drop procedure if exists InsertAddresses;

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