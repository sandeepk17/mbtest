@Library('mbtest@master') _

pipeline {
  agent{
      // Set Build Agent as Docker file 
      dockerfile true
  }
    environment {
        PROJECT_NAME = "ngraph"
        BRANCH_NAME = "${env.BRANCH_NAME}".replace("/", "%2F")
    }

    stages {
        stage ("Checkout") {
            steps {
                script {
                    def queue = Hudson.instance.queue
                    println "Queue contains ${queue.items.length} items"
                    queue.clear()
                    println "Queue cleared"
                    currentBuild.description = "<b>Branch:</b> ${BRANCH_NAME}<br/>"
                }
            }
        }
        stage ("Parallel CI1") {
            steps {
                echo"--------Testing jobs---------------"
                echo"--------${BRANCH_NAME}---------------"
                //sleep(time:100,unit:"SECONDS")
            }
        }
        stage ("Parallel CI") {
            steps {
                script{
                    def build_res = null
                    build_res = build job: "mbextended/${BRANCH_NAME}"
                    if (build_res.result != "SUCCESS")
                    {
                        color = "red"
                    }
                    else
                    {
                        color = "green"
                    }
                    currentBuild.description += "<b>Commit author:</b> ${currentBuild.number}<br/>"
                    currentBuild.description += '<a href=' + build_res.absoluteUrl +' style="color:' + color + '">'+ "mbextended->No#" + build_res.number + ':' + build_res.result + '</a><br>' + "\n" 
                }
            }
        }
    }
    post {
        success{
            //build job: "mbextended/${BRANCH_NAME}", quietPeriod: 10
            //parameters: [string(name: 'MY_BRANCH_NAME', defaultValue: '${env.BRANCH_NAME}', description: 'pass branch value')],
            echo"-------success-----------" 
        }
        failure {
           echo"--------failing jobs-----------" 
        }
        cleanup {
            echo"--------deleting repo-----------" 
            //deleteDir()
        }
    }
}
