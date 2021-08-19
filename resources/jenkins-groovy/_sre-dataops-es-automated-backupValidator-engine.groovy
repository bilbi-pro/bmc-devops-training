import groovy.json.*  
import groovy.transform.ToString
import java.util.*
import hudson.model.*


pipeline {
  agent none
  options {
    ansiColor('xterm')
    skipDefaultCheckout()
  }

  parameters {
    string defaultValue: 'sgp2', description: 'Kaltura enviroment', name: 'SRC_ES_ENV', trim: true
    string defaultValue: 'http://elasticsearch-client.service.consul:9200', description: 'the endpoint to the cluster', name: 'SRC_ES_URI'
    string defaultValue: 'green', description: 'flaviuor AWS tag value for source cluster, (green=active, blue=standby)', name: 'SRC_FLAVIUOR'
    string defaultValue: 'sgt2', description: 'Kaltura enviroment', name: 'TGT_ES_ENV', trim: true
    string defaultValue: 'http://elasticsearch-client-standby.service.consul:9200', description: 'the endpoint to the cluster', name: 'TGT_ES_URI'
    string defaultValue: 'blue', description: 'flaviuor AWS tag value for target cluster, (green=active, blue=standby)', name: 'TGT_FLAVIUOR'
    string defaultValue: '3', description: 'Number of \'MASTER\' nodes', name: 'MAS_NODE_COUNT'
    string defaultValue: '3', description: 'Number of \'MASTER\' nodes', name: 'DTA_NODE_COUNT'
    string defaultValue: '1', description: 'Number of \'MASTER\' nodes', name: 'CLIENT_NODE_COUNT'
    string defaultValue: '0', description: 'Number of \'INGRESS\' nodes', name: 'IGRS_NODE_COUNT'
    string defaultValue: 'sgp2-es-backups', description: 'snapshot repository name', name: 'ES_REPOSITORY'
    string defaultValue: '*', description: 'snapshot pattern name for automated resoration', name: 'ES_SNAPSHOT'
    string  defaultValue: '*', description: 'index pattern name', name: 'ES_INDICES'
    booleanParam defaultValue: false, description: 'to override the configs from the source cluster', name: 'OVERRIDE_SRC_CONF'
    string defaultValue: '{ "type": "s3", "settings": { "bucket": "sgp2-es-backups", "region": "ap-southeast-1", "base_path": "_clusters/green/sgp2-es-backups", "readonly": "true"}', description: 'to override ES config from source?', name: 'DEFUALT_REPO_CONF'
    string defaultValue: '{ "transient" : { "cluster.routing.allocation.cluster_concurrent_rebalance": 40, "cluster.routing.allocation.node_initial_primaries_recoveries": 40, "cluster.routing.allocation.node_concurrent_recoveries": 40, "cluster.routing.allocation.enable" : "all", "indices.recovery.max_bytes_per_sec": "2048mb", "indices.recovery.concurrent_streams": 20, "indices.recovery.concurrent_small_file_streams": 20, "indices.recovery.translog_size": "4mb", "indices.recovery.translog_ops": 4000 }}', description: 'the defualt cluster values ', name: 'DEFUALT_CLUSTER_CONF'
    string defaultValue: '0', description: 'timeout to wait for ES cluster bootstrapping, dt: int, unit: m, def:0, <=0 no limitations ', name: 'ES_BOOTSTRAP_TIMEOUT'
    string defaultValue: '3', description: 'process verbose level', name: '_JOB_VERB_LEVEL'
    booleanParam defaultValue: '3', description: 'process verbose level', name: '_JOB_SKIP_KBOT'
    booleanParam defaultValue: '3', description: 'process verbose level', name: '_JOB_SKIP_MERTIC_REPORTING'
  }
  // environment {
  // }
  stages {
    stage("perform validations on source, ") {
      agent {label SRC_ES_ENV}
      steps {
        getSrcData(SRC_ES_ENV,SRC_ES_URI,ES_REPOSITORY);
        
      }
    }
    stage("perform validations on target") {
      agent {label TGT_ES_ENV}
      steps {
        //locateServiceFromConsul()
        validateTarget();
        
      }
    }


    stage('Setup ES Restore Enviroment') {
      agent {label TGT_ES_ENV}
      steps {
        esAlighnESClusterSize(TGT_ES_ENV)
        esWaitForClusterStatus(TGT_ES_ENV,TGT_ES_URI,ES_BOOTSTRAP_TIMEOUT);
        esApplySettings(TGT_ES_ENV,TGT_ES_URI,DEFUALT_CLUSTER_CONF)
        esRegisterRepo(TGT_ES_ENV,TGT_ES_URI,ES_REPOSITORY,DEFUALT_REPO_CONF)
        
      }
    }

    stage('Performing ES Restore'){
      agent {label TGT_ES_ENV}
      steps{
        checkESStatus(TGT_ES_URI)
        checkESRepository(TGT_ES_URI,ES_REPOSITORY);
        checkESRepositorySnapshot(TGT_ES_URI,ES_REPOSITORY,ES_SNAPSHOT);
        closeIndices(TGT_ES_URI,ES_INDICES);
        deleteIndices(TGT_ES_URI,ES_INDICES);
        restoreIndices(TGT_ES_URI,ES_REPOSITORY,ES_SNAPSHOT,ES_INDICES);
      }
    }

    stage('perform cleanup') {
      agent {label TGT_ES_ENV}
      steps {
        closeIndices(TGT_ES_URI,ES_INDICES);
        deleteIndices(TGT_ES_URI,ES_INDICES);
        shutDownESCluster(TGT_ES_ENV)
        
      }
    }

  }
  post {
    unstable {
      echo "Sending message to Slack"
    }
    failure {
      echo "Sending message to Slack"
    }
    success {
      echo "Sending message to Slack"
    }
  }
}

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

