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

rem == BEGIN INSTALL MODIFICATIONS =============================================

set AGENT_HOME=@AGENT_HOME@
set ARCH=@ARCH@
set JAVA_HOME=@JAVA_HOME@
set PATHEXT=%PATHEXT%;.cmd;.bat;.exe
rem replace characters in PATHEXT so we can use it in our service's environment
rem change " to emptyString, ; to ';' and # to '#'
set PATHEXT=%PATHEXT:"=%
set PATHEXT=%PATHEXT:;=';'%
set PATHEXT=%PATHEXT:#='#'%

rem == END INSTALL MODIFICATIONS ===============================================

rem This only affects the monitor and should not need to be changed.
rem The worker heap is set in bin\worker-args.conf.
set MONITOR_JAVA_OPTS=-Xmx64m;-Dfile.encoding=UTF-8

set SRV=%AGENT_HOME%\native\%ARCH%\winservice.exe

set AGENT_NAME=ibm-ucd Agent
set DISPLAY_NAME=IBM UrbanCode Deploy Agent
set DESCRIPTION=IBM UrbanCode Deploy Agent

if ""%1"" == ""install"" goto installService
if ""%1"" == ""remove"" goto removeService
if ""%1"" == ""uninstall"" goto removeService

echo Usage: %AGENT_NAME% {install^|remove [servicename]}
goto end

rem -- Remove Service ----------------------------------------------------------

:removeService
set AGENT_NAME=%2
"%SRV%" //DS//%AGENT_NAME%
goto end

rem -- Install Service ---------------------------------------------------------

:installService
set JVM_DLL=auto
set AGENT_NAME=%2
set DISPLAY_NAME=%DISPLAY_NAME% (%2)
set DESCRIPTION=%DESCRIPTION% (%2)

call :getJvmDll
set SVCPATH=%PATH%;%JVM_BASE%\..;%AGENT_HOME%\bin;%AGENT_HOME%\opt\udclient
rem change " to emptyString, ; to ';' and # to '#'
set SVCPATH=%SVCPATH:"=%
set SVCPATH=%SVCPATH:;=';'%
set SVCPATH=%SVCPATH:#='#'%

"%SRV%" //IS//%AGENT_NAME% --DisplayName "%DISPLAY_NAME%" --Install "%SRV%" || goto installFailed
"%SRV%" //US//%AGENT_NAME% --Description "%DESCRIPTION%" || goto configFailed

"%SRV%" //US//%AGENT_NAME% --Jvm "%JVM_DLL%" || goto configFailed
"%SRV%" //US//%AGENT_NAME% --JavaHome "%JAVA_HOME%" || goto configFailed
"%SRV%" //US//%AGENT_NAME% --JvmOptions "%MONITOR_JAVA_OPTS%" || goto configFailed

"%SRV%" //US//%AGENT_NAME% --Environment "PATH=%SVCPATH%;PATHEXT=%PATHEXT%;JAVA_HOME=%JAVA_HOME%;AGENT_HOME=%AGENT_HOME%;ANT_HOME=%AGENT_HOME%\opt\apache-ant-1.8.4;GROOVY_HOME=%AGENT_HOME%\opt\groovy-1.8.8" || goto configFailed

"%SRV%" //US//%AGENT_NAME% --Startup auto || goto configFailed

"%SRV%" //US//%AGENT_NAME% --StartMode jvm || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StartClass com.urbancode.air.agent.AgentMonitor || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StartParams "%AGENT_HOME%;%AGENT_HOME%\bin\worker-args.conf;7000" || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StartPath "%AGENT_HOME%\bin" || goto configFailed

"%SRV%" //US//%AGENT_NAME% --StopMode jvm || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StopClass com.urbancode.air.agent.AgentMonitor || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StopParams "-shutdown" || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StopPath "%AGENT_HOME%\bin" || goto configFailed

"%SRV%" //US//%AGENT_NAME% --LogPath "%AGENT_HOME%\var\log" || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StdOutput auto || goto configFailed
"%SRV%" //US//%AGENT_NAME% --StdError auto || goto configFailed

"%SRV%" //US//%AGENT_NAME% --Classpath "%AGENT_HOME%\monitor\air-monitor.jar" || goto configFailed

goto end

rem -- Subroutines -------------------------------------------------------------

:getJvmDll
    if exist "%JAVA_HOME%\bin\client\jvm.dll" set JVM_BASE=%JAVA_HOME%\bin\client
    if exist "%JAVA_HOME%\bin\server\jvm.dll" set JVM_BASE=%JAVA_HOME%\bin\server
    if exist "%JAVA_HOME%\bin\j9vm\jvm.dll" set JVM_BASE=%JAVA_HOME%\bin\j9vm
    if exist "%JAVA_HOME%\jre\bin\client\jvm.dll" set JVM_BASE=%JAVA_HOME%\jre\bin\client
    if exist "%JAVA_HOME%\jre\bin\server\jvm.dll" set JVM_BASE=%JAVA_HOME%\jre\bin\server
    if exist "%JAVA_HOME%\jre\bin\j9vm\jvm.dll" set JVM_BASE=%JAVA_HOME%\jre\bin\j9vm
    set JVM_DLL=%JVM_BASE%\jvm.dll
goto :eof

:installFailed
    echo Service installation failed
    exit /b 1
goto :eof

:configFailed
    echo Service configuration failed
    exit /b 1
goto :eof
:end
