Controller\Master - The central, coordinating process which stores configuration, loads plugins, and renders the various user interfaces for Jenkins.

Node - A machine which is part of the Jenkins environment and capable of executing Pipelines or Projects. Both the Controller and Agents are considered to be Nodes.

Agent - An agent is component witch typically executes within a machine, or container, which connects to a Jenkins controller and executes tasks when directed by the controller.

Label - User-defined text for grouping Agents, typically by similar functionality or capability. For example linux for Linux-based agents or docker for Docker-capable agents.

Executor - A slot for execution of work defined by a Pipeline or Project on a Node. A Node may have zero or more Executors configured which corresponds to how many concurrent Projects or Pipelines are able to execute on that Node.

Cloud - A System Configuration which provides dynamic Agent provisioning and allocation, such as that provided by the Azure VM Agents or Amazon EC2 plugins.


Project\Job - A user-configured description of work which Jenkins should perform, such as building a piece of software, etc.

Step - A single task; fundamentally steps tell Jenkins what to do inside of a Pipeline or Project.

Build - Result of a single execution of a Project


Core - The primary Jenkins application (jenkins.war) which provides the basic web UI, configuration, and foundation upon which Plugins can be built.


Artifact - An immutable file generated during a Build or Pipeline run which is archived onto the Jenkins Controller for later retrieval by users.
Downstream - A configured Pipeline or Project which is triggered as part of the execution of a separate Pipeline or Project.
Fingerprint - A hash considered globally unique to track the usage of an Artifact or other entity across multiple Pipelines or Projects.
Folder - An organizational container for Pipelines and/or Projects, similar to folders on a file system.

Item - An entity in the web UI corresponding to either a: Folder, Pipeline, or Project.

Jenkins URL - The main url for the jenkins application, as visited by a user. e.g. https://ci.jenkins.io/





LTS
A long-term support Release line of Jenkins products, becoming available for downloads every 12 weeks. See this page for more info.


Pipeline
A user-defined model of a continuous delivery pipeline, for more read the Pipeline chapter in this handbook.

Plugin
An extension to Jenkins functionality provided separately from Jenkins Core.

Publisher - Part of a Build after the completion of all configured Steps which publishes reports, sends notifications, etc. A publisher may report Stable or Unstable result depending on the result of its processing and its configuration. For example, if a JUnit test fails, then the whole JUnit publisher may report the build result as Unstable.
Resource Root URL - A secondary url used to serve potentially untrusted content (especially build artifacts). This url is distinct from the Jenkins URL. 
Release- An event, indicating availability of Jenkins distribution products or one of Jenkins plugins. Jenkins products belong either to LTS or weekly Release lines. 
Stage - stage is part of Pipeline, and used for defining a conceptually distinct subset of the entire Pipeline, for example: "Build", "Test", and "Deploy", which is used by many plugins to visualize or present Jenkins Pipeline status/progress.

Trigger - A criteria for triggering a new Pipeline run or Build.
Update Center - Hosted inventory of plugins and plugin metadata to enable plugin installation from within Jenkins.
Upstream - A configured Pipeline or Project which triggers a separate Pipeline or Project as part of its execution.
Workspace - A disposable directory on the file system of a Node where work can be done by a Pipeline or Project. Workspaces are typically left in place after a Build or Pipeline run completes unless specific Workspace cleanup policies have been put in place on the Jenkins Controller.