@NonCPS
def parseJsonText(String json) {
  def object = new JsonSlurper().parseText(json)
  if(object instanceof groovy.json.internal.LazyMap) {
      return new HashMap<>(object)
  }
  return object
}

@NonCPS
def validateJson(String json){
  try {
    JsonOutput.prettyPrint(json);
    return true;
  } catch (ignored) {
     return false;
  }
}

//esWaitForClusterStatus(TGT_ES_ENV,TGT_ES_URI,ES_BOOTSTRAP_TIMEOUT);
def esWaitForClusterStatus(String esEnv, String esUri, String esBootstrapTimeout){
  try {
    def _esBootstrapTimeout = esBootstrapTimeout.toInteger();
    if (esBootstrapTimeout.toInteger() >=0) _esBootstrapTimeout=60;
    timeout(time: _esBootstrapTimeout.toInteger() , unit: 'MINUTES') { 
      
      def response = httpRequest(url: "${esUri}/_cluster/health",validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
      println(" WAITING CLUSTER BOOTSTRAP => IN PROGRESS: returned ${response.status}...");
      while (response.status != 200) {
        sleep(time:5,unit:"SECONDS")
        response = httpRequest(url: "${esUri}/_cluster/health",validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
      }
    }
  } catch(Exception err) { 
    reportError("failed waiting to cluster to be ready : ${esUri}\n\t\tInjected settings: '${TGT_ES_ENV}'\n\t\t response: '${err}'")
    // if(err.getCauses()[0].getUser().toString() == 'SYSTEM') { // SYSTEM means timeout.

    //   reportError("failed Appling ES Cluster Snapshot Repository for: ${esUri}\n\t\tInjected settings: '${esRepoConf}'\n\t\t response: '${response.content}'")
    // }
  }
}
def esApplySettings(String esEnv, String esUri, String esClusterConf){
  if (validateJson(esClusterConf)!=true) reportError("failed to validate proper json for desired ES cluster settings");
  def response = httpRequest( consoleLogResponseBody: true, httpMode: 'PUT', ignoreSslErrors: true, requestBody: esClusterConf, responseHandle: 'NONE',url: "${esUri}/_cluster/settings" );
  if (response.status != 200) reportError("failed Appling ES Cluster Settings ${esUri}\n\t\tInjected settings: '${esClusterConf}'\n\t\t response: '${response.content}'")

}

// esRegisterRepo(TGT_ES_ENV,TGT_ES_URI,ES_REPOSITORY,DEFUALT_REPO_CONF)
def esRegisterRepo(String esEnv, String esUri, String esSnapshotRepoName, String esRepoConf){
  if (validateJson(esRepoConf)!=true) reportError("failed to validate proper json for desired ES cluster settings");
  def response = httpRequest( consoleLogResponseBody: true, httpMode: 'PUT', ignoreSslErrors: true, requestBody: esRepoConf, responseHandle: 'NONE',url: "${esUri}/_snapshot/${esSnapshotRepoName}" );
  if (response.status != 200) reportError("failed Appling ES Cluster Snapshot Repository for: ${esUri}\n\t\tInjected settings: '${esRepoConf}'\n\t\t response: '${response.content}'")
  
}

def getSrcData(String esEnv, String esUri, String esSnapshotRepoName){
  println("OVERRIDE_SRC_CONF:${OVERRIDE_SRC_CONF}")
  if (OVERRIDE_SRC_CONF==true) {
    println("Invokation initiated with OVERRIDE_SRC_CONF='${OVERRIDE_SRC_CONF}'")
    return;
  }
  def awsRegion = awsGetRegion();
  def response = httpRequest(url: "${esUri}/_snapshot/${esSnapshotRepoName}", consoleLogResponseBody: true);
  if (response.status != 200) reportError("failed snapshot information from cluster: ${esUri}\n${response.content}")
  println("repo ${esSnapshotRepoName} on cluster ${esUri} Validated, \n CONF: ${response.content}")
  println("DEFUALT_REPO_CONF:${DEFUALT_REPO_CONF}")
  def _oldSnapshotRepoDef=jsonParse(response.content);
  _oldSnapshotRepoDef=_oldSnapshotRepoDef[(_oldSnapshotRepoDef.keySet() as String[])[0]];

  def _newRepoConfig="""
  { "type": "${_oldSnapshotRepoDef.type}", "settings": { "bucket": "${_oldSnapshotRepoDef.settings.bucket}", "region": "${_oldSnapshotRepoDef.settings.region}", "base_path": "${_oldSnapshotRepoDef.settings.base_path}", "readonly": "true"}}
  """;
  DEFUALT_REPO_CONF=_newRepoConfig;

  println("Retrived Existing Snapshot Repo as '${_oldSnapshotRepoDef}'");
  println("Drivated Snapshot Repo as '${_newRepoConfig}'");

  // env: ${env}, region: ${region} ,type: ${type},flavour: ${flavour},node_role: ${node_role}
  MAS_NODE_COUNT=getAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',SRC_FLAVIUOR,'master');
  DTA_NODE_COUNT=getAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',SRC_FLAVIUOR,'data');
  CLIENT_NODE_COUNT=getAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',SRC_FLAVIUOR,'client');
  IGRS_NODE_COUNT=getAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',SRC_FLAVIUOR,'ingress');
  println("Retrived from src: DEFUALT_REPO_CONF:${_oldSnapshotRepoDef}")
  println("Retrived from src: MAS_NODE_COUNT=${MAS_NODE_COUNT}")
  println("Retrived from src: DTA_NODE_COUNT=${DTA_NODE_COUNT}")
  println("Retrived from src: CLIENT_NODE_COUNT=${CLIENT_NODE_COUNT}")
  println("Retrived from src: IGRS_NODE_COUNT=${IGRS_NODE_COUNT}")
}


def checkESStatus(String esUri){
  def response = httpRequest("${esUri}/_cluster/health");
  if (response.status != 200) reportError("failed recive Cluster info ${ES_URI}")
  
  def json = jsonParse(response.content);
  // println(JsonOutput.prettyPrint(json));
  if (json.status == "red") reportError("ES CLUSTER IN CRITICAL STATE: One or more primary shards are unassigned, so some data is unavailable. This can occur briefly during cluster startup as primary shards are assigned.")
  if (json.status == "yellow") reportError("ES CLUSTER IN CRITICAL STATE: One or more primary shards are unassigned, so some data is unavailable. This can occur briefly during cluster startup as primary shards are assigned.")
  if (json.status == "green") println("Cluster ${json.cluster_name} (${esUri}), reporting status green")
}

def checkESRepository(String esUri, String esSnapshotRepoName){
  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}");
  if (response.status != 200) reportError("failed snapshot information from cluster: ${esUri}\n${response.content}")
  println("repo ${esSnapshotRepoName} on cluster ${esUri} Validated \n${response.content}")
}

def checkESRepositorySnapshotStatus(String esUri, String esSnapshotRepoName, String esSnapshotName){
  def _esSnapshotName=esSnapshotName;
  if (_esSnapshotName =="" || _esSnapshotName.contains("*")){
    def _ES_SNAPSHOT=getLatestSnapshot(esUri,esSnapshotRepoName,_esSnapshotName);
    _esSnapshotName=_ES_SNAPSHOT;
    ES_SNAPSHOT = _ES_SNAPSHOT;
  }

  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}/${_esSnapshotName}");
  if (response.status != 200) reportError("snapshot ${_esSnapshotName}, repo ${esSnapshotRepoName} on cluster ${esUri}, ${response.content}")
  println("snapshot ${esSnapshotName}, repo ${_esSnapshotName} on cluster ${esUri} Validated \n${response.content}")
}

