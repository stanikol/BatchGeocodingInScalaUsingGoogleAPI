#!/usr/bin/env bash
googleApiKey=AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc
#dbUrl="jdbc:mysql://localhost/test?user=root&useSSL=false&useUnicode=yes&characterEncoding=utf8"
curl "https://maps.googleapis.com/maps/api/geocode/jsonText?address=Odessa,%20Ukraine&key=$googleApiKey"
