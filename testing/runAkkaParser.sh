#!/usr/bin/env bash
export googleApiKey=googleApiKey
export dbUrl="jdbc:mysql://localhost/test?user=root&useSSL=false&useUnicode=yes&characterEncoding=utf8"

#--op=googleQueryAndParse
#--op=parseOnly
#--op=googleQueryOnly
sbt "runMain BatchParserCmd
--op=googleQueryAndParse
--maxEntries=1000000
--maxGoogleAPIOpenRequests=32
--maxGoogleAPIFatalErrors=5
--googleApiKey=$googleApiKey
--dbUrl=$dbUrl
--tableName=addresses"