def checkESRepositorySnapshot(String esUri, String esSnapshotRepoName, String esSnapshotName){
  def _esSnapshotName=esSnapshotName;
  if (_esSnapshotName =="" || _esSnapshotName.contains("*")){
    def _ES_SNAPSHOT=getLatestSnapshot(esUri,esSnapshotRepoName,_esSnapshotName);
    _esSnapshotName=_ES_SNAPSHOT;
    ES_SNAPSHOT = _ES_SNAPSHOT;
  }

  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}/${_esSnapshotName}");
  if (response.status != 200) reportError("snapshot ${_esSnapshotName}, repo ${esSnapshotRepoName} on cluster ${esUri}, ${response.content}")
  println("snapshot ${esSnapshotName}, repo ${_esSnapshotName} on cluster ${esUri} Validated \n${response.content}")
}

def getRepositorySnapshots(String esUri, String esSnapshotRepoName, String esSnapshotPattern){
  
  def _esSnapshotPattern = (esSnapshotPattern == "") ? "*" : "${esSnapshotPattern}" ;

  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}/${_esSnapshotPattern}");
  if (response.status == 404) { reportError("snapshot '${_esSnapshotPattern}' not exists on repo '${esSnapshotRepoName}', cluster: ${esUri}\n${response.content}"); return;}
  if (response.status != 200) { reportError("failed snapshot information from cluster: ${esUri}\n${response.content}"); return; }
  def snapshots = (new groovy.json.JsonSlurper().parseText(response.content)).snapshots;
  snapshots = snapshots.findAll{ it.state=="SUCCESS"}
  snapshots.each(){ it->
    println("#> ${it.getClass()}:=> ${it.snapshot}=>  ${it.keySet()} =>  ");
  }
  def mostRecent = snapshots.max{ a -> a.end_time_in_millis};
  println("#> mostRecent:=> ${mostRecent.snapshot}");
}

