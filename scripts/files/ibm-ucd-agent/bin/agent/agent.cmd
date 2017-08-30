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
set JAVA_DEBUG_OPTS=@JAVA_DEBUG_OPTS@
set JAVA_HOME=@JAVA_HOME@
set PATH=%PATH%;%AGENT_HOME%\opt\udclient
set PATHEXT=%PATHEXT%;.cmd;.bat;.exe
set ANT_HOME=%AGENT_HOME%\opt\apache-ant-1.8.4

rem == END INSTALL MODIFICATIONS ===============================================

set GROOVY_HOME=%AGENT_HOME%\opt\groovy-1.8.8
set JAVACMD=%JAVA_HOME%\bin\java

set JAVA_OPTS=-Dfile.encoding=UTF-8
if exist "%AGENT_HOME%\bin\setenv.cmd" (
  call "%AGENT_HOME%\bin\setenv.cmd"
)


rem -- Execute -----------------------------------------------------------------

rem This only affects the monitor and should not need to be changed.
rem The worker heap is set in bin\worker-args.conf.
set MONITOR_JAVA_OPTS=-Xmx64m -Dfile.encoding=UTF-8
set start_class=com.urbancode.air.agent.AgentWorker
set stop_class=com.urbancode.air.agent.AgentWorker

if ""%1"" == ""run"" goto doRun
if ""%1"" == ""start"" goto doStart
if ""%1"" == ""stop"" goto doStop

echo Usage: agent {run^|start^|stop}
goto end

:doRun
shift
set "WORKER_JAVA_OPTS= "
if ""%1"" == ""-debug"" (
    set WORKER_JAVA_OPTS=%JAVA_DEBUG_OPTS%
)
pushd "%AGENT_HOME%\bin"
"%JAVACMD%" %MONITOR_JAVA_OPTS% -jar "%AGENT_HOME%\monitor\air-monitor.jar" "%AGENT_HOME%" "%AGENT_HOME%\bin\worker-args.conf" 7000 %WORKER_JAVA_OPTS% -Dagent.log.to.console=y -Djava.io.tmpdir="%AGENT_HOME%\var\temp"
popd
goto end

:doStart
shift
set "WORKER_JAVA_OPTS= "
if ""%1"" == ""-debug"" (
    set WORKER_JAVA_OPTS=%JAVA_DEBUG_OPTS%
)
set ACTION=start
pushd "%AGENT_HOME%\bin"
start "Agent" "%JAVACMD%" %MONITOR_JAVA_OPTS% -jar "%AGENT_HOME%\monitor\air-monitor.jar" "%AGENT_HOME%" "%AGENT_HOME%\bin\worker-args.conf" 7000 %WORKER_JAVA_OPTS% -Djava.io.tmpdir="%AGENT_HOME%\var\temp"

popd
goto end

:doStop
shift
pushd "%AGENT_HOME%\bin"
"%JAVACMD%"  %MONITOR_JAVA_OPTS% -jar "%AGENT_HOME%\monitor\air-monitor.jar" "-shutdown" >> "%AGENT_HOME%/var/log/monitor.out" 2>&1
popd
goto end

:end
