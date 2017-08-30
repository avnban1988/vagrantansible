/*
* Licensed Materials - Property of IBM Corp.
* IBM UrbanCode Build
* IBM UrbanCode Deploy
* IBM UrbanCode Release
* IBM AnthillPro
* (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*/


/*
This script requires the Groovy scripting language.  You can find Groovy at
http://groovy.codehaus.org/.  Download Groovy at http://dist.codehaus.org/groovy/distributions/.
To install it, follow the instructions at http://groovy.codehaus.org/install.html.

Automatic import packages & classes:
  java.io/lang/net/util
  java.math.BigDecimal/BigInteger
  groovy.lang/util
*/
import groovy.json.JsonOutput;

import java.io.UnsupportedEncodingException;
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang.StringEscapeUtils
import org.apache.tools.ant.*
import org.apache.tools.ant.taskdefs.condition.Os

import com.urbancode.air.keytool.Extension
import com.urbancode.air.keytool.KeytoolHelper
import com.urbancode.commons.util.IO
import com.urbancode.commons.util.unix.Unix
import com.urbancode.commons.util.agent.AgentVersionHelper
import com.urbancode.commons.util.crypto.CryptStringUtil
import com.urbancode.commons.util.crypto.SecureRandomHelper
import com.urbancode.commons.validation.ValidationException
import com.urbancode.commons.validation.ValidationRules
import com.urbancode.commons.validation.format.JreHomeValidationRule
import com.urbancode.commons.validation.format.NoSpaceValidationRule
import com.urbancode.commons.validation.format.RequiredValueValidationRule
import com.urbancode.commons.validation.format.SocketPortValidationRule
import com.urbancode.commons.validation.format.YesNoValidationRule
import com.urbancode.commons.validation.rules.NumericValueRule
import com.urbancode.shell.Os

import org.apache.commons.lang.StringUtils
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.*
import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import groovy.json.JsonSlurper

class AgentInstaller {

    boolean isUnix = Os.isFamily("unix")
    boolean isWindows = Os.isFamily("windows")
    boolean isZos = Os.isFamily("z/os")

    def agentEncoding = "UTF-8"
    def systemEncoding = getSystemEncoding()
    def systemIn = System.in.newReader(systemEncoding)


    def NL = System.getProperty('line.separator')

    def nonInteractive = false
    def autoUpgradeAgent = false
    def inputAnotherHost = true
    def installzOSDeploymentTools = false

    def ant = null

    def productName = null
    def productPrefix = null
    def productCapitalName = null

    def srcDir = null
    def unpackDir = null
    def javaHome = null
    def currentJavaHome = null
    def javaSystemProperties = null

    def installPropertiesFile = null

    def installOs = null
    def installArch = null

    def installAsService = null
    def installServiceName = null
    def installServiceLogin = null
    def installServicePassword = null
    def installServiceAutostart = null
    def installServiceStatus = null
    def doServiceInstall = null
    def serviceNameValidated = false

    def installAgentDir = null
    def installAgentId = null
    def installAgentName = null
    def generateAgentName = false
    def installAgentTeams = null

    def installAgentRemoteHost = null
    def installAgentRemotePort = null
    def installAgentBrokerUrl = null
    def installAgentProxyHost = null
    def installAgentProxyPort = null
    def installDisableHttpFailover = null

    def installAgentRemoteHostList = []
    def installAgentRemotePortList = []
    def installAgentProxyHostList = []
    def installAgentProxyPortList = []
    def installBrokerURL = null

    def installAgentMutualAuth = null
    def disableFullEncryption = null
    def installAgentKeystore = null
    def installAgentKeystorePwd = null

    def installAgentVerifyServerIdentity = null
    def installAgentServerUrl = null

    def installInitUser = null
    def installInitGroup = null

    def portValidator = null
    def yesNoValidator = null
    def jreHomeValidator = null
    def optionalValidator = null
    def requiredValidator = null
    def numberValidator = null
    def serviceNameValidator = null

    def installAgentDirTokenReplacement = null
    def javaHomeTokenReplacement = null

    def hostName = null

    def val = null // for groovy bug work-around

    def classpath = null

    def encryptionKeyStorePath = null
    def keyStorePassword = null
    def encryptionKeystoreAlias = null

    AgentInstaller(classpath) {
        def requiredValidationRule = new RequiredValueValidationRule()

        optionalValidator = new ValidationRules()

        requiredValidator = new ValidationRules()
        requiredValidator.addRule(requiredValidationRule)

        portValidator = new ValidationRules()
        portValidator.addRule(requiredValidationRule)
        portValidator.addRule(new SocketPortValidationRule())

        yesNoValidator = new ValidationRules()
        yesNoValidator.addRule(requiredValidationRule)
        yesNoValidator.addRule(new YesNoValidationRule())

        jreHomeValidator = new ValidationRules()
        jreHomeValidator.addRule(requiredValidationRule)
        jreHomeValidator.addRule(new JreHomeValidationRule());

        def numericValidationRule = new NumericValueRule()
        numericValidationRule.setLowerBound(1)
        numericValidationRule.setUpperBound(Integer.MAX_VALUE)
        numberValidator = new ValidationRules()
        numberValidator.addRule(requiredValidationRule)
        numberValidator.addRule(numericValidationRule)

        serviceNameValidator = new ValidationRules();
        serviceNameValidator.addRule(requiredValidationRule);
        serviceNameValidator.addRule(new NoSpaceValidationRule());

        this.classpath = classpath
    }

    void setAntBuilder(antBuilder) {
        ant = new AntBuilder(antBuilder.project)
        // have to do this, otherwise properties don't work right
        antBuilder.project.copyInheritedProperties(ant.project)
        antBuilder.project.copyUserProperties(ant.project)

        installAgentDir = ant.project.properties.'install.agent.dir'
        if (installAgentDir != null) {
            installAgentDir = new File(installAgentDir).getAbsolutePath()
        }

        initNonInteractiveProperties()
    }

    void initNonInteractiveProperties() {
        srcDir = ant.project.properties.'src.dir'

        if (ant.project.properties.'locked/agent.home') {
            installAgentDir = ant.project.properties.'locked/agent.home'
        }

        // properties used when constructing an install package (msi, etc)
        if (ant.project.properties.'package.replacement.agent.dir') {
            installAgentDirTokenReplacement = ant.project.properties.'package.replacement.agent.dir'
        }
        if (ant.project.properties.'package.replacement.java.home') {
            javaHomeTokenReplacement = ant.project.properties.'package.replacement.java.home'
        }
        if (ant.project.properties.'package.java.home') {
            javaHome = ant.project.properties.'package.java.home'
        }
        if (ant.project.properties.'package.os') {
            installOs = ant.project.properties.'package.os'
        }
        if (ant.project.properties.'package.arch') {
            installArch = ant.project.properties.'package.arch'
        }
        if (ant.project.properties.'package.generate.agent.name') {
            generateAgentName = Boolean.valueOf(ant.project.properties.'package.generate.agent.name');
        }
        if (ant.project.properties.'zos.deployment.tools') {
            installzOSDeploymentTools = Boolean.valueOf(ant.project.properties.'zos.deployment.tools');
        }
        if (ant.project.properties.'package.init.user') {
            installInitUser = ant.project.properties.'package.init.user'
        }
        if (ant.project.properties.'package.init.group') {
            installInitGroup = ant.project.properties.'package.init.group'
        }

        if (javaHome == null) {
            javaHome = System.getenv().'JAVA_HOME'
        }
        if (javaHome == null) {
            javaHome = System.getProperty('java.home')
        }

        // Protection against invalid java paths set in the install props or JAVA_HOME
        jreHomeValidator.validate(javaHome)

        if (ant.project.properties.'install.properties.file') {
            installPropertiesFile = ant.project.properties.'install.properties.file'
        }

        initInstalledProperties();
    }