def getLatestSnapshot(String esUri, String esSnapshotRepoName, String esSnapshotPattern){
  
  def _esSnapshotPattern = (esSnapshotPattern == "") ? "*" : "${esSnapshotPattern}" ;

  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}/${_esSnapshotPattern}");
  if (response.status == 404) { reportError("snapshot '${_esSnapshotPattern}' not exists on repo '${esSnapshotRepoName}', cluster: ${esUri}\n${response.content}"); return;}
  if (response.status != 200) { reportError("failed snapshot information from cluster: ${esUri}\n${response.content}"); return; }
  def snapshots = (new groovy.json.JsonSlurper().parseText(response.content)).snapshots;
  snapshots = snapshots.findAll{ it.state=="SUCCESS"}
  snapshots.each(){ it->
    println("#> ${it.getClass()}:=> ${it.snapshot}=>  ${it.keySet()} =>  ");
  }
  def mostRecent = snapshots.max{ a -> a.end_time_in_millis};
  println("#> mostRecent:=> ${mostRecent.snapshot}");
  return mostRecent.snapshot;
}

def openIndices(String esUri, String ixNames,boolean ignoreUnavailableIndices = true){

  if (ixNames.contains(',')) {
    ixNames.tokenize(',').each(){ ixName ->
      println("OPENING IX pattern '${ixName}' on cluster: ${esUri} (init)");
      openIndex(esUri,ixName);   
    }
    return;
  }
  println("OPENING IX pattern '${ixNames}' on cluster: ${esUri} (init)");
  openIndex(esUri,ixNames);
  return;
}
def closeIndices(String esUri, String ixNames,boolean ignoreUnavailableIndices = true){

  if (ixNames.contains(',')) {
    ixNames.tokenize(',').each(){ ixName ->
      println("CLOSING IX pattern '${ixName}' on cluster: ${esUri} (init)");
      closeIndex(esUri,ixName);   
    }
    return;
  }
  println("CLOSING IX pattern '${ixNames}' on cluster: ${esUri} (init)");
  closeIndex(esUri,ixNames);
  return;
}
def deleteIndices(String esUri, String ixNames,boolean ignoreUnavailableIndices = true){
  if (ixNames.contains(',')) {
    ixNames.tokenize(',').each(){ ixName ->
      println("DELETING IX pattern '${ixName}' on cluster: ${esUri} (init)");
      deleteIndex(esUri,ixName);   
    }
    return;
  }
  println("DELETING IX pattern '${ixNames}' on cluster: ${esUri} (init)");
  deleteIndex(esUri,ixNames);
  return;
}


