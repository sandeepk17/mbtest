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
                }
            }
        }
        stage ("Parallel CI") {
            steps {
                echo"--------Testing jobs---------------"
                echo"--------${BRANCH_NAME}---------------"
                sleep(time:200,unit:"SECONDS")
            }
        }
    }
    post {
        success{
            build job: "mbextended/${BRANCH_NAME}", quietPeriod: 10
            //parameters: [string(name: 'MY_BRANCH_NAME', defaultValue: '${env.BRANCH_NAME}', description: 'pass branch value')],
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
