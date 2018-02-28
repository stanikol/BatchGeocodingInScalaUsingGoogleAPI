#!/usr/bin/env bash
export googleApiKey=AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc
export dbUrl="jdbc:mysql://localhost/test?user=root&useSSL=false&useUnicode=yes&characterEncoding=utf8"
#export dbUrl="jdbc:mysql://localhost"

#--op=googleQueryAndParse
#--op=parseOnly
#--op=googleQueryOnly
sbt "runMain geocoding.BatchParserCmd
--op=googleQueryAndParse
--maxEntries=1000000
--maxGoogleAPIOpenRequests=32
--maxGoogleAPIFatalErrors=5
--googleApiKey=$googleApiKey
--dbUrl=$dbUrl
--tableName=addresses"