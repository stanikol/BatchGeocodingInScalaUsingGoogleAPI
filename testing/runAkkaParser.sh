#!/usr/bin/env bash
#--op=googleQueryAndParse
#--op=parseOnly
#--op=googleQueryOnly
export dbUrl=jdbc:mysql://localhost/test?user=root&useSSL=false&useUnicode=yes&characterEncoding=utf8
export $googleApiKey=googleApiKey

sbt "runMain akka_parser.BatchParserCmd2
--op=googleQueryOnly
--maxEntries=1000000
--maxGoogleAPIOpenRequests=32
--maxGoogleAPIFatalErrors=5
--googleApiKey=$googleApiKey
--dbUrl=$dbUrl
--tableName=addresses"