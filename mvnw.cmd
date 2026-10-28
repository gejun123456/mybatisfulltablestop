@echo off
setlocal
set "MVN_EXEC=G:\idmdownload\Compressed\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd"
if exist "%MVN_EXEC%" (
    "%MVN_EXEC%" %*
) else (
    mvn %*
)
