#ps1_sysnative
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

Param(
    [parameter(Mandatory=$true)]
    [ValidateNotNull()]
    [alias("n")]
    $UCD_AGENT_NAME,
    [alias("p")]
    $UCD_PORT = "",
    [parameter(Mandatory=$true)]
    [ValidateNotNull()]
    [alias("s")]
    $UCD_REMOTE_HOST,
    [alias("l")]
    $PROXY_PORT = "20080",
    [alias("k")]
    $PROXY_HOST,
    [alias("x")]
    $START_OPTION,
    [switch]
    [alias("r")]
    $USE_RELAY,
    [switch]
    [alias("d")]
    $DISABLE_HTTP_FAILOVER,
    [alias("t")]
    $INITIAL_TEAM,
    [alias("c")]
    $AGENT_HOME,
    [switch]
    [alias("h")]
    $HELP)

function Expand-ZIPFile($file, $destination)
{
  $shell = new-object -com shell.application
  $zip = $shell.NameSpace($file)
  foreach($item in $zip.items())
  {
    $shell.Namespace($destination).copyhere($item)
  }
}

If ($HELP -eq $true) {
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
    Exit 0
}

$PSVERSION = $psversiontable.psversion.tostring()

If ($PSVERSION -eq "2.0"){
  $scriptPath = split-path -parent $MyInvocation.MyCommand.Definition
}Else {
  $scriptPath = $PSScriptRoot
}

pushd "$scriptPath"

$ANT_HOME="$scriptPath\opt\apache-ant-1.8.4"
$GROOVY_HOME="$scriptPath\opt\groovy-1.8.8"
$JRE_ARCHIVE="@JRE_ARCHIVE_NAME@"
$JRE="@JRE_PREFIX@"

echo "------------PSScriptRoot = $scriptPath------------"
echo "------------env:TEMP =  $env:TEMP------------"
echo "------------env:systemdrive = $env:systemdrive------------"

$PROXY_PORT=20080

If ( $INITIAL_TEAM.length -lt 1 ){
  $INITIAL_TEAM = "System Team"
}
$UCD_Agent_Properties_File="$scriptPath\agent.install.properties"

# extract domain name from URL
echo "UCD Remote Host URL = $UCD_REMOTE_HOST"
$UCD_REMOTE_HOST=([System.Uri]$UCD_REMOTE_HOST).Host -replace '^www\.'
echo "Updated - UCD Remote Hostname = $UCD_REMOTE_HOST"

# Validate variable values
If ($UCD_AGENT_NAME -eq "") {
  echo "ERROR: UCD_AGENT_NAME not defined; exiting"
  Exit 1
} Else {
  echo "UCD_AGENT_NAME = $UCD_AGENT_NAME"
  If ($UCD_REMOTE_HOST -eq "") {
    echo "ERROR: UCD remote host not defined or malformed - please enter a URL for the UCD server, i.e. http://my.ucdserver.com; exiting"
    Exit 2
  }
  echo "Connecting to UrbanCode Deploy at '$UCD_REMOTE_HOST'"
}

# Determine which agent name suffix to use, either the contents of
# this file (if it exists) or the IP address.
# If this file exists, it is because an init script created it.
$UCD_AGENT_NAME_SUFFIX = ""
$UCD_AGENT_NAME_SUFFIX_FILE = "$env:TEMP\ucd_agent_name_suffix"
If (Test-Path $UCD_AGENT_NAME_SUFFIX_FILE) {
  $UCD_AGENT_NAME_SUFFIX = Get-Content $UCD_AGENT_NAME_SUFFIX_FILE
  If ($UCD_AGENT_NAME_SUFFIX -eq "" -Or !$UCD_AGENT_NAME_SUFFIX) {
    echo "ERROR: Unable to read UCD Agent name suffix file, $UCD_AGENT_NAME_SUFFIX_FILE, or file is empty; exiting"
    echo "Get-ChildItem -File $UCD_AGENT_NAME_SUFFIX_FILE"
    Get-ChildItem -File $UCD_AGENT_NAME_SUFFIX_FILE
    Exit 1
  } Else {
    $UCD_AGENT_NAME = "$UCD_AGENT_NAME.$UCD_AGENT_NAME_SUFFIX"
  }
}

# If we don't have an agent name suffix, append IP address to agent name
If ($UCD_AGENT_NAME_SUFFIX -eq "" -Or !$UCD_AGENT_NAME_SUFFIX) {
  $ipconf = netsh interface ip show address | findstr "IP Address"
  $ipconf=$ipconf -replace "IP Address:",""
  $ipconf,$otherips=$ipconf -replace '\s',''

  #this logic handle the softlayer private IP address
  If ($otherips -ne "127.0.0.1"){
        $iptemp = netsh interface ip show address "PrivateNetwork-A" | findstr "IP Address"
        $ipconf=$iptemp -replace "IP Address:",""
        $ipconf=$ipconf -replace '\s',''
  }
  $ETH0_IP = $ipconf
  $UCD_AGENT_NAME="$UCD_AGENT_NAME.$ETH0_IP"
}
echo "Updated - UCD_AGENT_NAME = $UCD_AGENT_NAME"

