import groovy.json.*


pipeline {
  agent none
  options {
    ansiColor('xterm')
    skipDefaultCheckout()
  }
  parameters {
    string defaultValue: 'frt1', description: '', name: 'ES_ENV', trim: true
    string defaultValue: 'http://elasticsearch-client-standby.service.consul:9200', description: '', name: 'ES_URI'
    string defaultValue: 'frt1-es-backups', description: '', name: 'ES_REPOSITORY'
    string defaultValue: 'frt1-es-backups_date', description: '', name: 'ES_SNAPSHOT'
    string defaultValue: '30*', description: '', name: 'ES_INDICES'
    string defaultValue: '7', description: '', name: 'BACKUP_RETENTION_DAYS'
    booleanParam defaultValue: false, description: '', name: 'IGNORE_UNAVAILABLE_INDICES'
  }
  // environment {
  // }
  stages {
    stage('Validating Information') {
      agent {label ES_ENV}
        steps {
          initConfig();
          checkESStatus(ES_URI);
          checkESSnapshotRepository(ES_URI,ES_REPOSITORY);
      }
    }
    
    stage('run backups') {
      parallel {
        stage('run backups') {
          agent {label ES_ENV}
          steps {
            backupIndices(ES_URI,ES_REPOSITORY,ES_SNAPSHOT,ES_INDICES,true, true, true)
          }
        }
        stage('backup track') {
          agent {label ES_ENV}
          steps {
            checkESRepositorySnapshotStatus(ES_URI,ES_REPOSITORY,ES_SNAPSHOT);
          }
        }
      }
    }
    stage('Trimming Old backups') {
      agent {label ES_ENV}
        steps {
          initConfig();
          trimOldestBackups(ES_URI,ES_REPOSITORY,BACKUP_RETENTION_DAYS);
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
def mySleep() {
  echo "Start"
  sleep(5)
  echo "Stop"
}


def initConfig(){
  ES_SNAPSHOT="sre-autosnap"+new Date().format("yyyyMMdd-HHmm");

}

def locateServiceFromConsul(String searchPattern = "elasticsearch-client", String servicePort = '9200', String serviceProt = 'http'){
  def response = httpRequest "http://127.0.0.1:8500/v1/catalog/services"
  if (response.status != 200) reportError("failed recive metadata info from consul discovery service, make sure consul client runs of jenkins node: ${ES_ENV}\n${response.content}")
  
  def json = new groovy.json.JsonSlurper().parseText(response.content);
  def services =json.keySet().findAll{ it.contains(searchPattern)};
  println (services.collect{ it-> return "${serviceProt}://${it}.service.consul:${servicePort}"});
  return (services.collect{ it-> return "${serviceProt}://${it}.service.consul:${servicePort}"});
}

def checkESStatus(String esUri){
  def response = httpRequest("${esUri}/_cluster/health");
  if (response.status != 200) reportError("failed recive info")
  
  def json = jsonParse(response.content);
  // println(JsonOutput.prettyPrint(json));
  ES_CLUSTER_NAME=json.cluster_name;
  if (json.status == "red") reportError("ES CLUSTER IN CRITICAL STATE: One or more primary shards are unassigned, so some data is unavailable. This can occur briefly during cluster startup as primary shards are assigned.")
  if (json.status == "yellow") reportError("ES CLUSTER IN CRITICAL STATE: One or more primary shards are unassigned, so some data is unavailable. This can occur briefly during cluster startup as primary shards are assigned.")
  if (json.status == "green") println("Cluster ${json.cluster_name} (${esUri}), reporting status green")
}

def checkESSnapshotRepository(String esUri, String esSnapshotRepoName){
  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}");
  if (response.status != 200) reportError("failed snapshot information from cluster: ${esUri}\n${response.content}")
  
  def json = jsonParse(response.content);

  println(json)
  println(json.keySet()[0])
  println( json.collect({it.value}))
  def isReadOnly = (json.collect({it.value}).settings)[0].readonly;
  if (isReadOnly == "true") reportError("Snapshot validation failed, snapshot '${esSnapshotRepoName}'on cluster '${esUri}' is read only");
}

def checkESRepositorySnapshotStatus(String esUri, String esSnapshotRepoName, String esSnapshotName){

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
    println("IN PROGRESS: ${snapshot.state}...");
    request = httpRequest(url: snapUri,validResponseCodes: '100:599',ignoreSslErrors: true,consoleLogResponseBody: true);
    snapshot = jsonParse(request.content).snapshots[0];
  }

  // println("STATE=${snapshot.state}");
  def jobName="kaltura_sre_ott_esbackup"
  def promUri = "http://prometheus.service.consul:9091/metrics/job/${jobName}";
  def clusterName= ES_CLUSTER_NAME;
  def esSnapState = snapshot.state;
  def req_boby = """
  # TYPE some_metric counter
  sre_dataops_es_backup_duration_in_millis{job="${jobName}", es_snapshot="${esSnapshotName}", es_repo="${esSnapshotRepoName}", cluster="${clusterName}", env="${ES_ENV}", state="${esSnapState}" } ${snapshot.duration_in_millis}
  sre_dataops_es_backup_start_time_in_millis{job="${jobName}", es_snapshot="${esSnapshotName}", es_repo="${esSnapshotRepoName}", cluster="${clusterName}", env="${ES_ENV}", state="${esSnapState}" } ${snapshot.start_time_in_millis}
  sre_dataops_es_backup_end_time_in_millis{job="${jobName}", es_snapshot="${esSnapshotName}", es_repo="${esSnapshotRepoName}", cluster="${clusterName}", env="${ES_ENV}", state="${esSnapState}" } ${snapshot.end_time_in_millis}
  elasticsearch_cluster_backup_shards_total{job="${jobName}", es_snapshot="${esSnapshotName}", es_repo="${esSnapshotRepoName}", cluster="${clusterName}", env="${ES_ENV}", state="${esSnapState}" } ${snapshot.shards.total}
  elasticsearch_cluster_backup_shards_failed{job="${jobName}", es_snapshot="${esSnapshotName}", es_repo="${esSnapshotRepoName}", cluster="${clusterName}", env="${ES_ENV}", state="${esSnapState}" } ${snapshot.shards.failed}
  elasticsearch_cluster_backup_shards_successful{job="${jobName}", es_snapshot="${esSnapshotName}", es_repo="${esSnapshotRepoName}", cluster="${clusterName}", env="${ES_ENV}", state="${esSnapState}" } ${snapshot.shards.successful}
  """
  println(req_boby);
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

def trimOldestBackups(String esUri, String esSnapshotRepoName, String retentionDays){
  def retentionDaysLong = Integer.parseInt(retentionDays);
  retentionDaysLong = -1*retentionDaysLong;
  def currentEpocTime = Long.valueOf((new Date()).getTime());
  def upperEpochTime = Long.valueOf((new Date().plus(retentionDaysLong)).getTime());

  println "CURRENT EPOCHTIME+>${currentEpocTime}"
  println "upper window EPOCHTIME+>${upperEpochTime}"
  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}/_all");
  if (response.status != 200) reportError("failed snapshot information from cluster: ${esUri}\n${response.content}")
  def snapshot = jsonParse(response.content).snapshots;
  // snapshot.each(){ snap ->    println("#######\n${snap}\n#########")   }

  def snapToDrop = snapshot.findAll{ it.end_time_in_millis <= upperEpochTime  };
  println("Found ${snapToDrop.size()} snapshot to delete, out of ${snapshot.size()} snapshots...")
  snapToDrop.each(){ snap ->
    println("#######PREPERRINT TO DROP ${snap.snapshot} #########...")
    def deleteSnapReq = httpRequest(url:"${esUri}/_snapshot/${esSnapshotRepoName}/${snap.snapshot}?wait_for_completion=true",httpMode:'DELETE',validResponseCodes: '100:599');
    if (deleteSnapReq.status != 200) reportError("snapshot ${snap.snapshot}, repo ${esSnapshotRepoName} on cluster ${esUri}, Failed to delete \n ${deleteSnapReq.content}")
    sendSlackAlert("#ott-sre-jenkins-successes","ES AUTO BACKUP- Snapshot auto trim", "snapshot ${snap.snapshot}, repo ${esSnapshotRepoName} on cluster ${esUri}, Finished to delete \n ${deleteSnapReq.content}", "#3EB991")
  }
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


def checkESRepositorySnapshot(String esUri, String esSnapshotRepoName, String esSnapshotName){
  
  def response = httpRequest("${esUri}/_snapshot/${esSnapshotRepoName}/${esSnapshotName}");
  if (response.status != 200) reportError("snapshot ${esSnapshotName}, repo ${esSnapshotRepoName} on cluster ${esUri}, ${response.content}")
  println("snapshot ${esSnapshotName}, repo ${esSnapshotName} on cluster ${esUri} Validated \n${response.content}")
}

def backupIndices(String esUri, String esSnapshotRepoName, String esSnapshotName,String ixName, boolean ignoreUnavailableIndices = false, boolean includeGlobalState = true, boolean waitForCompletion = true){
  def selectedIndeces = (ixName?.trim()) ? ixName : "*" ;

  def snapUriPath = "${esUri}/_snapshot/${esSnapshotRepoName}/${esSnapshotName}?wait_for_completion=${waitForCompletion}";
  def req_boby = "{ \"indices\": \"${selectedIndeces}\",\"ignore_unavailable\": ${ignoreUnavailableIndices},\"include_global_state\": true }"
  
  println(req_boby);
  println(snapUriPath);
  alertActionStarted("Backup Intitiated");
  def response = httpRequest( consoleLogResponseBody: true,
                    httpMode: 'PUT',
                    ignoreSslErrors: true,
                    requestBody: req_boby,
                    responseHandle: 'NONE',
                    url: snapUriPath,
                    wrapAsMultipart: false,
                    validResponseCodes: '100:599' );

  if (response.status != 200) reportError("Failed to perform ES backup : ${esUri}\n${response.content}")
  
  def json = jsonParse(response.content);

  if (json.status!=null && json.status==500 ){
    alertActionFailed(json);
  }

  if (json.snapshot.state!=null){
    switch(json.snapshot.state){
      case "SUCCESS":
        alertActionPassed(response.content)
        break;
      case "PARTIAL":
        alertActionFailed(response.content);
        break;
      default:
        println("Return Code unsupported"); 
        alertActionFailed(response.content);
        break;  
    }
  }
  //(String component_name,String action, String env, String value, String owner )
  kbotReport("ELASTICSEARCH","BACKUP",ES_ENV, "PASSED", "bilbi.sre@kaltura.com");
  echo "elasticSearch Snapshot FInished: ${json}"
}


def reportError(String msg){
  alertActionFailed(msg);
  error(msg);
}

def parseActionResultDetails(String msg){
  println('parseBackupResultDetails');
  println(msg);
}
def alertActionStarted(String msg){
  println('alertBackupStarted');
  sendSlackAlert("#ott-sre-jenkins-successes","BACKUP STARTED", msg, "#3EB991")
}
def alertActionPassed(String msg){
  println('alertBackupPassed');
  sendSlackAlert("#ott-sre-jenkins-successes","BACKUP PASSED", msg, "#3EB991")
}
def alertActionPartial(String msg){
  println('alertBackupPartial');
  sendSlackAlert("#ott-sre-jenkins-failures","PARTIAL BACKUP ONLY", msg, "#FFC300")
  failPipiline(msg);
}
def alertActionFailed(String msg){
  println('alertBackupPartial');
  sendSlackAlert("#ott-sre-jenkins-failures","BACKUP FAILED", msg, "#FFC300")
  failPipiline(msg);
}

def sendSlackAlert(String slackChannel,String msgTitle, String msg, String color){
  try{
    slackSend (
      color: "${color}",
      channel: "${slackChannel}",
      message: """*SRE ES AUTO BACKUP: ${msgTitle}:*
      (<${env.BUILD_URL}|Open>)
      ENV: ${ES_ENV},
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