    void initInstalledProperties() {

        // always written by installer:
        // locked/agent.brokerUrl
        // locked/agent.home
        // locked/ant.home

        // properties from the existing agent's installed.properties
        if (ant.project.properties.'locked/agent.id') {
            installAgentId = ant.project.properties.'locked/agent.id'
        }
        if (ant.project.properties.'locked/agent.name') {
            installAgentName = ant.project.properties.'locked/agent.name'
        }
        if (ant.project.properties.'locked/agent.initial.teams') {
            installAgentTeams = ant.project.properties.'locked/agent.initial.teams'
        }
        if (ant.project.properties.'locked/agent.jms.remote.host') {
            installAgentRemoteHostList.addAll(ant.project.properties.'locked/agent.jms.remote.host'.split(','))
        }
        if (ant.project.properties.'locked/agent.jms.remote.port') {
            installAgentRemotePortList.addAll(ant.project.properties.'locked/agent.jms.remote.port'.split(','))
        }
        // validate the input for agent.jms.remote.* properties
        if (installAgentRemoteHostList.size() != installAgentRemotePortList.size()) {
            throw new IllegalArgumentException(
                "You only specified " +
                installAgentRemotePortList.size() +
                " ports. Expected " +
                installAgentRemoteHostList.size() + ".")
        }
        for (int i = 0; i < installAgentRemotePortList.size(); i++) {
            portValidator.validate(installAgentRemotePortList.get(i))
        }
        if (ant.project.properties.'locked/agent.http.proxy.host') {
            installAgentProxyHost = ant.project.properties.'locked/agent.http.proxy.host'
        }
        if (ant.project.properties.'locked/agent.http.proxy.port') {
            installAgentProxyPort = ant.project.properties.'locked/agent.http.proxy.port'
        }
        if (ant.project.properties.'locked/agent.mutual_auth') {
            installAgentMutualAuth = ant.project.properties.'locked/agent.mutual_auth'
        }

        if (ant.project.properties.'agent.jms.disable_full_encryption') {
            disableFullEncryption = ant.project.properties.'agent.jms.disable_full_encryption'
        }

        if (ant.project.properties.'locked/agent.keystore') {
            installAgentKeystore = ant.project.properties.'locked/agent.keystore'
        }
        if (ant.project.properties.'locked/agent.keystore.pwd') {
            installAgentKeystorePwd = CryptStringUtil.decrypt(ant.project.properties.'locked/agent.keystore.pwd')
        }
        if (ant.project.properties.'locked/agent.service') {
            installAsService = ant.project.properties.'locked/agent.service'
        }
        if (ant.project.properties.'locked/agent.service.name') {
            installServiceName = ant.project.properties.'locked/agent.service.name'
        }
        if (ant.project.properties.'locked/agent.service.login') {
            installServiceLogin = ant.project.properties.'locked/agent.service.login'
            installServiceLogin = installServiceLogin.replaceAll("(\\\\\\\\)+", "\\\\");
            installServiceLogin = installServiceLogin.replace("\\\\", "\\")
        }
        if (ant.project.properties.'locked/agent.service.password') {
            installServicePassword = CryptStringUtil.decrypt(ant.project.properties.'locked/agent.service.password')
        }
        if (ant.project.properties.'locked/agent.service.autostart') {
            installServiceAutostart = ant.project.properties.'locked/agent.service.autostart'
        }
        if (ant.project.properties[productName + "/java.home"]) {
            javaHome = ant.project.properties[productName + "/java.home"]
            currentJavaHome = javaHome
        }
        if (ant.project.properties['agent.HttpFailoverHandler.disabled']) {
            installDisableHttpFailover = ant.project.properties.'agent.HttpFailoverHandler.disabled'
        }
        if (ant.project.properties.'locked/agent.brokerUrl'){
            installBrokerURL = ant.project.properties.'locked/agent.brokerUrl'
        }
        if (ant.project.properties.'verify.server.identity'){
            installAgentVerifyServerIdentity = ant.project.properties.'verify.server.identity'
        }
        if (ant.project.properties.'server.url'){
            installAgentServerUrl = ant.project.properties.'server.url'
        }
        if (ant.project.properties.'encryption.keystore') {
            encryptionKeyStorePath = ant.project.properties.'encryption.keystore'
        }
        if (ant.project.properties.'encryption.keystore.password') {
            keyStorePassword = CryptStringUtil.class.decrypt(ant.project.properties.'encryption.keystore.password')
        }
        if (ant.project.properties.'encryption.keystore.alias') {
            encryptionKeystoreAlias = ant.project.properties.'encryption.keystore.alias'
        }
    }

    void setNonInteractive(mode) {
        this.nonInteractive = mode
    }

    void setAutoUpgradeAgent(mode) {
        this.autoUpgradeAgent = mode
    }

    void unpack(src, dst) {
        ant.unzip(src: src, dest: dst)
    }

    void init() {
        if (ant == null) {
            ant = new AntBuilder()
        }

        def overlayZip = srcDir +  File.separator + "overlay.zip"
        def newTmpDir = srcDir.substring(0, srcDir.lastIndexOf(File.separator))
        System.getProperties().setProperty("java.io.tmpdir", newTmpDir)
        unpackDir = File.createTempFile("agent-install-", ".tmp")
        unpackDir.delete()
        unpackDir.mkdirs()
        unpackDir = unpackDir.getCanonicalPath()
        unpack(overlayZip, unpackDir)
    }

    void install(productName, productPrefix, productCapitalName) {
        this.productName = productName
        this.productPrefix = productPrefix
        this.productCapitalName = productCapitalName
        init()

        try {
            this.installAgent()
            prompt('Installer Complete. (press return to exit installer)') // wait for user input
        }
        catch (Exception e) {
            e.printStackTrace()
            prompt('Install Failed. (press return to exit installer)') // wait for user input
            throw e;
        }
        finally {
            ant.delete(dir: unpackDir)
        }
    }

    void installzOSToolkit(doUpgrade) {
        if (isZos) {
            println "";
            def installToolkit = false;
            if (doUpgrade) {
                //do not ask when upgrading existing agent and toolkit. 
                if (new File(installAgentDir + "/conf/toolkit/installed.version").exists()) {
                    installToolkit = true
                }
                else {
                    installToolkit = false
                }
            }
            else {
                def defaultInstallToolkit = null;
                if(nonInteractive) defaultInstallToolkit = installzOSDeploymentTools? "y" : "N";
                installToolkit = parseBoolean( prompt(defaultInstallToolkit,
                        'Install the zOS deployment tools? zOS deployment tools is required ' +
                        'for importing and deploying z/OS component versions. y,n',
                        null,
                        yesNoValidator))
            }
            if (installToolkit) {
                this.class.classLoader.addClasspath("${installAgentDir}/ucdtoolkit/bin/groovy");
                def installer  = this.class.classLoader.loadClass("ToolkitInstaller").newInstance();
                ant.property(name:"agent.dir",value:installAgentDir);
                ant.property(name:"toolkit.src.dir",value:"${installAgentDir}/ucdtoolkit/bin");
                ant.property(name:"installer.doUpgrade",value:"${doUpgrade}");

                installer.setAntBuilder(ant)
                installer.setNonInteractive(nonInteractive)
                installer.install('IBM UrbanCode Deploy', 'ibm-ucd', 'IBM-UCD')
            }
        }
    }