def closeIndex(String esUri, String ixName,boolean ignoreUnavailableIndices = true){
  def selectedIndeces = (ixName=="" || ixName=="*") ?  "_all": ixName;
  def snapUriPath = "${esUri}/${selectedIndeces}/_close?ignore_unavailable=${ignoreUnavailableIndices}&wait_for_completion=true";
  def req_boby = "{}";
  alertActionStarted("CLOSING IX pattern ${selectedIndeces} on cluster: ${esUri}");
  def response = httpRequest( consoleLogResponseBody: true, httpMode: 'POST', ignoreSslErrors: true, requestBody: req_boby, responseHandle: 'NONE', url: snapUriPath, wrapAsMultipart: false, validResponseCodes: '100:599' );
  if (response.status == 404){
    alertActionPartial("Failed to Close ES IX pattern '${selectedIndeces}': ${snapUriPath}\n${response.content}");
    return;
  }
  if (response.status != 200) alertActionFailed("Failed to Close ES IX pattern : '${selectedIndeces}'${snapUriPath}\n${response.content}");  
  alertActionPassed("CLOSED IX ${selectedIndeces} on cluster: ${esUri}");
}
def openIndex(String esUri, String ixName,boolean ignoreUnavailableIndices = true){
  def selectedIndeces = (ixName=="" || ixName=="*") ?  "_all": ixName;
  def snapUriPath = "${esUri}/${ixName}/_open?ignore_unavailable=${ignoreUnavailableIndices}&wait_for_completion=true";
  def req_boby = "{}";
  alertActionStarted("OPENINGING IX ${ixName} on cluster: ${snapUriPath}");
  def response = httpRequest( consoleLogResponseBody: true, httpMode: 'POST', ignoreSslErrors: true, requestBody: req_boby, responseHandle: 'NONE', url: snapUriPath, wrapAsMultipart: false, validResponseCodes: '100:599' );
  if (response.status == 404){
    alertActionFailed("Failed to Close ES IX pattern '${ixName}': ${snapUriPath}\n${response.content}");
    return;
  }
  if (response.status != 200) reportError("Failed to Close ES Index : ${snapUriPath}\n${response.content}");  
  alertActionPassed("OPENED IX ${ixName} on cluster: ${esUri}");
}
def deleteIndex(String esUri, String ixName,boolean ignoreUnavailableIndices = true){
  def selectedIndeces = (ixName=="" || ixName=="*") ?  "_all": ixName;
  def snapUriPath = "${esUri}/${selectedIndeces}?ignore_unavailable=${ignoreUnavailableIndices}&wait_for_completion=true";
  def req_boby = "{}";
  alertActionStarted("DELETING IX ${selectedIndeces} on cluster: ${esUri}");
  def response = httpRequest( consoleLogResponseBody: true, httpMode: 'DELETE', ignoreSslErrors: true, requestBody: req_boby, responseHandle: 'NONE', url: snapUriPath, wrapAsMultipart: false, validResponseCodes: '100:599' );
  if (response.status == 404){
    alertActionFailed("Failed to DELETE ES IX pattern '${selectedIndeces}' on: ${snapUriPath}, index pattern not exists\n${response.content}");
    return;
  }
  if (response.status != 200) reportError("Failed to Close ES Index : ${snapUriPath}\n${response.content}"); 
  alertActionPassed("DELETED IX ${selectedIndeces} on cluster: ${esUri}");
}

def restoreIndices(String esUri, String esSnapshotRepoName, String esSnapshotName,String ixName,boolean ignoreUnavailableIndices = true, boolean includeGlobalState = false){
  def selectedIndeces = (ixName?.trim()) ? ixName : "*" ;
  alertActionStarted("RESTORE IX `${selectedIndeces}` on cluster: `${esUri}`, from snapshot `${esSnapshotRepoName}/${esSnapshotName}` DONE");

  def snapUriPath = "${esUri}/_snapshot/${esSnapshotRepoName}/${esSnapshotName}/_restore?wait_for_completion=true";
  def req_boby = "{ \"indices\": \"${selectedIndeces}\",\"ignore_unavailable\": ${ignoreUnavailableIndices},\"include_global_state\": false, \"include_aliases\": true }";
  alertActionStarted("RESTORING IX ${selectedIndeces} on cluster: ${esUri}\n URI:'${snapUriPath}'\nbody:${req_boby}");
  
  def response = httpRequest( consoleLogResponseBody: true, httpMode: 'POST', ignoreSslErrors: true, requestBody: req_boby, responseHandle: 'NONE', url: snapUriPath, wrapAsMultipart: false, validResponseCodes: '100:599' );
  if (response.status == 404){
    alertActionFailed("Failed to RESTORE ES IX pattern '${ixName}' on: ${snapUriPath}, index pattern not exists\n${response.content}");
    return;
  }
  if (response.status != 200) reportError("Failed to RESTORE ES Index : ${snapUriPath}\n${response.content}"); 
  alertActionPassed("RESTORE IX `${ixName}` on cluster: `${esUri}`, from snapshot `${esSnapshotRepoName}/${esSnapshotName}` DONE");

}

