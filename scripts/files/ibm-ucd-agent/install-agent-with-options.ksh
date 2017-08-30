#!/bin/ksh
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
# This script performs a unattended agent installation using a property
# file that contains the necessary installation properties.
# The script requires one argument which is the path to the property file.
################################################################################

SHELL_NAME=$0
SHELL_PATH=`dirname ${SHELL_NAME}`

if [ "." = "$SHELL_PATH" ]
then
    SHELL_PATH=`pwd`
fi
cd ${SHELL_PATH}

# TODO replace the hard-coded versions on these strings

ANT_HOME=opt/apache-ant-1.8.4
GROOVY_HOME=opt/groovy-1.8.8
JRE_ARCHIVE=@JRE_ARCHIVE_NAME@
JRE=@JRE_PREFIX@

export ANT_HOME
export GROOVY_HOME
export JAVA_HOME

AGENT_INSTALL_DIR=/opt/ibm-ucd
PROXY_PORT=20080
DISABLE_HTTP_FAILOVER=false
INITIAL_TEAM="System Team"
UCD_Agent_Properties_File=agent.install.properties

# Get options
# TODO add options for proxy host and proxy port
while getopts "n:p:s:x:l:k:t:c:rdvh" opt; do
  case $opt in
    n)
      UCD_AGENT_NAME=$OPTARG
      ;;
    p)
      UCD_PORT=$OPTARG
      ;;
    s)
      UCD_REMOTE_HOST=$OPTARG
      ;;
    l)
      PROXY_PORT=$OPTARG
      ;;
    k)
      PROXY_HOST=$OPTARG
      ;;
    x)
      START_OPTION=$OPTARG
      ;;
    r)
      USE_RELAY=true
      ;;
    d)
      DISABLE_HTTP_FAILOVER=true
      ;;
    t)
      INITIAL_TEAM=$OPTARG
      ;;
    c)
      AGENT_HOME=$OPTARG
      ;;
    v)
      INSTALL_SERVICE=true
      ;;
    h)
      echo "Usage: install-agent-with-options -n <agent name> -s <UCD remote host URL> -p <agent communication port> -x <start option> -r -k <proxy host> -l <proxy port>"
      echo "  - Agent name: Name of the agent to create"
      echo "  - UCD remote host: Hostname of the remote UCD host the agent should communicate with (UCD server or UCD agent relay)"
      echo "  - Agent communication port: Port over which the agent should communicate with the UCD remote host (Default: 7918, or 7916 when using agent relay)"
      echo "  - Start option (Optional): Start the agent after installation with the specified option (start, run)."
      echo "  - Use Agent Relay (Optional): specify -r to configure agent for agent-relay"
      echo "  - Initial Team (Optional): Comma-separated list of teams to add the agent to when it connects to the server (Default: System Team)"
      echo "  - Proxy host (Optional): Hostname of the proxy server to use with the agent relay (Default: UCD Remote Host)"
      echo "  - Proxy port (Optional): Port of the proxy server to use with the agent relay (Default: 20080)"
      echo "  - Disable http failover (Optional): specify -d to disable HTTP failover handling for agents connecting through a relay"
      echo "  - UCD Install Location (Optional): specify -c <path> to instsall UCD agent to a desire location"
      echo "  - Install Service (Optional): specify -v to install UCD agent service"
      exit
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done

install_agent_service (){
  AGENT_NEW_INIT_DIR=$AGENT_INSTALL_DIR/bin/init
  cp $AGENT_NEW_INIT_DIR/agent $AGENT_NEW_INIT_DIR/agent.bak.$$
  sed 's|AGENT_USER=.*|AGENT_USER=root|' $AGENT_NEW_INIT_DIR/agent.bak.$$ > $AGENT_NEW_INIT_DIR/agent
  cp $AGENT_NEW_INIT_DIR/agent $AGENT_NEW_INIT_DIR/agent.bak.$$
  sed 's|AGENT_GROUP=.*|AGENT_GROUP=system|' $AGENT_NEW_INIT_DIR/agent.bak.$$ > $AGENT_NEW_INIT_DIR/agent
  cp $AGENT_NEW_INIT_DIR/agent $AGENT_NEW_INIT_DIR/agent.bak.$$
  sed "s|^AGENT_HOME=.*|AGENT_HOME=\"$AGENT_NEW_HOME\"|" $AGENT_NEW_INIT_DIR/agent.bak.$$ > $AGENT_NEW_INIT_DIR/agent
  cp $AGENT_NEW_INIT_DIR/agent /etc/rc.d/rc2.d/S98ibm-ucdagent
}

