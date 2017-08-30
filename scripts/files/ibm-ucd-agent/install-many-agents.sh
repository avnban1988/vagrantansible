#!/bin/sh
# Licensed Materials - Property of IBM Corp.
# IBM UrbanCode Build
# IBM UrbanCode Deploy
# IBM UrbanCode Release
# IBM AnthillPro
# (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
#
# U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
# GSA ADP Schedule Contract with IBM Corp.
################################################################################
# WARNING:  This script is an example of how to create an unattended
# installation script.  The parameters below as well as the script content
# may and probably WILL need to be modified to accommodate your situation.
#
# The parameters which can be modified to alter the unattended installation.
################################################################################
JAVA_HOME=`readlink -f $(which java)`
AGENT_JAVA_HOME=`cut -d '/' -f 1-6 <<< $JAVA_HOME`

CONNECT_VIA_RELAY=N
INSTALL_AGENT_REMOTE_HOST=3.3.87.13
INSTALL_AGENT_REMOTE_PORT=7918

INSTALL_AGENT_REMOTE_PORT_MUTUAL_AUTH=N
INSTALL_AGENT_RELAY_HTTP_PORT=20080

INSTALL_AGENT_NAME=`hostname -f`
INSTALL_AGENT_DIR=/opt/urbancode/agent

agent_count=1

################################################################################
# The installation script.
################################################################################

SHELL_NAME=$0
SHELL_PATH=`dirname ${SHELL_NAME}`

if [ "." = "$SHELL_PATH" ] 
then
    SHELL_PATH=`pwd`
fi
cd ${SHELL_PATH}

ANT_HOME=opt/apache-ant-1.8.4
export ANT_HOME

chmod +x opt/apache-ant-1.8.4/bin/ant

# Run the installation.
i=0
while [ $i -lt "$agent_count" ]
do
  opt/apache-ant-1.8.4/bin/ant -nouserlib -noclasspath -f install.with.groovy.xml \
    "-Dinstall-agent=true" \
    "-DIBM UrbanCode Deploy/java.home=$AGENT_JAVA_HOME" \
    "-Dlocked/agent.jms.remote.host=$INSTALL_AGENT_REMOTE_HOST" \
    "-Dlocked/agent.jms.remote.port=$INSTALL_AGENT_REMOTE_PORT" \
    "-Dlocked/agent.mutual_auth=$INSTALL_AGENT_REMOTE_PORT_MUTUAL_AUTH" \
    "-Dlocked/agent.name=$INSTALL_AGENT_NAME" \
    "-Dlocked/agent.home=$INSTALL_AGENT_DIR" \
    install-non-interactive
  i=`expr $i + 1`
done