def validateTarget(){
    println("validating target...");
}


def awsGetRegion(){
  def response = httpRequest("http://169.254.169.254/latest/meta-data/placement/region");
  if (response.status != 200) reportError("failed to retrive information")
  return "${response.content}";
}

def esAlighnESClusterSize(String esEnv){
  def awsRegion = awsGetRegion();

  alertActionStarted("""
    Initiating temp ES Cluster Provisioning
    ---------------------------------------
    AWS Details:
    \t\t Account: '${'aws_account_id'}', Region: '${awsRegion}' ${TGT_ES_URI} on cluster: ${TGT_ES_URI} ENV: ${env}
    AWS Identifiers (tags): [\"type\": \"elasticsearch\",\"flavour\": \"${TGT_FLAVIUOR}\", \"env\":\"${env}\" ]
    ASG ATTR(s):
    \t\t MAS_NODE_COUNT=${MAS_NODE_COUNT}
    \t\t DTA_NODE_COUNT=${DTA_NODE_COUNT}
    \t\t CLIENT_NODE_COUNT=${CLIENT_NODE_COUNT}
    \t\t IGRS_NODE_COUNT=${IGRS_NODE_COUNT}
  """);
  setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'master',MAS_NODE_COUNT);
  setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'data',DTA_NODE_COUNT);
  setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'client',CLIENT_NODE_COUNT);
  // setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'ingress',IGRS_NODE_COUNT);


}

def shutDownESCluster(String esEnv,boolean ignoreDropAllData = false ){
  def awsRegion = awsGetRegion();
  alertActionStarted("""
    nitiating temp ES Cluster Provisioning
    ---------------------------------------
    AWS Details:
    \t\t Account: '${'aws_account_id'}', Region: '${awsRegion}' ${TGT_ES_URI} on cluster: ${TGT_ES_URI} ENV: ${env}
    AWS Identifiers (tags): [\"type\": \"elasticsearch\",\"flavour\": \"${TGT_FLAVIUOR}\", \"env\":\"${env}\" ]
    ASG ATTR(s):
    \t\t MAS_NODE_COUNT= From '${MAS_NODE_COUNT}' -> 0
    \t\t DTA_NODE_COUNT=From '${DTA_NODE_COUNT}' -> 0
    \t\t CLIENT_NODE_COUNT=From '${CLIENT_NODE_COUNT}' -> 0
    \t\t IGRS_NODE_COUNT=From '${IGRS_NODE_COUNT}' -> 0
  """);
  setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'master','0');
  setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'data','0');
  setAwsASGDesiredCapacityByRole(esEnv,awsRegion,'elasticsearch',TGT_FLAVIUOR,'client','0');
}

def getAwsASGDesiredCapacityByRole(String env,String region, String type,String flavour, String node_role ){
  def curlReq = "aws autoscaling describe-auto-scaling-groups --region ${region}  --query \"AutoScalingGroups[? Tags[? (Key=='Type') && Value=='${type}']] | [? Tags[? Key=='Flavour' && Value =='${flavour}']] | [? Tags[? Key=='node_role' && Value =='${node_role}']].DesiredCapacity\" --output text";
  def rtn = sh (script: curlReq, returnStdout: true).trim();
  println("getAwsASGDesiredCapacityRole(env: ${env}, region: ${region} ,type: ${type},flavour: ${flavour},node_role: ${node_role} )=>${rtn}")
  return rtn;
}

