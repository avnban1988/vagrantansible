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
REM ###########################################################################
REM WARNING:  This script is an example of how to create an unattended
REM installation script.  The parameters below as well as the script content
REM may and probably WILL need to be modified to accommodate your situation.
REM
REM The parameters which can be modified to alter the unattended installation.
REM ###########################################################################

set AGENT_JAVA_HOME=C:\Program Files\Java\jre6

set CONNECT_VIA_RELAY=N
set INSTALL_AGENT_REMOTE_HOST=<PUT SERVER HOSTNAME HERE>
set INSTALL_AGENT_REMOTE_PORT=7918
REM IF CONNECT_VIA_RELAY
REM set INSTALL_AGENT_REMOTE_PORT=7916
REM ELSE

set INSTALL_AGENT_REMOTE_PORT_MUTUAL_AUTH=N
REM IF CONNECT_VIA_RELAY
set INSTALL_AGENT_RELAY_HTTP_PORT=20080
REM ELSE
REM set INSTALL_AGENT_RELAY_HTTP_PORT=

set INSTALL_AGENT_NAME=IBM UrbanCode Deploy-agent
set INSTALL_AGENT_DIR=C:\Program Files\IBM UrbanCode Deploy\agent

set INSTALL_SERVICE=N
set INSTALL_SERVICE_NAME=ibm-ucdagent
set INSTALL_SERVICE_LOGIN=.\localsystem
set INSTALL_SERVICE_PASSWORD=password
set INSTALL_SERVICE_AUTOSTART=N

set agent_count=10

REM ###########################################################################
REM The installation script
REM ###########################################################################

cd %~dp0

set ANT_HOME=opt\apache-ant-1.8.4

set i=0
:LOOP
  CALL opt\apache-ant-1.8.4\bin\ant.bat -nouserlib -noclasspath -f install.with.groovy.xml ^
        "-Dinstall-agent=true" ^
        "-DIBM UrbanCode Deploy/java.home=%AGENT_JAVA_HOME%" ^
        "-Dlocked/agent.jms.remote.host=%INSTALL_AGENT_REMOTE_HOST%" ^
        "-Dlocked/agent.jms.remote.port=%INSTALL_AGENT_REMOTE_PORT%" ^
        "-Dlocked/agent.http.proxy.host=%INSTALL_AGENT_RELAY_HTTP_PORT%" ^
        "-Dlocked/agent.mutual_auth=%INSTALL_AGENT_REMOTE_PORT_MUTUAL_AUTH%" ^
        "-Dlocked/agent.name=%INSTALL_AGENT_NAME%-%i%" ^
        "-Dlocked/agent.home=%INSTALL_AGENT_DIR%-%i%" ^
        "-Dlocked/agent.service=%INSTALL_SERVICE%" ^
        "-Dlocked/agent.service.name=%INSTALL_SERVICE_NAME%" ^
        "-Dlocked/agent.service.login=%INSTALL_SERVICE_LOGIN%" ^
        "-Dlocked/agent.service.password=%INSTALL_SERVICE_PASSWORD%" ^
        "-Dlocked/agent.service.autostart=%INSTALL_SERVICE_AUTOSTART%" ^
	install-non-interactive
  set /a i=i+1
  if "%i%"=="%agent_count%" goto END
  goto LOOP
:END