check_disk_space (){
  echo "### Check diskspace ###"
  if (( `df ${AGENT_INSTALL_DIR} | sed -n 2p | awk '{print $3}'` < 500000 )) ; then
      echo "### The selected location does not have enough disk space. ###"
      exit 1
  else
      echo "continue..."
  fi
}

check_UCD_Agent_StatUp_Process(){
  COUNTER=0
  until ps -eo args | grep com.urbancode.air.agent.AgentWorker | grep -v "grep" ; do
    echo "Wating..."
    if (( COUNTER == 15 )) ; then
      echo "Timeout"
      echo "*** Cannot Start UCD agent from the new location. ***"
      exit
    fi
    ((COUNTER=COUNTER+1))
    sleep 1
  done
  echo "### UCD Agent is running from `ps -eo args | grep com.urbancode.air.agent.AgentWorker | cut -d' ' -f10 | rev | cut -c 20- | rev` ###"
}

###

#Set UCD installed dir
if [ ! -z "$AGENT_HOME" ]; then
  AGENT_INSTALL_DIR=$AGENT_HOME
  echo "Info: Install UCD Agent to $AGENT_INSTALL_DIR"
fi

mkdir -p $AGENT_INSTALL_DIR
check_disk_space

# extract domain name from URL
echo "UCD Remote Host URL = $UCD_REMOTE_HOST"
UCD_REMOTE_HOST=`echo $UCD_REMOTE_HOST | awk -F/ '{print $3}' | cut -d ':' -f 1`
echo "Updated - UCD Remote Hostname = $UCD_REMOTE_HOST"

# Validate variable values
if [ -z "$UCD_AGENT_NAME" ]; then
  echo "ERROR: UCD_AGENT_NAME not defined; exiting"
  exit 1
else
  echo "UCD_AGENT_NAME = $UCD_AGENT_NAME"
fi
if [ -z "$UCD_REMOTE_HOST" ]; then
  echo "ERROR: UCD remote host not defined or malformed - please enter a URL for the UCD server, i.e. http://my.ucdserver.com; exiting"
  exit 1
else
  echo "Connecting to UrbanCode Deploy at '$UCD_REMOTE_HOST'"
fi

# Determine which agent name suffix to use, either the contents of
# this file (if it exists) or the IP address.
# If this file exists, it is because an init script created it.
UCD_AGENT_NAME_SUFFIX_FILE="/tmp/ucd_agent_name_suffix"
UCD_AGENT_NAME_SUFFIX=
if [ -f "$UCD_AGENT_NAME_SUFFIX_FILE" ]; then
  UCD_AGENT_NAME_SUFFIX=`cat $UCD_AGENT_NAME_SUFFIX_FILE`
  if [ -z "$UCD_AGENT_NAME_SUFFIX" ]; then
    echo "ERROR: Unable to read UCD Agent name suffix file, $UCD_AGENT_NAME_SUFFIX_FILE, or file is empty; exiting"
    echo "ls -l $UCD_AGENT_NAME_SUFFIX_FILE"
    ls -l $UCD_AGENT_NAME_SUFFIX_FILE
    exit 1
  else
    UCD_AGENT_NAME="$UCD_AGENT_NAME.$UCD_AGENT_NAME_SUFFIX"
  fi