def setAwsASGDesiredCapacityByRole(String env,String region, String type,String flavour, String node_role, String desiredCapacity ){
  def curlReq = "aws autoscaling describe-auto-scaling-groups --region ${region}  --query \"AutoScalingGroups[? Tags[? (Key=='Type') && Value=='${type}']] | [? Tags[? Key=='Flavour' && Value =='${flavour}']] | [? Tags[? Key=='node_role' && Value =='${node_role}']].AutoScalingGroupName\" --output text";
  def asgName = sh (script: curlReq, returnStdout: true).trim();
  println("setAwsASGDesiredCapacityByRole(env: ${env}, region: ${region} ,type: ${type},flavour: ${flavour},node_role: ${node_role} )=>(get ASG Name)${asgName}")
  def scaleRslt = awsScaleAutoScalingGroup(env,region,asgName,desiredCapacity);
  println("setAwsASGDesiredCapacityByRole(env: ${env}, region: ${region} ,type: ${type},flavour: ${flavour},node_role: ${node_role} )=>(awsScaleAutoScalingGroup)${scaleRslt}")

  return scaleRslt;
}

def getAwsASGNameByRole(String env,String region, String type,String flavour, String node_role ){
  def curlReq = "aws autoscaling describe-auto-scaling-groups --region ${region}  --query \"AutoScalingGroups[? Tags[? (Key=='Type') && Value=='${type}']] | [? Tags[? Key=='Flavour' && Value =='${flavour}']] | [? Tags[? Key=='node_role' && Value =='${node_role}']].AutoScalingGroupName\" --output text";
  def _asgs = sh (script: curlReq, returnStdout: true).trim();
}


def awsScaleAutoScalingGroup(String env,String region, String asgName,String desiredCapacity){
  def curlReq = """
  aws autoscaling update-auto-scaling-group --region ${region} --auto-scaling-group-name ${asgName}  --min-size ${desiredCapacity} --max-size ${desiredCapacity} --desired-capacity ${desiredCapacity} --output json
  """;
  def rtn = sh (script: curlReq, returnStdout: true).trim();
  return rtn;
}


def reportError(String msg){
  alertActionFailed(msg);
}

def parseBackupResultDetails(String msg){
  println('parseBackupResultDetails');
  println(msg);
}
def alertActionStarted(String msg){
  println('alertActionStarted');
  sendSlackAlert("#ott-sre-jenkins-successes","RESTORE STARTED", msg, "#3EB991")
}
def alertActionPassed(String msg){
  println('alertActionPassed');
  sendSlackAlert("#ott-sre-jenkins-successes","RESTORE PASSED", msg, "#3EB991")
}
def alertActionPartial(String msg){
  println('alertActionPartial');
  sendSlackAlert("#ott-sre-jenkins-failures","PARTIAL RESTORE ONLY", msg, "#FFC300")
}
def alertActionFailed(String msg, boolean toHalt=true){
  println('alertActionFailed');
  sendSlackAlert("#ott-sre-jenkins-failures","RESTORE FAILED", msg, "#FFC300")
  if(toHalt==true) failPipiline(msg);
}

def sendSlackAlert(String slackChannel,String msgTitle, String msg, String color){
  try{
    println(msg)
    slackSend (
      color: "${color}",
      channel: "${slackChannel}",
      message: """*SRE ES AUTO RESTORE: ${msgTitle}:*
      (<${env.BUILD_URL}|Open>)
      EXCECUTED BY: `${env.BUILD_USER_ID}`,
      ENV: ${TGT_ES_ENV},
      CLUSTER: ${TGT_ES_URI},
      REPOSITORY: ${ES_REPOSITORY},
      SNAPSHOT NAME: ${ES_SNAPSHOT},
      INDICES: ${ES_INDICES}
      result: ```
      ${msg}
      ```
      """
    )
    } catch (e) {
    println(e);
  }
}

def failPipiline(String exception){
  error(exception);
}


def checkESnapshotRestoreStatus(String esUri, String esSnapshotRepoName, String esSnapshotName){

  def snapUri="${esUri}/_snapshot/${esSnapshotRepoName}/${esSnapshotName}";
  def request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
  def _limitor =10;
  while (_limitor != 0 ) {
    if (request.status ==200) break;
    _limitor--;
    if (_limitor == 0 && request.status != 200 ) error("failed to load snapshot info");
    echo "limit:${_limitor}, status: ${request.status}"
    request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
  }

  def snapshot = jsonParse(request.content).snapshots[0];

  println("SNAPSHOT=>'${snapshot}'");
  println("STATE=${snapshot.state}");
  while (snapshot.state=="IN_PROGRESS"){
    println("IN PROGRESS: ${snapshot}...");
    request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
    snapshot = jsonParse(request.content).snapshots[0];
  }
  println("SNAPSHOT=>'${snapshot}'");
  println("STATE=${snapshot.state}");
  def jobName="kaltura.sre.ott.esbackup"
  def promUri = "http://prometheus.service.consul:9091/metrics/job/${jobName}";
  def req_boby = """
  # TYPE some_metric counter
  sre_dataops_es_backup_duration_in_millis{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.duration_in_millis}
  sre_dataops_es_backup_start_time_in_millis{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${ssnapshot.start_time_in_millis}
  sre_dataops_es_backup_end_time_in_millis{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.end_time_in_millis}
  elasticsearch_cluster_backup_shards_total{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.shards.total}
  elasticsearch_cluster_backup_shards_failed{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.shards.failed}
  elasticsearch_cluster_backup_shards_successful{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.shards.successful}
  """
  def response = httpRequest( consoleLogResponseBody: true,
                    httpMode: 'PUT',
                    ignoreSslErrors: true,
                    requestBody: req_boby,
                    responseHandle: 'NONE',
                    url: promUri,
                    wrapAsMultipart: false,
                    validResponseCodes: '100:599' );
  println(response)
}

