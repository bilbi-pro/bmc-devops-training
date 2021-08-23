import org.jenkinsci.plugins.workflow.job.*
import java.util.*

def jobName = "job"
def count = 9
println getBuildsForJob(jobName)
println "############################"
trimBuilds(jobName,count)
println "############################"
println getBuildsForJob(jobName)

println "#######"

  
println "#######"
println "#######"
println "#######"

def getJobByName(String jobName){
	return hudson.model.Hudson.instance.items.find{job -> job.name = jobName }
}


def getLastBuild(String jobName){
	return hudson.model.Hudson.instance.items.find{job -> job.name = jobName }.getLastBuild()
}

def buildJob(String jobName){
	run =  hudson.model.Hudson.instance.items.find{job -> job.name = jobName }.scheduleBuild2(0)
	return run.get()
}

def getBuildsForJob(String jobName){
  return hudson.model.Hudson.instance.items.find{job -> job.name = jobName }.getBuilds()
  
}

def trimBuilds(String jobName, int buildCount){

  builds =  ((List)hudson.model.Hudson.instance.items.find{job -> job.name = jobName }.getBuilds()).subList(0,buildCount)
  
  
  builds.collect{ it-> 
    println("job:${it.project.name}, number:${it.number}, date: ${ it.timestamp.time}")
    it.delete()
  }
  
}