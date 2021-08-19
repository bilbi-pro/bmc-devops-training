import groovy.json.*
import groovy.transform.ToString
import java.util.*

pipeline {
  agent none
  options {
    ansiColor('xterm')
    disableConcurrentBuilds()
    skipDefaultCheckout()
  }
  triggers{
    githubPush()
  }
  parameters {
    string defaultValue: 'frt1', description: 'Kaltura enviroment', name: 'ES_ENV', trim: true
    string defaultValue: 'http://elasticsearch-client-standby.service.consul:9200', description: 'the endpoint to the cluster', name: 'ES_URI'
    string defaultValue: 'frp1-es-backups', description: 'snapshot repository name', name: 'ES_REPOSITORY'
    string defaultValue: '*', description: 'snapshot pattern name', name: 'ES_SNAPSHOT'
    string  defaultValue: '319*,91_*', description: 'index pattern name', name: 'ES_INDICES'
    booleanParam defaultValue: false, description: '', name: 'IGNORE_UNAVAILABLE_INDICES'
  }
  // environment {
  // }
  stages {
    stage('run backups') {
      agent {label ES_ENV}
      steps {
        //locateServiceFromConsul()
        checkESStatus(ES_URI)
        checkESRepository(ES_URI,ES_REPOSITORY);
        checkESRepositorySnapshot(ES_URI,ES_REPOSITORY,ES_SNAPSHOT);
        closeIndices(ES_URI,ES_INDICES);
        deleteIndices(ES_URI,ES_INDICES);
        restoreIndices(ES_URI,ES_REPOSITORY,ES_SNAPSHOT,ES_INDICES);
        // openIndices(ES_URI,ES_INDICES);
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


def locateServiceFromConsul(String searchPattern = "elasticsearch-client", String servicePort = '9200', String serviceProt = 'http'){
  def response = httpRequest "http://127.0.0.1:8500/v1/catalog/services"
  if (response.status != 200) reportError("failed recive metadata info from consul discovery service, make sure consul client runs of jenkins node: ${ES_ENV}\n${response.content}")
  
  def json = new groovy.json.JsonSlurper().parseText(response.content);
  def services =json.keySet().findAll{ it.contains(searchPattern)};
  println (services.collect{ it-> return "${serviceProt}://${it}.service.:${servicePort}"});
  return (services.collect{ it-> return "${serviceProt}://${it}.service.consul:${servicePort}"});
}

def checkESStatus(String esUri){
  def response = httpRequest("${esUri}/_cluster/health");
  if (response.status != 200) reportError("failed recive Cluster info ${ES_URI}")
  
  def json = new groovy.json.JsonSlurper().parseText(response.content);
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


def reportError(String msg){
  error(msg);
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
def alertActionFailed(String msg){
  println('alertActionFailed');
  sendSlackAlert("#ott-sre-jenkins-failures","RESTORE FAILED", msg, "#FFC300")
  failPipiline(msg);
}

def sendSlackAlert(String slackChannel,String msgTitle, String msg, String color){
  try{
    println(msg)
    slackSend (
      color: "${color}",
      channel: "${slackChannel}",
      message: """*SRE ES AUTO BACKUP: ${msgTitle}:*
      (<${env.BUILD_URL}|Open>)
      EXCECUTED BY: `${env.BUILD_USER_ID}`,
      ENV: ${DST_ES_ENV},
      CLUSTER: ${ES_URI},
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