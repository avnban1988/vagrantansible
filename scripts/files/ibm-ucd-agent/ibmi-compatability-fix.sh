#!/QOpenSys/usr/bin/sh
# Licensed Materials - Property of IBM Corp.
# IBM UrbanCode Build
# IBM UrbanCode Deploy
# IBM UrbanCode Release
# IBM AnthillPro
# (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
#
# U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
# GSA ADP Schedule Contract with IBM Corp.
# This PASE shell script substitutes instances of '#!/bin/sh' (QShell) to
#  '#!/QOpenSys/usr/bin/sh' (PASE shell).
# Run this within the uDeploy Agent's install directory, (e.g. /opt/uDeploy/agent)
# Author: Sean Babineau
filelist=$(find . -type f -exec grep -l '#!/bin/sh' {} \;)
count=0;
for file in $filelist ; do
   sed 's/#!\/bin\/sh/#!\/QOpenSys\/usr\/bin\/sh/g' $file > $file.qsh
   rm $file
   mv $file.qsh $file
   print Changed file: $file
   count=$((count+=1))
done
print Processed $count files
# Expected results from invocation:
# > ibmi-compatability-fix.sh
# Changed file: ./bin/udagent
# Changed file: ./bin/configure-agent
# Changed file: ./opt/groovy-1.8.8/bin/groovy
# Changed file: ./opt/groovy-1.8.8/bin/groovyConsole
# Changed file: ./opt/groovy-1.8.8/bin/grape
# Changed file: ./opt/groovy-1.8.8/bin/groovyc
# Changed file: ./opt/groovy-1.8.8/bin/groovysh
# Changed file: ./opt/groovy-1.8.8/bin/groovydoc
# Changed file: ./opt/groovy-1.8.8/bin/java2groovy
# Processed 9 files
# $
