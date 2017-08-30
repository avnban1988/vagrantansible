@echo off
REM Licensed Materials - Property of IBM Corp.
REM IBM UrbanCode Build
REM IBM UrbanCode Deploy
REM IBM UrbanCode Release
REM IBM AnthillPro
REM (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
REM
REM U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
REM GSA ADP Schedule Contract with IBM Corp.

setlocal

cd %~dp0

set ANT_HOME=opt\apache-ant-1.8.4
set ANT_OPTS=
if "%1"=="-fips" set ANT_OPTS=-Dcom.ibm.jsse2.usefipsprovider=true

%ANT_HOME%\bin\ant.bat -nouserlib -noclasspath -f install.with.groovy.xml install-agent