fi
# If agent name suffix is empty, append IP address to agent name
if [ -z "$UCD_AGENT_NAME_SUFFIX" ]; then
  # Append IP address to agent name
  ETH_ID=$(ifconfig -a | awk 'NR==1{print $1}' | sed 's/://')
  ETH0_IP=$(ifconfig $ETH_ID | grep inet | awk '{print $2}')
  UCD_AGENT_NAME=$UCD_AGENT_NAME.$ETH0_IP
fi
echo "UCD_AGENT_NAME = $UCD_AGENT_NAME"

# Extract embedded java jre
echo "Extracting JRE to install dir...."

if [[ -e "lib/jre/$JRE_ARCHIVE" ]] ; then
  gunzip lib/jre/$JRE_ARCHIVE
fi

if [[ -e "lib/jre/$(echo ${JRE_ARCHIVE} | sed 's/.gz//')" ]] ; then
  mkdir -p $AGENT_INSTALL_DIR/$UCD_AGENT_NAME/opt/${JRE}
  tar xf lib/jre/$(echo ${JRE_ARCHIVE} | sed 's/.gz//') -C $AGENT_INSTALL_DIR/$UCD_AGENT_NAME/opt/${JRE}
else
  exit
fi

echo "Completed extract of JRE"
JAVA_HOME=$AGENT_INSTALL_DIR/$UCD_AGENT_NAME/opt/${JRE}/jre
export JAVA_HOME

# Call groovy script to write these to a install.properties file
if [ "$USE_RELAY" = true ] ; then
  echo 'Configuring agent for agent relay!'
  if [ -z "$PROXY_HOST" ]; then
    PROXY_HOST=$UCD_REMOTE_HOST
  fi
  if [ -z "$UCD_PORT" ]; then
    UCD_PORT=7916
  fi
  echo "Relay proxy host = $PROXY_HOST"
  echo "Relay proxy port = $PROXY_PORT"
  echo "UCD communication port = $UCD_PORT"
  "$GROOVY_HOME/bin/groovy" install/template-install-agent.groovy "$UCD_AGENT_NAME" $UCD_REMOTE_HOST $UCD_PORT "$JAVA_HOME" "$INITIAL_TEAM" "$AGENT_INSTALL_DIR" $PROXY_HOST $PROXY_PORT $DISABLE_HTTP_FAILOVER
else
  if [ -z "$UCD_PORT" ]; then
    UCD_PORT=7918
  fi
  echo "UCD communication port = $UCD_PORT"
  "$GROOVY_HOME/bin/groovy" install/template-install-agent.groovy "$UCD_AGENT_NAME" $UCD_REMOTE_HOST $UCD_PORT "$JAVA_HOME" "$INITIAL_TEAM" "$AGENT_INSTALL_DIR"
fi

# Set replacement JAVA HOME for agent execution
AGENT_INSTALL_DIR=$AGENT_INSTALL_DIR/$UCD_AGENT_NAME
AGENT_JAVA_HOME=$AGENT_INSTALL_DIR/opt/${JRE}/jre
echo "Agent JAVA_HOME = $AGENT_JAVA_HOME"

chmod +x "$ANT_HOME/bin/ant"

# Run the installation.
"$ANT_HOME/bin/ant" -f install.with.groovy.xml \
    "-nouserlib" \
    "-noclasspath" \
    "-Dinstall-agent=true" \
    "-Dinstall.properties.file=$UCD_Agent_Properties_File" \
    "-Dpackage.replacement.java.home=$AGENT_JAVA_HOME" \
    install-non-interactive

# Install UCD Agent Service
if [ "$INSTALL_SERVICE" = true ] ; then
  echo "Agent installed, with service"
  install_agent_service
else
  echo "Agent installed, without service"
fi

# Start the agent if specified
if [ -z "$START_OPTION" ]; then
  echo "Agent installed, not starting"
else
  echo "Starting agent with start option $START_OPTION"
  "$AGENT_INSTALL_DIR/bin/agent" $START_OPTION
fi

# Check UCD process
check_UCD_Agent_StatUp_Process
