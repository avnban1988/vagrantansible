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

# save current state
PREVIOUS_DIR=`pwd`
PREVIOUS_ANT_HOME=$ANT_HOME

# now change the dir to the root of install script
SHELL_NAME=$0
SHELL_PATH=`dirname ${SHELL_NAME}`
FIPS_ARG=$1

if [ "." = "$SHELL_PATH" ]
then
   SHELL_PATH=`pwd`
fi
cd "${SHELL_PATH}"

# set ANT_HOME
ANT_HOME=opt/apache-ant-1.8.4
export ANT_HOME

ANT_OPTS=
if [ "-fips" = "$FIPS_ARG" ]
then
   ANT_OPTS="-Dcom.ibm.jsse2.usefipsprovider=true"
fi
export ANT_OPTS

chmod +x "opt/apache-ant-1.8.4/bin/ant"

# run the install
opt/apache-ant-1.8.4/bin/ant -nouserlib -noclasspath -f install.with.groovy.xml install-agent

# restore previous state
cd "${PREVIOUS_DIR}"
ANT_HOME=${PREVIOUS_ANT_HOME}
export ANT_HOME