    void installAgent() {
        def componentInstall = "agent"

        if (nonInteractive) {
            println("\nInstalling " + productName + " Agent (non-interactive)\n")
        } else {
            println("\nInstalling " + productName + " Agent\n")
        }

        installAgentDir = getInstallDir(componentInstall, installAgentDir)

        def doUpgrade = checkForUpgrade(installAgentDir, componentInstall, installServiceName)

        if (doUpgrade) {
            File installedVersionFile = new File(installAgentDir + "/conf/installed.version")
            Properties props = new Properties();
            installedVersionFile.withInputStream{
                props.load(it)
            }
            def installedVersion = props['installed.version']

            // We should not delete plugins if the agent version is 6.1.0.4 or newer.
            if (!AgentVersionHelper.versionIsAtLeast(installedVersion, 6, 1, 0, 4)) {
                File pluginDir = new File(installAgentDir + "/var/plugins")
                println("\nDeleting existing plugins directory: " + pluginDir + "\n")
                if (pluginDir.exists()) {
                    pluginDir.deleteDir()
                    pluginDir.mkdirs()
                }
            }
        }

        println("\nInstalling Agent to: " + installAgentDir + "\n")

        // read existing properties
        // file location was changed in newer versions of the agent
        File oldPropsFile = new File(installAgentDir, "conf/agent/agent.properties");
        File propsFile = new File(installAgentDir, "conf/agent/installed.properties");
        if (oldPropsFile.exists()) {
            ant.move(tofile: propsFile.absolutePath,
                     file: oldPropsFile.absolutePath);
        }

        if (propsFile.exists()) {
            ant.property(file: propsFile.absolutePath)
            initInstalledProperties()
        } 
        else if (installPropertiesFile) {
            // if we were given a properties file on input, copy it to the agent's installed.properties
            // to preserve extra properties

            def props = new Properties()
            new File(installPropertiesFile).withInputStream {
                props.load(it)
            }

            // Filter properties used to configure creation of install packages
            def propsCopy = new Properties()
            props.each {
                if (!it.key.startsWith("package.") && !it.key.contains("agent.service.password")) {
                    propsCopy[it.key] = it.value
                }
            }

            propsFile.parentFile.mkdirs()
            propsFile.withOutputStream {
                propsCopy.store(it, null)
            }
        }

        // make sure we have the JAVA_HOME
        final def jreDir = new File(srcDir + File.separator + 'java')
        if (jreDir.exists()) {
            javaHome = installAgentDir + File.separator + 'java'
        } else {
            javaHome = prompt(
                    currentJavaHome,
                    "Please enter the home directory of the JRE/JDK used to run this agent. " +
                            "[Default: " + javaHome + "]",
                    javaHome,
                    jreHomeValidator)
        }

        javaHome = new File(javaHome).absolutePath
        println("JAVA_HOME: " + javaHome + "\n")

        def installWithRelayFromProperty = installAgentProxyPort ? "Y" : "N"
        def installWithRelayDefault = nonInteractive ? installWithRelayFromProperty : null
        def installWithRelay = installAgentProxyHost ? "Y" : installWithRelayDefault
        installWithRelay = parseBoolean(prompt(
                installWithRelay,
                "Will the agent connect to an agent relay instead of directly to the server? y,N [Default: N]",
                "N",
                yesNoValidator))

        if (nonInteractive) {
            inputAnotherHost = null
            // add default values if needed
            if (installAgentRemoteHostList.size() == 0) {
                installAgentRemoteHostList.add("localhost");
            }
            if (installAgentRemotePortList.size() == 0) {
                installAgentRemotePortList.add("7918");
            }
        }
        while (inputAnotherHost) {
            if (installWithRelay) {
                installAgentRemoteHostList.add(prompt(
                        installAgentRemoteHost,
                        "Enter the hostname or address of the agent relay the agent will connect to."))
                installAgentRemotePortList.add(prompt(
                        installAgentRemotePort,
                        "Enter the agent communication port for the agent relay. [Default: 7916]",
                        "7916",
                        portValidator))
                inputAnotherHost = parseBoolean(prompt(
                        null,
                        "Do you want to configure another failover relay connection? y,N [Default: N]",
                        "N",
                        yesNoValidator))
            } else {
                installAgentRemoteHostList.add(prompt(
                        installAgentRemoteHost,
                        "Enter the hostname or address of the server the agent will connect to. [Default: localhost]",
                        "localhost"))
                installAgentRemotePortList.add(prompt(
                        installAgentRemotePort,
                        "Enter the agent communication port for the server. [Default: 7918]",
                        "7918",
                        portValidator))
                inputAnotherHost = parseBoolean(prompt(
                        null,
                        "Do you want to configure another failover server connection? y,N [Default: N]",
                        "N",
                        yesNoValidator))
            }
        }

        if (installAgentRemoteHostList.isEmpty()) {
            installAgentRemoteHostList.add(installAgentRemoteHost)
        }
        if (installAgentRemotePortList.isEmpty()) {
            installAgentRemotePortList.add(installAgentRemotePort)
        }

        if (installWithRelay) {
            installAgentProxyHost = prompt(
                    installAgentProxyHost,
                    "Enter the hostname or address of the HTTP proxy server for the agent relay. " +
                    "[Default: " + installAgentRemoteHostList.get(0) + "]",
                    installAgentRemoteHostList.get(0))
            installAgentProxyPort = prompt(
                    installAgentProxyPort,
                    "Enter the HTTP proxy port for the agent relay. [Default: 20080]",
                    "20080",
                    portValidator)
            installDisableHttpFailover = parseBoolean(prompt(
                    installDisableHttpFailover,
                    "Do you want to disable HTTP Failover Handling? This is necessary if the relay is behind a firewall and accessed through a load balancer. N,y [Default: N]",
                    "N",
                    yesNoValidator))
        }

        installAgentMutualAuth = parseBoolean(prompt(
            installAgentMutualAuth,
            "Enable mutual (two-way) authentication with SSL for server/agent JMS communication? This setting must match that of the server. y,N [Default: N]",
            "n"))

        // Prompt for whether to enable end-to-end encryption (for JMS),
        // the default is "N" if upgrade, "Y" if new installation.
        String promptAgentFullEncryption =
            "End-to-end encryption enhances the security of UrbanCode messages sent between\n" +
            "the agent and the server, and requires an initial HTTPS connection to set up\n" +
            "keys. (WARNING: If your server has been configured to require end-to-end\n" +
            "encryption exclusively, you must not disable this agent feature and must supply\n" +
            "the full web URL below or your agent will not come online.)\n" +
            "\n" +
            "Disable end-to-end encryption for server/agent JMS communication?"
        String defaultAgentFullEncryption = "n"

        promptAgentFullEncryption = promptAgentFullEncryption + " y,N [Default: N]"
        disableFullEncryption = parseBoolean(prompt(
            disableFullEncryption,
            promptAgentFullEncryption,  // user prompt
            defaultAgentFullEncryption))

        installAgentVerifyServerIdentity = parseBoolean(prompt(
            installAgentVerifyServerIdentity,
            "Enable the agent to verify the server HTTPS certificate?" +
                " If enabled, you must import the server certificate" +
                " to the JRE keystore on the agent. y,N [Default: N]",
            "n"))

        // If full encryption is enabled, process the required value for 'server.url'.
        if (disableFullEncryption == null || !disableFullEncryption) {

            String promptAgentServerUrl =
                "Enter the full web URL for the central IBM UrbanCode Deploy server to validate\n" +
                "the connection. (WARNING: If your server has been configured to require\n" +
                "end-to-end encryption exclusively, you must supply the URL here or your agent\n" +
                "will not come online.)\n" +
                "Leave empty to skip."

            if (doUpgrade) {
                // "Prompt" for server.url value if current value is null. No validation.
                installAgentServerUrl = prompt(installAgentServerUrl, promptAgentServerUrl)
            }
            // Else if silent-mode and value for server.url is empty, print warning--prompt() will
            // do that for empty values--and continue with silent installation
            else if (nonInteractive && !StringUtils.isNotBlank(installAgentServerUrl)) {
                installAgentServerUrl = prompt(installAgentServerUrl, promptAgentServerUrl)
            }
            // Else, new installation, and either interactive mode or silent w/server.url value not empty
            else {
                boolean serverUrlIsValid = false

                // Loop while we prompt the user for the central server and Test the connection.
                while (!serverUrlIsValid) {
                    installAgentServerUrl = prompt(installAgentServerUrl, promptAgentServerUrl, null)

                    // Loop until they do not enter the url or we can validate it
                    if (installAgentServerUrl == null || installAgentServerUrl.isAllWhitespace()) {
                        serverUrlIsValid = true;
                    }
                    else {
                        try {
                            // Test server with http request
                            // Display message to wait while we send a test request to the central server.
                            println("Sending a request to the central IBM UrbanCode Deploy server...")
                            probeGenerateKeyAPI(installAgentServerUrl)
                            serverUrlIsValid = true
                            println("Server URL is valid.")
                        }
                        catch (Exception e) {
                            println("Error: Server access attempt failed for URL [${installAgentServerUrl}]: ${e.message}.\n")
                        }
    
                        if (!serverUrlIsValid) {
                            if (nonInteractive) {   // For Non-Interactive mode, fail-out
                                throw new IllegalArgumentException(
                                    "Non-Interactive Mode: problem with full web URL for central IBM UrbanCode Deploy server: '${installAgentServerUrl}' .")
                            }
                            else {  // interactive mode: server url access failed, clear value and ask user again
                                installAgentServerUrl = null
                            }
                        }
                    }
                }
            }
        }

        try {
            hostName = InetAddress.localHost.canonicalHostName
        }
        catch (UnknownHostException e) {
            hostName = "localhost"
        }

        if (!generateAgentName) {
            installAgentName = prompt(
                installAgentName,
                "Enter the name for this agent. [Default: "+hostName+"]",
                hostName)
        }

        if (!doUpgrade) {
            ant.echo(message: "The agent can be added to one or more teams when it first connects to the server. " +
                "Changing this setting after initial connection to the server will not have any effect. " +
                "You can also add a specific type associated with a team by using the format <team>:<type>")
            installAgentTeams = prompt(
                installAgentTeams,
                "Enter teams (and types) to add this agent to, separated by commas. [Default: None]",
                "")
        }

        // copy files
        copyAgentFiles(installAgentDir, doUpgrade)

        //create key if it does not exist
        createSecretKey();

        // write the installed version
        ant.echo(file: installAgentDir + "/conf/installed.version", encoding: "UTF-8",
                 message: "installed.version=" + ant.project.properties.'version' + NL)
        ant.echo(message: "Installed version " + ant.project.properties.'version' + NL)

        //install zos deployment tools
        if (isZos){
            println('Agent Install Completed.');
            this.installzOSToolkit(doUpgrade);
        }

        //install service
        if (Os.isFamily("windows")) {
            doServiceInstall = false
            if (installServiceName != null) {
                doServiceInstall = true
            }
            else {
                installAsService = parseBoolean(prompt(
                        installAsService,
                        "Do you want to install the Agent as Windows service? y,N [Default: N]",
                        "N",
                        yesNoValidator))
                doServiceInstall = installAsService
            }

            def defaultName = productName.toLowerCase().replace(" ", "-")+"-agent"
            def strLocalsystem = /.\localsystem/
            def strPath = /'.\'/
            if (doServiceInstall == true) {
                if (installServiceName == null) {
                    installServiceName = prompt(
                        installServiceName,
                        "Enter a unique service name for the Agent. No spaces please. [Default: " + defaultName+"]",
                        defaultName,
                        serviceNameValidator)
                }

                if (installServiceLogin == null) {
                    installServiceLogin = prompt(
                        installServiceLogin,
                        "Enter the user account name including domain path to run the service as (for local use " +
                        strPath + " before login), The local system account will be used by default. [Default: " +
                        strLocalsystem + "]",
                        strLocalsystem,
                        requiredValidator)
                }

                if (installServiceLogin != strLocalsystem) {
                    installServicePassword = prompt(
                            installServicePassword,
                            "Please enter your password for desired account.",
                            "nopass",
                            requiredValidator)
                }
                else {
                    installServicePassword = "nopass"
                }

                installServiceAutostart = prompt(
                        installServiceAutostart,
                        "Do you want to start the '" + installServiceName + "' service automatically? y,N " +
                        "[Default: N]",
                        "N",
                        yesNoValidator)

                ant.exec(dir: installAgentDir + "/bin/service", executable:"cscript.exe") {
                    arg(value:"//I")
                    arg(value:"//Nologo")
                    arg(value:installAgentDir + "/bin/service/agent_srvc_install.vbs")
                    arg(value:installServiceName)
                    arg(value:installServiceLogin)
                    arg(value:installServicePassword)
                    arg(value:installServiceAutostart)
                }

                // read service installation status properties
                if (new File(installAgentDir + "/bin/service/srvc.properties").exists()) {
                    ant.property(file: installAgentDir + "/bin/service/srvc.properties")
                    installServiceStatus = ant.project.properties.'install.service.status'
                    if (installServiceStatus == "OK") {
                        ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                            entry(key: "install.service.name", value: installServiceName)
                        }
                    }
                    ant.delete(file: installAgentDir + "/bin/service/srvc.properties")

                }
            }
            else {
                println("\nYou can install service manually (see documentation).\n\n")
            }
        }
    }

    private void copyAgentFiles(installAgentDir, doUpgrade) {

        ant.delete(dir: installAgentDir + "/lib", failonerror: 'false')

        // clean out old unused configuration files
        ant.delete(dir: installAgentDir + "/conf") {
          include(name: "client/**/*.xml")
          include(name: "spring-client/**/*")
        }

        // create directory structure
        // ant mkdir threw a fit with cyrillic characters
        new File(installAgentDir + "/bin").mkdirs()
        new File(installAgentDir + "/conf/agent").mkdirs()
        new File(installAgentDir + "/lib").mkdirs()
        new File(installAgentDir + "/monitor").mkdirs()
        new File(installAgentDir + "/native").mkdirs()
        new File(installAgentDir + "/opt").mkdirs()
        new File(installAgentDir + "/var/jobs").mkdirs()
        new File(installAgentDir + "/var/log").mkdirs()
        new File(installAgentDir + "/var/temp").mkdirs()

        final def jreDir = new File(srcDir + '/java')
        if (jreDir.exists()) {
            ant.delete(dir: installAgentDir + "/java", quiet: 'true', failonerror: 'false')
            ant.copy(todir: installAgentDir + "/java") {
                fileset(dir: srcDir + "/java") {
                    include(name: "**/*")
                }
            }
            ant.chmod(perm: "+x", type: "file", dir: installAgentDir + "/java/bin", includes: "**")
        }

        // these need to run after the embedded Java is copied so it can be used.
        installOs = getOs(javaHome, classpath)
        installArch = getArch(javaHome, classpath)

        // copy conf files
        if (!doUpgrade) {
            ant.copy(todir: installAgentDir + "/conf", overwrite: 'true') {
                fileset(dir: unpackDir + "/conf") {
                    include(name: "agent/java.security")
                    include(name: "agent/log4j.properties")
                }
            }
        }
        else {
            ant.copy(todir: installAgentDir + "/conf", overwrite: 'true') {
                fileset(dir: unpackDir + "/conf") {
                    include(name: "agent/java.security")
                }
            }
        }

        // Copy all other overlay files
        ant.copy(todir: installAgentDir + "/properties", overwrite: 'true') {
            fileset(dir: unpackDir + "/properties") {
                exclude(name: "conf/**/*")
            }
        }


        if (!installAgentKeystore) {
            installAgentKeystore = "../conf/agent.keystore"
        }
        if (!installAgentKeystorePwd) {
            installAgentKeystorePwd = "changeit"
        }

        //check whether or not the file path is absolute
        File keyStoreFile = new File(installAgentKeystore);
        if (!keyStoreFile.isAbsolute()) {
            //if the file path is relative it must be relative from the agent's bin directory
            keyStoreFile = new File(installAgentDir+"/bin", installAgentKeystore);
        }

        //if the keystore file does not exist we need to make a new one.
        if (!keyStoreFile.exists()) {
            Date from = new Date();
            Date to = new Date(from.getTime() + (7305 * 86400000l));

            KeytoolHelper keyHelper = new KeytoolHelper();
            KeyStore ks = keyHelper.generateKeyStore(keyStoreFile, "JKS", installAgentKeystorePwd);
            KeyPair pair = keyHelper.generateKeyPair("RSA", 2048);

            PrivateKey privKey = pair.getPrivate();
            PublicKey pubKey = pair.getPublic();

            Extension extension = new Extension(pubKey, false);
            extension.SetExtensionIdentifierAsSubjectKey();
            List<Extension> extensions = new ArrayList<Extension>();
            extensions.add(extension);

            Certificate cert = keyHelper.buildWithExtensions("CN="+productPrefix+"_agent", from, to,
                            pair, "SHA256WithRSA", extensions);
            Certificate[] certArray = new Object[1]
            certArray[0] = cert

            ks.setKeyEntry(productPrefix + "_agent", privKey, installAgentKeystorePwd.toCharArray(), certArray);
            ks.store(new FileOutputStream(keyStoreFile.getAbsolutePath()), installAgentKeystorePwd.toCharArray());
        }

        if (installAgentId == null) {
            installAgentId = ""
        }

        // so we do not get "null"
        installAgentProxyHost = installAgentProxyHost ? installAgentProxyHost : ""
        installAgentProxyPort = installAgentProxyPort ? installAgentProxyPort : ""

        // ensure replacements are initialized
        if (installAgentDirTokenReplacement == null) {
            installAgentDirTokenReplacement = installAgentDir
        }
        if (javaHomeTokenReplacement == null) {
            javaHomeTokenReplacement = javaHome
        }

        def brokerURL = null

        // construct the broker URL if not already defined
        if (installBrokerURL == null) {
            // if installer is interactive and installWithRelay is true, no default is provided and
            // no validation is done. so if user just presses enter through the prompt, null gets
            // added to the list
            installAgentRemoteHostList.remove(null)

            brokerURL = "failover:("
            for (int i = 0; i < installAgentRemoteHostList.size(); i++) {
                brokerURL += "ah3://" + installAgentRemoteHostList.get(i) + ":" + installAgentRemotePortList.get(i)
                if (i+1 != installAgentRemoteHostList.size()) {
                    brokerURL += ","
                }
            }
            brokerURL += ")"
        }
        else {
            brokerURL = installBrokerURL
        }

        if (!encryptionKeyStorePath) {
            encryptionKeyStorePath = "../conf/encryption.keystore";
        }
        if (!keyStorePassword) {
            //set to default
            keyStorePassword = "changeit"
        }

        if (!encryptionKeystoreAlias) {
            def uniquePart = RandomStringUtils.randomAlphanumeric(4)
            def prefix = "aes128key"
            encryptionKeystoreAlias = "${prefix}${uniquePart}".toLowerCase()
        }

        if (!doUpgrade) {
            ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                entry(key: "locked/agent.brokerUrl", value: brokerURL)
                entry(key: "locked/agent.jms.remote.host", value: installAgentRemoteHostList.get(0))
                entry(key: "locked/agent.jms.remote.port", value: installAgentRemotePortList.get(0))
                entry(key: "locked/agent.http.proxy.host", value: installAgentProxyHost)
                entry(key: "locked/agent.http.proxy.port", value: installAgentProxyPort)
                entry(key: productName + "/java.home", value: javaHomeTokenReplacement)
                entry(key: "locked/agent.home", value: installAgentDirTokenReplacement)
                entry(key: "locked/agent.mutual_auth", value: installAgentMutualAuth)
                entry(key: "agent.jms.full_encryption", operation:"del");
                if (disableFullEncryption != null && disableFullEncryption) {
                    entry(key: "agent.jms.disable_full_encryption", value: disableFullEncryption)
                }
                entry(key: "locked/agent.keystore", value: installAgentKeystore)
                entry(key: "locked/agent.keystore.pwd", value: CryptStringUtil.encrypt(installAgentKeystorePwd))
                entry(key: "system.default.encoding", value: systemEncoding)
                entry(key: "agent.HttpFailoverHandler.disabled", value: installDisableHttpFailover)
                entry(key: "verify.server.identity", value: installAgentVerifyServerIdentity)
                entry(key: "encryption.keystore.password", value: CryptStringUtil.encrypt(keyStorePassword))
                entry(key: "encryption.keystore", value: encryptionKeyStorePath)
                entry(key: "encryption.keystore.alias", value: encryptionKeystoreAlias)
            }
            if (installAgentServerUrl != null && !installAgentServerUrl.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "server.url", value: installAgentServerUrl)
                }
            }
            if (installAgentId != null && !installAgentId.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "locked/agent.id", value: installAgentId)
                }
            }
            if (installAgentName != null && !installAgentName.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "locked/agent.name", value: installAgentName)
                }
            }
            if (installAgentTeams != null && !installAgentTeams.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "locked/agent.initial.teams", value: installAgentTeams)
                }
            }
            if (isZos) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "com.urbancode.shell.impersonation.unix.suFormat", value: "%s -s %u -c %c")
                }
            }
        }
        else {
            ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                entry(key: "locked/agent.id", default: installAgentId)
                entry(key: "locked/agent.brokerUrl", default: brokerURL)
                entry(key: "locked/agent.jms.remote.host", default: installAgentRemoteHostList.get(0))
                entry(key: "locked/agent.jms.remote.port", default: installAgentRemotePortList.get(0))
                entry(key: "locked/agent.http.proxy.host", default: installAgentProxyHost)
                entry(key: "locked/agent.http.proxy.port", default: installAgentProxyPort)
                entry(key: productName + "/java.home", default: javaHomeTokenReplacement)
                entry(key: "locked/agent.home", default: installAgentDirTokenReplacement)
                entry(key: "locked/agent.mutual_auth", default: installAgentMutualAuth)
                entry(key: "agent.jms.full_encryption", operation:"del");
                if (disableFullEncryption != null && disableFullEncryption) {
                    entry(key: "agent.jms.disable_full_encryption", default: disableFullEncryption)
                }
                entry(key: "locked/agent.keystore", default: installAgentKeystore)
                entry(key: "locked/agent.keystore.pwd", default: CryptStringUtil.encrypt(installAgentKeystorePwd))
                entry(key: "system.default.encoding", default: systemEncoding)
                entry(key: "agent.HttpFailoverHandler.disabled", default: installDisableHttpFailover)
                entry(key: "verify.server.identity", default: installAgentVerifyServerIdentity)
                entry(key: "encryption.keystore.password", default: CryptStringUtil.encrypt(keyStorePassword))
                entry(key: "encryption.keystore", default: encryptionKeyStorePath)
                entry(key: "encryption.keystore.alias", default: encryptionKeystoreAlias)
            }
            if (installAgentServerUrl != null && !installAgentServerUrl.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "server.url", default: installAgentServerUrl)
                }
            }
            if (installAgentId != null && !installAgentId.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "locked/agent.id", default: installAgentId)
                }
            }
            if (installAgentName != null && !installAgentName.isAllWhitespace()) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "locked/agent.name", default: installAgentName)
                }
            }
            if (isZos) {
                ant.propertyfile(file: installAgentDir + "/conf/agent/installed.properties") {
                    entry(key: "com.urbancode.shell.impersonation.unix.suFormat", default: "%s -s %u -c %c")
                }
            }
        }

        // copy lib files
        ant.copy(todir: installAgentDir + "/lib", overwrite: 'true') {
            fileset(dir: srcDir + "/lib") {
                include(name: "*.jar")
                include(name: "Validation.jar")
            }

            fileset(dir: srcDir + "/lib/bsf") {
                include(name: "*.jar")
            }

            // include external lib files (distribution project may have unpacked these)
            if (new File(srcDir,'../lib').exists() && new File(srcDir, 'lib/include-main-jars.conf').exists()) {
                fileset(dir: '../lib', includesfile:'lib/include-main-jars.conf')
            }
        }

        // copy monitor files
        ant.copy(todir: installAgentDir + "/monitor", overwrite: 'true') {
            fileset(dir: srcDir + "/monitor") {
                include(name: "*.jar")
            }
        }

        // copy native files
        File nativeSrcDir = new File(srcDir + "/native/" + installOs)
        if (nativeSrcDir.isDirectory()) {
            ant.copy(todir: installAgentDir + "/native", overwrite: 'true', failonerror: 'false') {
                fileset(dir: srcDir + "/native/" + installOs) {
                    include(name: "**")
                }
            }
        }

        // copy opt files
        ant.copy(todir: installAgentDir + "/opt", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir: srcDir + "/opt") {
                include(name: "apache-ant-*/**/*")
                include(name: "groovy-*/**/*")
            }
        }

        // run platform specific install
        if (isUnix) {
            copyAgentFilesUnix(installAgentDir, doUpgrade)
            removeWorkingDirFromClasspathUnix(installAgentDir)
        }
        else if (isWindows) {
            copyAgentFilesWindows(installAgentDir, doUpgrade)
            removeWorkingDirFromClasspathWindows(installAgentDir)
        }

        writePluginJavaOpts()

        if (isZos) {
            installAgentFileszOS()
        }

        // chmod opt bin files
        ant.chmod(perm: "+x", type: "file") {
            fileset(dir: installAgentDir) {
                include(name: "opt/*/bin/*")
                exclude(name: "opt/*/bin/*.bat")
                exclude(name: "opt/*/bin/*.cmd")
            }
        }
    }

    /**
     * Copy the unix specific files to the agent directory.  Make any necessary substitutions and file permission
     * alterations.
     */
    private void copyAgentFilesUnix(installAgentDir, doUpgrade) {

        //copy udclient
        ant.copy(todir: installAgentDir + "/opt/udclient/", overwrite: 'true') {
            fileset(dir:srcDir + '/opt/udclient') {
                include(name:'udclient.jar')
                include(name:'udclient')
            }
        }

        ant.chmod(file: installAgentDir + "/opt/udclient/udclient", perm:"+x")

        // create a directory for the init script
        ant.mkdir(dir: installAgentDir + "/bin/init")

        // ensure replacements are initialized
        if (installAgentDirTokenReplacement == null) {
            installAgentDirTokenReplacement = installAgentDir
        }
        if (javaHomeTokenReplacement == null) {
            javaHomeTokenReplacement = javaHome
        }
        if (installInitUser == null) {
            installInitUser = ""
        }
        if (installInitGroup == null) {
            installInitGroup = ""
        }


        ant.filterset(id: 'agent-unix-filterset') {
            filter(token: "AGENT_HOME",    value: installAgentDirTokenReplacement)
            filter(token: "AGENT_USER",    value: installInitUser)
            filter(token: "AGENT_GROUP",   value: installInitGroup)
            filter(token: "product.prefix",   value: productPrefix)
            filter(token: "product.name",   value: productName)
            filter(token: "product.capital.name",   value: productCapitalName)
            filter(token: "JAVA_HOME",       value: javaHomeTokenReplacement)
            filter(token: "JAVA_DEBUG_OPTS", value: '-Xdebug -Xrunjdwp:transport=dt_socket,address=localhost:10001,server=y,suspend=n -Dcom.sun.management.jmxremote')
            filter(token: "JAVA_OPTS",       value: "-Xmx512m")
        }

        //
        // Copy all scripts and unix-specific files
        //

        ant.copy(todir: installAgentDir + "/bin/", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir:srcDir+'/bin/agent') {
                include(name:'agent')
                include(name:'*tool')
                include(name:'codestation')
                include(name:'init/agent')
                include(name:'configure-agent')
            }
            filterset(refid: 'agent-unix-filterset')
            compositemapper {
                mapper(type:'glob', from:'configure-agent', to:'configure-agent')
                mapper(type:'glob', from:'*agent', to:'*agent')
                mapper(type:'glob', from:'*tool', to:'*tool')
                mapper(type:'glob', from:'codestation', to:'codestation')
            }
        }

        ant.copy(todir: installAgentDir + "/bin/", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir:srcDir+'/bin/agent') {
                include(name:'classpath.conf.UNIX')
                include(name:'worker-args.conf.UNIX')
            }
            filterset(refid: 'agent-unix-filterset')
            mapper(type:'regexp', from:'^(.*)\\.UNIX$', to:'\\1')
        }

        //
        // Fix line endings and permissions
        //

        // locate all files that are native scripts
        ant.fileset(dir: installAgentDir, id: 'agentNativeScriptFileSet') {
            patternset(id:'agentNativeScriptFileSelector') {
                include(name: 'bin/agent')
                include(name: 'bin/init/agent')
                include(name: 'bin/configure-agent')
                include(name: 'opt/apache-ant-*/bin/ant')
                include(name: 'opt/groovy-*/bin/groovy')
                include(name: 'opt/groovy-*/bin/groovyc')
            }
        }

        // Link old executables to the new one if upgrading.
        if(doUpgrade) {
            makeSymlinksToNewExec(installAgentDir)
        }

        // fix line endings - must be done before chmod
        if (!isZos) {
            ant.fixcrlf(srcDir: installAgentDir, eol: "lf", encoding: "UTF-8", outputEncoding: "UTF-8") {
                include(name: "bin/classpath.conf")
                patternset(refid:'agentNativeScriptFileSelector')
            }
        }

        // adjust modes on scripts - must be after doing fixcrlf
        ant.chmod(perm: "+x", type: "file") {
            fileset(refid:'agentNativeScriptFileSet')
        }

        ant.chmod(perm: "+x", type: "file", dir: installAgentDir + "/native", includes: "**")
    }

    //
    // private method for linking old executable files to the new when upgrading.
    //

    private void makeSymlinksToNewExec(installAgentDir) {
        // List of known deprecated executable file names.
        def oldExecNames = ["udagent", "ibm-ucdagent"]

        // The executable name currently in use.
        def newExecName = "agent"

        // Necessary for creating symlinks.
        Unix unix = new Unix();

        for(String n : oldExecNames) {
            // File objects for an old executable and its corresponding .bak file.
            File oldExec = new File(installAgentDir + "/bin/" + n)
            File oldExecBak = new File(installAgentDir + "/bin/" + n + ".bak")

            // Same as above, but for the executable in the init directory.
            File oldExecInit = new File(installAgentDir + "/bin/init/" + n)
            File oldExecInitBak = new File(installAgentDir + "/bin/init/" + n + ".bak")

            if(oldExec.exists() && !oldExec.isDirectory()) {
                if(!oldExecBak.exists()) {
                    IO.copy(oldExec, oldExecBak)
                }
                IO.delete(oldExec)
                unix.mksymlink(oldExec, installAgentDir + "/bin/" + newExecName)
            }

            if(oldExecInit.exists() && !oldExecInit.isDirectory()) {
                if(!oldExecInitBak.exists()) {
                    IO.copy(oldExecInit, oldExecInitBak)
                }
                IO.delete(oldExecInit)
                unix.mksymlink(oldExecInit, installAgentDir + "/bin/init/" + newExecName)
            }
        }
    }

    private void copyAgentFilesWindows(installAgentDir, doUpgrade) {

        //copy udclient
        ant.copy(todir: installAgentDir + "/opt/udclient/", overwrite: 'true') {
            fileset(dir:srcDir + '/opt/udclient') {
                include(name:'udclient.jar')
                include(name:'udclient.cmd')
            }
        }

        // create a directory for the service script and exe
        ant.mkdir(dir: installAgentDir + "\\bin\\service")

        def overwriteNative = doUpgrade ? 'true' : 'false'

        // ensure replacements are initialized
        if (installAgentDirTokenReplacement == null) {
            installAgentDirTokenReplacement = installAgentDir
        }
        if (javaHomeTokenReplacement == null) {
            javaHomeTokenReplacement = javaHome
        }

        // create a shared filter set
        ant.filterset(id: "agent-windows-filterset") {
            filter(token: "TEMPLATE", value: "agent")
            filter(token: "AGENT_HOME", value: installAgentDirTokenReplacement)
            filter(token: "JAVA_HOME", value: javaHomeTokenReplacement)
            filter(token: "product.prefix",   value: productPrefix)
            filter(token: "product.name",   value: productName)
            filter(token: "product.capital.name",   value: productCapitalName)
            filter(token: "ARCH", value: installArch)
            filter(token: "JAVA_OPTS", value: "-Xmx512m")
            filter(token: "JAVA_DEBUG_OPTS", value: '-Xdebug -Xrunjdwp:transport=dt_socket,address=10001,server=y,suspend=n -Dcom.sun.management.jmxremote')
        }

        ant.copy(toDir: installAgentDir + "\\bin", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir: srcDir+"\\bin\\agent") {
                include(name:'agent.cmd')
                include(name:'configure-agent.cmd')
            }

            filterset(refid: "agent-windows-filterset")
            compositemapper {
                mapper(type:'glob', from:'agent.cmd', to:'agent.cmd')
                mapper(type:'glob', from:'configure-agent.cmd', to:'configure-agent.cmd')
            }
        }

        ant.copy(toDir: installAgentDir + "\\bin", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir: srcDir+"\\bin\\agent") {
                include(name:'classpath.conf.WIN')
                include(name:'worker-args.conf.WIN')
            }

            filterset(refid: "agent-windows-filterset")
            mapper(type:'regexp', from:'^(.*)\\.WIN$', to:'\\1')
        }

        ant.copy(toDir: installAgentDir + "\\bin", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir: srcDir+'\\bin') {
                include(name:'TEMPLATE_run.cmd')
                include(name:'TEMPLATE_start.cmd')
                include(name:'TEMPLATE_stop.cmd')
            }
            filterset(refid: "agent-windows-filterset")
            mapper(type:'regexp', from:'^TEMPLATE_(.*)\\.cmd$', to:'\\1_agent.cmd')
        }

        // create service install script
        // do NOT attempt to quote spaces in JAVA_OPTS here
        ant.copy(todir: installAgentDir + "\\bin\\service", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir:srcDir + "\\bin\\agent\\service\\") {
                include(name:'_agent.cmd')
            }

            filterset { filter(token: "JAVA_OPTS", value: "") }
            filterset(refid: "agent-windows-filterset")
            compositemapper {
                mapper(type:'glob', from:'agent.cmd', to:'agent.cmd')
                mapper(type:'glob', from:'_agent.cmd', to:'_agent.cmd')
            }
        }


        ant.copy(todir: installAgentDir + "\\bin", overwrite: 'true') {
            fileset(dir: srcDir + "\\bin") {
                include(name: "Impersonater.exe") // copy Impersonater exe
            }
        }

        // copy service exe
        if (new File(srcDir + "\\bin\\agentservice.exe").exists()) {
            ant.copy(file: srcDir + "\\bin\\agentservice.exe", todir: installAgentDir + "\\bin\\service", overwrite: 'true')
        }

        // copy the service configurator vbs scripts
        ant.copy(todir: installAgentDir + "\\bin", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir: srcDir + "\\bin\\agent") {
                include(name: "service\\agent_srvc_configurator.vbs")
                include(name: "service\\agent_srvc_stop.vbs")
                include(name: "service\\agent_srvc_install.vbs")
            }

            filterset(refid: "agent-windows-filterset")
        }

        // fix line endings - must be done before chmod
        ant.fixcrlf(srcDir: installAgentDir, encoding: "UTF-8", outputEncoding: "UTF-8") {
            include(name: "bin\\agent.cmd")
            include(name: "bin\\classpath.conf")
            include(name: "bin\\worker-args.conf")
            include(name: "bin\\configure-agent.cmd");
            include(name: "bin\\run_agent.cmd")
            include(name: "bin\\start_agent.cmd")
            include(name: "bin\\stop_agent.cmd")
            include(name: "bin\\service\\agent.cmd")
            include(name: "bin\\service\\_agent.cmd")
            include(name: "bin\\service\\agent_srvc_configurator.vbs")
            include(name: "bin\\service\\agent_srvc_stop.vbs")
            include(name: "bin\\service\\agent_srvc_install.vbs")
        }
    }

    private void installAgentFileszOS() {

        // Copy zos-specific files
        // A zOS system is also identified as Unix, most of the installation is done in copyAgentFilesUnix
        ant.filterset(id: 'agent-zos-filterset') {
            filter(token: "JAVA_HOME",       value: javaHomeTokenReplacement)
        }
        ant.copy(todir: installAgentDir + "/bin/", overwrite: 'true', encoding: "UTF-8", outputEncoding: "UTF-8") {
            fileset(dir:srcDir+'/bin/agent') {
                include(name:'install-zos-tools')
            }
            filterset(refid: "agent-zos-filterset")
        }
        
        // files are copied as UTF8, convert to native encoding 
        IO.transcode(agentEncoding, systemEncoding, new File(installAgentDir + "/opt/udclient/udclient"))
        IO.transcode(agentEncoding, systemEncoding, new File(installAgentDir + "/bin/agent"))
        IO.transcode(agentEncoding, systemEncoding, new File(installAgentDir + "/bin/init/agent"))
        IO.transcode(agentEncoding, systemEncoding, new File(installAgentDir + "/bin/configure-agent"))
        IO.transcode(agentEncoding, systemEncoding, new File(installAgentDir + "/bin/install-zos-tools"))

        // transcoding the files removes the execute permission from them.
        ant.chmod(perm: "+x", type: "file") {
            fileset(dir: installAgentDir) {
                include(name: "opt/udclient/udclient")
                include(name: "bin/agent")
                include(name: "bin/init/agent")
                include(name: "bin/configure-agent")
                include(name: "bin/install-zos-tools")
            }
        }

        ant.copy(todir: installAgentDir + "/conf", overwrite: 'true',  failonerror: 'false') {
            fileset(dir:srcDir) {
                include(name:'smpe.version')
            }
        }

        //unzip the zos toolkit files
        ant.exec(executable:"pax", dir: installAgentDir){
            arg(value: "-r")
            arg(value: "-pp")
            arg(value: "-f")
            arg(value: "${installAgentDir}/native/ucdtoolkit.pax")
        }
    }

    private String getInstallDir(installComponent, installDir) {
        String defaultDir =  null
        if (Os.isFamily('mac')) {
            defaultDir = '/Library/' + productPrefix + '/'+installComponent
        }
        else if (Os.isFamily('unix')) {
            defaultDir = '/opt/' + productPrefix + '/' + installComponent
            if(Os.isFamily("z/os")){
                 println "The agent directory length must not exceed 38 characters in z/OS."
            }
        }
        else if (Os.isFamily('windows')) {
            String progFiles = ant.project.properties.'ProgramFiles'
            if (progFiles != null && progFiles.length() > 0 ) {
                defaultDir = progFiles+'\\' + productPrefix + '\\'+installComponent
            }
            else {
                defaultDir = "C:\\Program Files" + File.separator + installComponent.replace('/', '\\')
            }
        }

        installDir = prompt(
                installDir,
                'Enter the directory where ' + installComponent + ' should be installed.' +
                (defaultDir == null ? '' : ' [Default: '+defaultDir+']'),
                defaultDir,
                requiredValidator)

        if (!new File(installDir).exists()) {
            String createDir = prompt(
                    null,
                    'The specified directory does not exist. Do you want to create it? Y,n [Default: Y]',
                    'Y',
                    yesNoValidator)
            if ('Y'.equalsIgnoreCase(createDir) || 'YES'.equalsIgnoreCase(createDir)) {
                new File(installDir).mkdirs()
            }
            else {
                ant.fail('Can not install without creating installation directory.')
            }
        }

        return new File(installDir).absolutePath
    }

    private boolean checkForUpgrade(installDir, componentInstall, installServiceName) {
        if (new File(installDir + '/conf/installed.version').exists()) {
            ant.property(file: installDir + '/conf/installed.version')

            if (new File(installDir + '/var/' + productName + '-' + componentInstall + '.pid').exists()) {
                ant.fail('A previously installed version of ${component.name} is running. ' +
                     'Please shutdown the running ${component.name} and start the installation again.')
            }

            if (nonInteractive) {
                return true
            }

            def doUpgrade = prompt(
                    null,
                    'A previous version (' + ant.project.properties.'installed.version' + ') ' +
                    'exists in the specified directory. Do you want to upgrade the currently ' +
                    'installed version? [Y,n]',
                    'Y',
                    yesNoValidator)
            if (doUpgrade == 'Y' || doUpgrade == 'y') {

                //stopping service if running
                if (componentInstall == 'agent' && installServiceName != null && (Os.isFamily('windows'))) {
                       println('\nYour Agent is installed as "' + installServiceName + '" service.\n\n')
                    ant.exec(dir: './bin/agent/service', executable: 'cscript.exe') {
                        arg(value:'//I')
                        arg(value:'//Nologo')
                        arg(value:'agent_srvc_stop.vbs')
                        arg(value:installServiceName)
                    }
                }

                return true
            }
            ant.fail('Not upgrading the existing installation.')
        }

        return false
    }

    private String prompt(promptText) {
        return prompt(null, promptText, null)
    }

    private String prompt(curValue, promptText) {
        return prompt(curValue, promptText, null)
    }

    private String prompt(curValue, promptText, defaultValue) {
        return prompt(curValue, promptText, defaultValue, null)
    }

    private String prompt(curValue, promptText, defaultValue, validator) {
        // use curValue if not null and not empty
        if (curValue != null && curValue.trim()) {
            return curValue
        }

        if (nonInteractive) {
            println(promptText)

            def warningMessage = 'Warning: Installer prompting for input in non-interactive mode.'
            if (defaultValue) {
                warningMessage += '  Returning default: ' + defaultValue
            }
            println(warningMessage)

            if (validator != null) {
                try {
                    validator.validate(defaultValue)
                } catch (ValidationException ve) {
                    throw new IllegalArgumentException(
                            "Non-Interactive Mode: problem with default value of '${defaultValue}' " +
                            "for '${promptText}' - " + ve.getValidationMessageArray().join(' '))
                }
            }
            return defaultValue
        }

        def userValue = null
        def valid = false
        while (!valid) {
            println(promptText)
            userValue = read(defaultValue)

            if (validator != null) {
                try {
                    validator.validate(userValue)
                    valid = true
                }
                catch (ValidationException ve) {
                    for (message in ve.getValidationMessageArray()) {
                        println(message)
                    }
                }
            }
            else {
                valid = true
            }
        }

        return userValue
    }

    private String read(defaultValue) {
        def line = systemIn.readLine()?.trim()
        return line ?: defaultValue
    }

    private void println(displayText) {
        if (displayText != null) {
            ant.echo(displayText)
        }
    }

    private def getAllIPs(){
        def ips = []
        for (interfaz in java.net.NetworkInterface.getNetworkInterfaces()) {
            for (inet in interfaz.getInetAddresses()) {
                ips.add(inet.getHostAddress())
            }
        }
        // filter self-assinged ips
        return ips.findAll{!(it.startsWith('169.254') || it.startsWith('fe80:'))}
    }



    private def getAllIPv4s(){
        return getAllIPs().findAll{it.split('\\.') == 4}
    }

    private def getPrimaryIPv4(){
        // only use localhost as primary ip if no other public ips are present
        def result = getAllIPs().find{it != "localhost" && it != '127.0.0.1'}
        return result == null ? 'localhost' : result
    }

    private def getAllIPv6s(){
        return getAllIPs().findAll{it.split(':') > 2}
    }

     // command is a string or string array
    private Map runCommand(command) {
        def proc = command.execute()
        proc.waitFor()
        def result = [:] // empty map
        result["out"] = proc.in.text // std output of the process
        result["err"] = proc.err.text
        result["exit"] = proc.exitValue()
        return result
    }

     /**
      * runs the specified script with the given arguments
      * @param script the path to the script ot run, given as relative to the src dir
      * @param args   the arguments to provide to the script
      */
    private void runGroovyScript(script, args) {
        def extclasspath = ant.path() {
            fileset(dir: srcDir + '/lib/ext')
            pathelement(path: classpath)
        }

        // need groovy-all-*.jar and commons-cli.jar (and driver-class) on classpath
        ant.java(
                classname:'groovy.lang.GroovyShell',
                fork:'yes',
                failonerror: true,
                dir: srcDir,
                classpath: extclasspath
        ) {
            arg(value: new File(srcDir+'/'+script).canonicalPath)

            for (argument in args) {
                arg(value: argument)
            }
        }
    }

    private Map getSystemPropertiesForRuntime(javaHome, classpath) {
        def result = [:]
        try {
            def process = ["${javaHome}" + File.separator + "bin" + File.separator + "java",
                           "-Xmx64m", "-Xms64m", "-classpath", classpath,
                           "com.urbancode.commons.detection.GetSystemProperties"].execute()
            process.consumeProcessErrorStream(System.err)
            process.in.eachLine {
                def prop = it.split("=", 2)
                result[prop[0]] = StringEscapeUtils.unescapeJava(prop[1])
            }
        }
        catch (Exception e) {
            result = null
        }
        return result
    }

    private String getSSLKeyManagerFactoryDefaultAlgorithmForRuntime(javaHome, classpath) {
        def result = null
        try {
            def process = ["${javaHome}" + File.separator + "bin" + File.separator + "java",
                           "-Xmx64m", "-Xms64m", "-classpath", classpath,
                           "com.urbancode.commons.detection.GetSSLKeyManagerFactoryDefaultAlgorithm"].execute()
            process.consumeProcessErrorStream(System.err)
            result = process.text.trim()
        }
        catch (Exception e) {
        }

        if (result == null || result.size() == 0) {
            result = 'SunX509'
        }
        return result
    }

    private String getOs(javaHome, classpath) {
        if (installOs == null) {
            try {
                def process = ["${javaHome}" + File.separator + "bin" + File.separator + "java",
                               "-Xmx64m", "-Xms64m", "-classpath", classpath,
                               "com.urbancode.commons.detection.GetOs"].execute()
                process.consumeProcessErrorStream(System.err)
                installOs = process.text.trim()
            }
            catch (Exception e) {
                println "Error retrieving OS. Installation may not complete correctly. Error: ${e.message}"
            }
        }
        return installOs
    }

    private String getArch(javaHome, classpath) {
        if (installArch == null) {
            try {
                def process = ["${javaHome}" + File.separator + "bin" + File.separator + "java",
                               "-Xmx64m", "-Xms64m", "-classpath", classpath,
                               "com.urbancode.commons.detection.GetArch"].execute()
               process.consumeProcessErrorStream(System.err)
               installArch = process.text.trim()
            }
            catch (Exception e) {
                println "Error retrieving system architecture. Installation may not complete correctly. Error: ${e.message}"
            }
        }
        return installArch
    }

    // Get the system encoding. Use console.encoding (ibm jdk) if set, otherwise use file.encoding
    private String getSystemEncoding() {
        def systemEncoding = System.properties.'console.encoding'
        if (systemEncoding == null) {
            systemEncoding = System.properties.'file.encoding'
        }
        return systemEncoding
    }

    private void writePluginJavaOpts() {
        File pluginJavaOpts = new File(installAgentDir + "/conf/plugin-javaopts.conf")
        if (!pluginJavaOpts.exists()) {
            pluginJavaOpts.createNewFile();
            pluginJavaOpts.withWriter("UTF-8") { out ->
                out.writeLine('-Dfile.encoding=${system.default.encoding}')
                out.writeLine('-Dconsole.encoding=${system.default.encoding}')
            }
        }
        else {
            // At one point, java.io.tmpdir was added to the plugin-javaopts.conf file
            // Spaces in the value caused the argument to be interpreted incorrectly,
            // preventing the agent from doing work, so we remove the old line here
            def javaOptArray = []
            pluginJavaOpts.eachLine("UTF-8") { line ->
                line = line.trim()
                if (!line.isEmpty() && line != '-Djava.io.tmpdir=${java.io.tmpdir}') {
                    javaOptArray << line
                }
            }
            pluginJavaOpts.write(javaOptArray.join(System.getProperty("line.separator")), "UTF-8")
        }
    }

    private String getProtocolForRuntime(javaHome, classpath) {
        def result = null
        try {
            def properties = getSystemPropertiesForRuntime(javaHome, classpath)
            if (properties != null && properties["java.vendor"].toLowerCase().contains("ibm")) {
                result = 'SSL_TLSv2'
            }
        }
        catch (Exception e) {
        }

        if (result == null || result.size() == 0) {
            result = 'TLSv1.2'
        }
        return result
    }

    private boolean parseBoolean(String s) {
        return Boolean.valueOf(s) ||
            "Y".equalsIgnoreCase(s) ||
            "YES".equalsIgnoreCase(s)
    }


    /*
     * Groovy 1.8.8 adds the current working directory to the CLASSPATH even
     * when the CLASSPATH is explicitly defined. This is incorrect behavior and
     * will cause issues since users may download artifacts and or set a current
     * working directory which contains classes which we don't want to have
     * loaded.
     *
     * We modify the startGroovy scripts on to fix this issue. If the version of
     * groovy is changed it will likely break our fix.I have put a check in
     * place to alert future DEVs to this if or when the groovy version is
     * updated.
     */
    private void warnAboutGroovyVersion(String contents) {
        // I split the string here to prevent a a find/replace from changing this
        if (contents.indexOf("groovy-1"+".8"+".8.jar") == -1) {
            println("Warning: using a version of groovy other than 1"+".8"+".8 may introduce a regression where" +
                "the current working directory is added onto Groovy's classpath.")
        }
    }

    private void removeWorkingDirFromClasspathUnix(String agentDir) {
        File startGroovyFile = new File(agentDir + "/opt/groovy-1.8.8/bin/startGroovy");
        String contents = startGroovyFile.getText();
        warnAboutGroovyVersion(contents)
        contents = contents.replace('CP="$CLASSPATH":.', 'CP="$CLASSPATH"');
        contents = contents.replace('CP="$CP":.', 'CP="$CP"')
        contents = contents.replace('CP="$CP:.', 'CP=')
        startGroovyFile.write(contents)
    }

    private void removeWorkingDirFromClasspathWindows(String agentDir) {
        File startGroovyFile = new File(agentDir + "/opt/groovy-1.8.8/bin/startGroovy.bat");
        String contents = startGroovyFile.getText();
        warnAboutGroovyVersion(contents)
        contents = contents.replace('set CP=%CP%;.":.', 'set CP=%CP%')
        contents = contents.replace('set CP=.', 'set CP=');
        startGroovyFile.write(contents)
    }

    /**
     * Makes a HTTP request to the given URL.  Throws an exception if the request fails.
     */
    private executeGetMethod (requestUrl) {
        HttpGet request = new HttpGet(requestUrl)

        request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        request.addHeader("Content-Type", "text/html;charset=UTF-8")
        request.addHeader("User-Agent", "Mozilla") // required because deploy server rejects httpclient's default

        HttpClient client = createHttpClient()

        def response = client.execute(request)
        def statusLine = response.getStatusLine()
        def statusReason = statusLine.getReasonPhrase()
        def statusCode = statusLine.getStatusCode()

        // Throws an exception if the request did not return status code of 200.
        if (statusCode != 200) {
            throw new Exception ("Response reason: \"${statusReason}\"; status code: ${statusCode}")
        }
    }

    private probeGenerateKeyAPI(requestUrl) {
        while (requestUrl.endsWith("/")) {
            requestUrl = requestUrl.substring(0, requestUrl.length() - 1);
        }

        def cookie = UUID.randomUUID().toString();

        def requestJson = JsonOutput.toJson([probe: true, cookie: cookie]);

        def request = new HttpPost(requestUrl + "/rest/agent/generateKey")
        def entity = new StringEntity(requestJson.toString())
        entity.setContentType("application/json")
        request.setEntity(entity)

        def response = createHttpClient().execute(request)
        def statusCode = response.statusLine.statusCode
        def statusReason = response.statusLine.reasonPhrase
        if (statusCode != 200) {
            throw new Exception("Probe failed: HTTP call failed: reason: \"${statusReason}\"; status code: ${statusCode}")
        }
        def responseContent = EntityUtils.toString(response.getEntity())

        def responseJson = new JsonSlurper().parseText(responseContent)
        if (!responseJson.probe) {
            throw new Exception("Probe failed: invalid probe response")
        }
        if (cookie != responseJson.cookie) {
            throw new Exception("Probe failed: probe cookie corrupt")
        }
    }

    /**
     * Create a instance of HttpClient that shares connections and adheres to the JVM proxy settings.
     */
    private HttpClient createHttpClient() {

        HttpClientBuilder builder = new HttpClientBuilder()

        // so we do not get "null"
        def tmpInstallAgentProxyHost = installAgentProxyHost ? installAgentProxyHost : ""
        def tmpInstallAgentProxyPort = installAgentProxyPort ? installAgentProxyPort : ""

        int httpProxyPort = 0
        if (StringUtils.isNotBlank(tmpInstallAgentProxyPort)) {
            httpProxyPort = Integer.parseInt(tmpInstallAgentProxyPort)
        }

        if (StringUtils.isNotBlank(tmpInstallAgentProxyHost)) {
            builder.setProxyHost(tmpInstallAgentProxyHost)
        }
        if (httpProxyPort > 0) {
            builder.setProxyPort(httpProxyPort)
        }

        boolean trustAllCerts = !installAgentVerifyServerIdentity
        builder.setTrustAllCerts(trustAllCerts)

        builder.setTimeoutMillis(10000)

        return builder.buildClient()
    }

    private void createSecretKey() {
        File installDir = new File(installAgentDir)
        File confDir = new File(installDir, "conf")
        File keyStoreFile = new File(confDir, encryptionKeyStorePath)
        ant.property(name: "encryption.keystore", value: keyStoreFile.absolutePath)

        KeyStore keyStore = loadKeyStore(keyStoreFile)

        boolean isKeyStored = false;
        isKeyStored = keyStore.isKeyEntry(encryptionKeystoreAlias)
        if (!isKeyStored) {
            try {
                println "Creating new encryption key."
                SecureRandom sr = SecureRandomHelper.getSecureRandom()
                KeyGenerator keygen = KeyGenerator.getInstance("AES")
                keygen.init(128, sr)
                SecretKey key = keygen.generateKey()

                keyStore.setKeyEntry(encryptionKeystoreAlias, key, keyStorePassword.toCharArray(), null)
                def isKeyStoredNow = keyStore.isKeyEntry(encryptionKeystoreAlias);

            }
            catch (NoSuchAlgorithmException impossible) {
                throw new RuntimeException(impossible)
            }
            catch (UnsupportedEncodingException impossible) {
            }
            catch (IOException e) {
                throw new SecurityException(e)
            }

            OutputStream output = new FileOutputStream(keyStoreFile)
            try {
                keyStore.store(output, keyStorePassword.toCharArray())
            }
            finally {
                output.close()
            }
        }
        else {
            println "Encryption key retrieved from keystore. Proceeding..."
        }
        ant.property(name: "key.store.password", value: keyStorePassword)
        ant.property(name: "key.store.alias", value: encryptionKeystoreAlias)
    }

    private KeyStore loadKeyStore(File keyStoreFile)
    throws GeneralSecurityException, IOException {

        String type = "JCEKS"
        KeyStore keyStore = null
        try {
            keyStore = KeyStore.getInstance(type)
        }
        catch (KeyStoreException e) {
            throw new RuntimeException("Key store type \"" + type + "\" is not available", e)
        }

        if (keyStoreFile.exists()) {
            InputStream input = new FileInputStream(keyStoreFile)
            try {
                keyStore.load(input, keyStorePassword.toCharArray())
            }
            finally {
                input.close()
            }
        }
        else {
            //new keystores are loaded with null for first arg
            keyStore.load(null, keyStorePassword.toCharArray())
        }

        return keyStore
    }
}
