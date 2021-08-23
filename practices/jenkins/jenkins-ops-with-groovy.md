# Practice: jenkins operations using Groovy

## excercies goals

the perpuse of the excercise is not only to make you familer with Groovy and Jenkin's SDK. the Idea is that you will be able to formulize the code you wrote here, into imutable (known also as static) functions that you able to add this into your Jenkins Shared libraries. 

## some important notes durring the excercies

* use the java-docs for the jenkins SDK (<https://javadoc.jenkins-ci.org/hudson/model/package-summary.html>)
* remember in Groovy the diffrance between `each` and `collect`
* don't use all the time the `try-catch-finally`, check the that the prompts are valid before attempting to excecute those.

## list all installed plugins

create a groovy code that lists all installed plugins and their version

## list all existing projects\Pipelines\Jobs

create a groovy code that lists all existing projects\jobs, and their last builds

## retrive XML definition of job\project\pipeline

create a method that recives projects name, and extract for you it's XML definition

## creating job\project\pipeline from XML definition

create a method that recives projects name, and XML document. using those parameters, a new job is created.
notes:

* make sure that there is no existing job name
* make sure the XML document is a valid one

## excecute jenkins job\project\pipeline

create a method that recives projects name, and excecutes it immidiatly.
hints:

* [remember the `.scheduleBuild2(0)` method](https://javadoc.jenkins-ci.org/hudson/model/AbstractProject.html#scheduleBuild2-int-)?
* make sure the job does exist before you attempt to excecute it

## trimming old job's build

as you may recall, there is a need to trim from time to time, since it may consume huge ammount of storage. create a groovy function that able to recive a job name, and trims from it's tail a trim for pre-defined number of builds.
hints:

* remember the `.scheduleBuild2(0)` method?
* don't forget to trim from the oldest build to the new

## calculating job success rate

this excersice it a bit comlex, to pay attention to details:

* you will need to create a new jenkins pipeline job, that have unstable result (it uses `new Random()` to generate new int, and if it divides by 2 the job passes, else it fails by thorowing an exception)
* create a code to excecute that job 200 times
* you will beed to calculate the success rate of the job you created, the rate is calculated as ($number success excecutions) * 100 / ($ total excecutions)

hints:

* you will beed to approve this script on your jenkins server, `http://${YOUR JENKINS}:8080/scriptApproval/`, and allow `method java.util.Random next int` excecution
* [groovy operators](https://groovy-lang.org/operators.html)
* 
* the rand function with pipelines (try first alone...):

```groovy
pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                divBy2()
            }
        }
    }
}

def divBy2(){
  def i = new Random().next(10)
  println(i)
  if (i%2!=0){
    throw new Exception("The number ${i} not dividade by 2")
  } else {
    println("all is OK ${i}, dividing by 2...")
  }
}
```

## calculating to error failures reasons

this excersice it a bit comlex, to pay attention to details:

* you will need to create several new jenkins  pipeline jobs, that have unstable result (but now with multiple failure reasons)
* also create additional empty jenkins pipelines jobs
* excecute all  jobs, multiple times
* you will need to write a function to calculate the top 3 exceptions accoures durring the job excecution, and the number of exceptions related to each and every exception

hints:

* you will beed to approve this script on your jenkins server, `http://${YOUR JENKINS}:8080/scriptApproval/`, and allow the excecution
* [groovy operators](https://groovy-lang.org/operators.html)
* 
* the `randException` function with pipelines:

```groovy
pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                randException()
            }
        }
    }
}
def randException(){
  def i = new Random().next(10)
  println(i)
  if (i%2!=0){
    throw new Exception("The number ${i} not dividade by 2")
  } else {
    File file = new File("/path-to-file")  
    FileInputStream fis=new FileInputStream(file); 
    System.out.println("file content: "); 
  }
}

```
## simulating job full lifecycle

now this is the time to test all together:

* create a simple dummy job (either pipeline or traditional job), name it `exec-job`
* write a code the achieves the following:
  * cloning it to following jobe `${origJobName}-clone`

  * excecutes it 200 time
  * trimming it's first 100 excecutions (builds)
  * deleting the job

## users provisioning in jenkins

create 3 new users, with [GUID\UUID](https://www.djamware.com/post/58c4cc3980aca7148020f80e/groovy-generate-uuid-examples) as a password



## exec 1.0: list all installed plugins

## exec 1.0: list all installed plugins


## refactore your code

if you may recall durring the lesson, and also durring the practices, we are using multiple times the same code (get all jobs, users, pluging, nodes and more), perform a refactor in you code, so you will be able to write less code all the time

## aggrigate all your functions and codes

hope you enjoyed those excercises, aggrigate all code you wrote into your 1st jenkins lib (also for your internal code snip lib)


## some hints

```groovy
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
```
