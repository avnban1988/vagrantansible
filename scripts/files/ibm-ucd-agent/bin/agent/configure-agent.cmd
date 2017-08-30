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
set JAVA_HOME=@JAVA_HOME@

rem == END INSTALL MODIFICATIONS ===============================================

pushd "%AGENT_HOME%\bin"
"%JAVA_HOME%\bin\java" -jar "%AGENT_HOME%\monitor\launcher.jar" "%AGENT_HOME%\bin\classpath.conf" com.urbancode.air.agent.AgentConfigurator %*
popd