def extractBackupMetricsFromSnapshot(String esUri, String esSnapshotRepoName, String esSnapshotName){

  def snapUri="${esUri}/_snapshot/${esSnapshotRepoName}/${esSnapshotName}";
  def request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
  def _limitor =10;
  while (_limitor != 0 ) {
    if (request.status ==200) break;
    _limitor--;
    if (_limitor == 0 && request.status != 200 ) error("failed to load snapshot info");
    echo "limit:${_limitor}, status: ${request.status}"
    request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
  }

  def snapshot = jsonParse(request.content).snapshots[0];

  println("SNAPSHOT=>'${snapshot}'");
  println("STATE=${snapshot.state}");
  while (snapshot.state=="IN_PROGRESS"){
    println("IN PROGRESS: ${snapshot}...");
    request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
    snapshot = jsonParse(request.content).snapshots[0];
  }
  println("SNAPSHOT=>'${snapshot}'");
  println("STATE=${snapshot.state}");
  def jobName="kaltura.sre.ott.esbackup"
  def promUri = "http://prometheus.service.consul:9091/metrics/job/${jobName}";
  def req_boby = """
  # TYPE some_metric counter
  sre_dataops_es_backup_duration_in_millis{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.duration_in_millis}
  sre_dataops_es_backup_start_time_in_millis{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${ssnapshot.start_time_in_millis}
  sre_dataops_es_backup_end_time_in_millis{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.end_time_in_millis}
  elasticsearch_cluster_backup_shards_total{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.shards.total}
  elasticsearch_cluster_backup_shards_failed{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.shards.failed}
  elasticsearch_cluster_backup_shards_successful{job='${jobName}', es_snapshot='${esSnapshotName}', es_repo='${esSnapshotRepoName}', cluster='${clusterName}', env='${ES_ENV}', state='${esSnapState}' } ${snapshot.shards.successful}
  """
  def response = httpRequest( consoleLogResponseBody: true,
                    httpMode: 'PUT',
                    ignoreSslErrors: true,
                    requestBody: req_boby,
                    responseHandle: 'NONE',
                    url: promUri,
                    wrapAsMultipart: false,
                    validResponseCodes: '100:599' );
  println(response)
}

def wrappedKbotReport(String system, String componentApplied, String action, String actionResult, String env, String extraNotes, String componentReported){
  kbotReport("${system}\\${componentApplied}","${action}:${actionResult}",env,extraNotes,componentReported)
}
def kbotReport(String component_name,String action, String env, String value, String owner){

  def req = "https://ci.rnd.ott.kaltura.com/notify";
  def xAccessToken="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJBbWl0IiwiaWF0IjoxNjIwNzUyNDQ0LCJleHAiOjE2NTIyODg0NDQsImF1ZCI6ImNpLWFwaSIsInN1YiI6ImNpLWFwaSIsIm5hbWUiOiJhbWl0In0.Uw_tY7KFzqMBhg3M5CchewgF1_MCr6Phxoy8RRfLUbo";
  def req_boby = "{\"component_name\": \"${component_name}\",\"action\": \"${action}\",\"env\": \"${env}\", \"value\": \"${value}\",\"owner\": \"${owner}\"}";

  println(req_boby)
  def response = httpRequest( consoleLogResponseBody: true,
                    httpMode: 'POST',
                    ignoreSslErrors: true,
                    requestBody: req_boby,
                    contentType: 'APPLICATION_JSON',
                    responseHandle: 'NONE',
                    customHeaders: [[name:'x-access-token', value:"${xAccessToken}"]],
                    url: req,
                    wrapAsMultipart: false,
                    validResponseCodes: '100:599' );
  println(response);
}