#Create JAVA_HOME folder

If (!$AGENT_HOME){
  $AGENT_INSTALL_DIR="$env:systemdrive\opt\ibm-ucd"
}Else{
  $AGENT_INSTALL_DIR=$AGENT_HOME
}

# Create AGENT_INSTALL_DIR folder if it does not exist
If(!(Test-Path -Path $AGENT_INSTALL_DIR )){
    New-Item -ItemType directory -Path $AGENT_INSTALL_DIR
    echo "Created $AGENT_INSTALL_DIR"
}

# Check Disk Space
$f = get-item $AGENT_INSTALL_DIR; $drive_name = $f.PSDrive.Name + ':' ;
$free_space = gwmi win32_logicaldisk -filter "DeviceID='$drive_name'" | % { $_.freespace/1GB } ;

If ($free_space -lt 0.5){
  echo "The selected location does not have enough disk space."
  Exit 3
}Else{
  $JAVA_PATH="$AGENT_INSTALL_DIR\$UCD_AGENT_NAME\opt\$JRE"
  # Create JAVA HOME folder if it does not exist
  If(!(Test-Path -Path $JAVA_PATH )){
    New-Item -ItemType directory -Path $JAVA_PATH
    echo "Created $JAVA_PATH"
  }
}

# Extract embedded java jre
echo "Extracting JRE to install dir...."
Expand-ZipFile -File "$scriptPath\lib\jre\$JRE_ARCHIVE" -Destination "$JAVA_PATH"

echo "Completed extract of JRE"

# Set JAVA_HOME Environment variable needed for Groovy execution
$JAVA_HOME="$JAVA_PATH\jre"
echo "JAVA_HOME = $JAVA_HOME"
$env:JAVA_HOME = $JAVA_HOME

# Add an extra '\'' after \\
$UCD_AGENT_HOME=$AGENT_INSTALL_DIR -replace '\\','\\'
$JAVA_HOME=$JAVA_HOME -replace '\\','\\'

# Call groovy script to write these to a install.properties file
If ($USE_RELAY -eq $true) {
  echo "Configuring agent for agent relay!"
  If ($PROXY_HOST -ne "" ) {
    $PROXY_HOST=$UCD_REMOTE_HOST
  }

  If ($UCD_PORT.length -eq 0) {
    $UCD_PORT=7916
  }

  echo "Relay proxy host = $PROXY_HOST"
  echo "Relay proxy port = $PROXY_PORT"
  echo "UCD communication   port = $UCD_PORT"

  $args = @()
  $args += ("`"$scriptPath\install\template-install-agent.groovy`"")
  $args += ("`"$UCD_AGENT_NAME`"")
  $args += ("$UCD_REMOTE_HOST")
  $args += ("$UCD_PORT")
  $args += ("`"$JAVA_HOME`"")
  $args += ("`"$INITIAL_TEAM`"")
  $args += ("`"$UCD_AGENT_HOME`"")
  $args += ("$PROXY_HOST")
  $args += ("$PROXY_PORT")
  $args += ("$DISABLE_HTTP_FAILOVER")

  $cmd = "`"$GROOVY_HOME\bin\groovy.bat`""

  Invoke-Expression "& $cmd $args"
} Else {
  If ($UCD_PORT.length -eq 0) {
    $UCD_PORT=7918
  }

  echo "UCD communication port = $UCD_PORT"

  $args = @()
  $args += ("`"$scriptPath\install\template-install-agent.groovy`"")
  $args += ("`"$UCD_AGENT_NAME`"")
  $args += ("$UCD_REMOTE_HOST")
  $args += ("$UCD_PORT")
  $args += ("`"$JAVA_HOME`"")
  $args += ("`"$INITIAL_TEAM`"")
  $args += ("`"$UCD_AGENT_HOME`"")

  $cmd = "`"$GROOVY_HOME\bin\groovy.bat`""

  Invoke-Expression "& $cmd $args"
}

# Set ANT_HOME Environment variable needed for ANT execution
$env:ANT_HOME = $ANT_HOME

cmd /C $scriptPath\opt\apache-ant-1.8.4\bin\ant.bat -f "$scriptPath\install.with.groovy.xml" `
     "-nouserlib" `
     "-noclasspath" `
     "-Dinstall-agent=true" `
     "-Dinstall.properties.file=`"$UCD_Agent_Properties_File`"" `
     install-non-interactive

# Start the agent if specified
If ($START_OPTION.length -lt 1) {
  echo "Agent installed, not starting"
}
ElseIf ($START_OPTION -eq "install-service") {
  gsv -display "IBM UrbanCode Deploy Agent (udagent)"

}Else {
  echo "Starting agent with start option $START_OPTION"

  $args = @()
  $args += ("$START_OPTION")
  $cmd = "`"$AGENT_INSTALL_DIR\$UCD_AGENT_NAME\bin\agent.cmd`""
  Invoke-Expression "& $cmd $args"
}
popd
Exit 0
