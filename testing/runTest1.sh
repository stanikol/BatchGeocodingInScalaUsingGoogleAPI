#!/usr/bin/env bash
#16:11-17:01
export SBT_OPTS="-Xmx10G -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=10G -Xss2M"
#export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home
sbt "runMain BatchParserCmd \
        --op=googleQueryAndParse \
        --maxEntries=1000000 \
        --maxGoogleAPIOpenRequests=10 \
        --maxGoogleAPIFatalErrors=5 \
        --googleApiKey=AIzaSyAl3u33Ea4Nw31iVKP5uPE4KfwW-vnXawc \
        --dbUrl=jdbc:mysql://localhost/test?user=root&useSSL=false&useUnicode=yes&characterEncoding=utf8 \
        --tableName=addresses"