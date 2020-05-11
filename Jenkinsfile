#!/usr/bin/env groovy

import jenkins.model.Jenkins

//import jenkins.model.Jenkins

def killPreviousRunningJobs() {
    def jenkinsQueue = manager.hudson.instance.queue    

    def downstream_jobs = manager.build.getParent().getDownstreamProjects() 

    def downstream_job_name = []
    downstream_jobs.each { job ->
       downstream_job_name.add( job.getFullName()) 
    }   

    downstream_job_name.each { job_name ->
        manager.listener.logger.println ("Downstream project: " + job_name)
        def queue = []
        jenkinsQueue.getItems().each {  queue_item ->
            if ( queue_item.task.getFullName() == job_name ) { 
               queue.add(queue_item)
            }    
        }   

        def queue_list = []
        queue.each { queue_item -> 
               queue_list.add( queue_item.getId()) }    

        if ( queue_list.size() == 0 ) {
            manager.listener.logger.println ("There is no jobs in the queue of: " + job_name )
        } else {
            queue_list.each { queue_id ->
            manager.listener.logger.println ("Cancelling queue item: " + queue_id + " of job: " + job_name )
            jenkinsQueue.doCancelItem(queue_id) 
            }
        }
    }
}
//def killPreviousRunningJobs() {
//    def branchName = env.BRANCH_NAME
//    def buildNo = env.BUILD_NUMBER.toInteger()
//    def q = Jenkins.instance.queue
//    //Find items in queue that match <project name>
//    def queue = q.items.findAll { it.task.name.startsWith(branchName) }
//    //get all jobs id to list
//    def queue_list = []
//    queue.each { queue_list.add(it.getId()) }
//    //sort id's, remove last one - in order to keep the newest job, cancel the rest
//    queue_list.sort().take(queue_list.size() - 1).each { q.doCancelItem(it) }
//}

def notifyByEmail(def gitPrInfo) {
    stage('Notify') {
        String notifyPeople = "${gitPrInfo.prAuthorEmail}, ${gitPrInfo.commitAuthorEmail}"
        emailext (
            subject: "nGraph-Onnx CI: PR ${CHANGE_ID} ${currentBuild.result}!",
            body: """
                <table style="width:100%">
                    <tr><td>Status:</td> <td>${currentBuild.result}</td></tr>
                    <tr><td>Pull Request Title:</td> <td>${CHANGE_TITLE}</td></tr>
                    <tr><td>Pull Request:</td> <td><a href=${CHANGE_URL}>${CHANGE_ID}</a> </td></tr>
                    <tr><td>Branch:</td> <td>${CHANGE_BRANCH}</td></tr>
                    <tr><td>Commit Hash:</td> <td>${gitPrInfo.commitHash}</td></tr>
                    <tr><td>Commit Subject:</td> <td>${gitPrInfo.commitSubject}</td></tr>
                    <tr><td>Jenkins Build:</td> <td> <a href=${RUN_DISPLAY_URL}> ${BUILD_NUMBER} </a> </td></tr>
                </table>
            """,
            to: "${notifyPeople}"
        )
    }
}

def getGitPrInfo(String project) {
    def gitPrInfo = [
        prAuthorEmail : "",
        commitAuthorEmail : "",
        commitHash : "",
        commitSubject : ""
    ]
    try {
        dir ("${WORKDIR}/${project}") {
            gitPrInfo.prAuthorEmail = sh (script: 'git log -1 --pretty="format:%ae" ', returnStdout: true).trim()
            gitPrInfo.commitAuthorEmail = sh (script: 'git log -1 --pretty="format:%ce" ', returnStdout: true).trim()
            gitPrInfo.commitHash = sh (script: 'git log -1 --pretty="format:%H" ', returnStdout: true).trim()
            gitPrInfo.commitSubject = sh (script: 'git log -1 --pretty="format:%s" ', returnStdout: true).trim()
        }
    }
    catch(e) {
        echo "Failed to retrieve ${project} git repository information!"
        echo "ERROR: ${e}"
    }
    return gitPrInfo
}

def checkoutSources() {
    branchExists = sh (script: "git ls-remote --heads ${NGRAPH_ONNX_REPO_ADDRESS} ${NGRAPH_ONNX_BRANCH}",
                        returnStdout: true)
    if(!branchExists) {
        NGRAPH_ONNX_BRANCH = "master"
    }
    sh "rm -rf ${WORKSPACE}/*"
    dir ("${WORKDIR}/ngraph") {
        retry(3) {
            checkout([$class: 'GitSCM',
                branches: [[name: "${NGRAPH_BRANCH}"]],
                doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', timeout: 120]], submoduleCfg: [],
                userRemoteConfigs: [[credentialsId: "${JENKINS_GITHUB_CREDENTIAL_ID}",
                url: "${NGRAPH_REPO_ADDRESS}"]]])
        }
    }
    dir ("${WORKDIR}/ngraph-onnx") {
        retry(3) {
            checkout([$class: 'GitSCM',
                branches: [[name: "${NGRAPH_ONNX_BRANCH}"]],
                doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', timeout: 120]], submoduleCfg: [],
                userRemoteConfigs: [[credentialsId: "${JENKINS_GITHUB_CREDENTIAL_ID}",
                url: "${NGRAPH_ONNX_REPO_ADDRESS}"]]])
        }
    }
}

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
                    def jenkinsQueue = manager.hudson.instance.queue
                    
                    def downstream_jobs = manager.build.getParent().getDownstreamProjects()
                    
                    def downstream_job_name = []
                    downstream_jobs.each { job ->
                       downstream_job_name.add( job.getFullName()) 
                    }
                    
                    downstream_job_name.each { job_name ->
                        manager.listener.logger.println ("Downstream project: " + job_name)
                        def queue = []
                        jenkinsQueue.getItems().each {  queue_item ->
                            if ( queue_item.task.getFullName() == job_name ) { 
                               queue.add(queue_item)
                            }    
                        }
                    
                        def queue_list = []
                        queue.each { queue_item -> 
                               queue_list.add( queue_item.getId()) }
                    
                        if ( queue_list.size() == 0 ) {
                            manager.listener.logger.println ("There is no jobs in the queue of: " + job_name )
                        } else {
                            queue_list.each { queue_id ->
                            manager.listener.logger.println ("Cancelling queue item: " + queue_id + " of job: " + job_name )
                            jenkinsQueue.doCancelItem(queue_id) 
                            }
                        }
                    }
                    manager.listener.logger.println ("")
                    manager.listener.logger.println ("####################################################################")
                    manager.listener.logger.println ("# Groovy script: DONE ")
                    manager.listener.logger.println ("####################################################################")
                    manager.listener.logger.println ("")
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
