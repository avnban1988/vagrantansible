-------------------------------------------------------------------------------
To install a agent interactively, use:
  
  Windows Example:
    set JAVA_HOME=<your_java_home>
    install-agent.bat
    
  Unix/Linux Example:
    export JAVA_HOME=<your_java_home>
    ./install-agent.sh
    
  
-------------------------------------------------------------------------------
To install a agent using a defined set of properties in a file, use:

  First edit the example.agent.install.properties file and set the
  installation properties as needed.
  
  Windows Example:
    set JAVA_HOME=<your_java_home>
    install-agent-from-file.bat example.agent.install.properties
    
  Unix/Linux Example:
    export JAVA_HOME=<your_java_home>
    ./install-agent-from-file.sh example.agent.install.properties
    
  
-------------------------------------------------------------------------------
To install multiple agents, use:

  First edit the install-many-agents.bat or install-many-agents.sh
  file and set the installation properties as needed.
  
  Windows Example:
    set JAVA_HOME=<your_java_home>
    install-many-agents.bat example.agent.install.properties
    
  Unix/Linux Example:
    export JAVA_HOME=<your_java_home>
    ./install-many-agents.sh example.agent.install.properties
  