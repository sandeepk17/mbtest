
@NonCPS

def killPreviousRunningJobs() {
    def jobname = env.JOB_NAME
    def buildnum = env.BUILD_NUMBER.toInteger()

    def job = Jenkins.instance.getItemByFullName(jobname)
    for (build in job.builds) {
        if (!build.isBuilding()){
            continue;
        }
        if (buildnum == build.getNumber().toInteger()){
            continue;
        }
        echo "Kill task = ${build}"
        build.doStop();
    }
}

def notifyBuild(String buildStatus = 'STARTED') {
    // build status of null means successful
    buildStatus =  buildStatus ?: 'SUCCESSFUL'

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.BUILD_URL})"
    def details = """<p>STARTED: Job '${env.JOB_NAME} [${env.BUILSD_NUMBER}]':</p>
    <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""

    // Override default values based on build status
    if ((buildStatus == 'STARTED')||(buildStatus == 'DEPLOY TO STAGING?')) {
        color = 'YELLOW'
        colorCode = '#FFFF00'
        echo "ERROR:${details}"
    } else if (buildStatus == 'SUCCESSFUL') {
        color = 'GREEN'
        colorCode = '#00FF00'
        echo "ERROR:${details}"
    } else {
        color = 'RED'
        colorCode = '#FF0000'
        echo "ERROR:${details}"
    }
}

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
        stage('build') {
            steps {
              echo 'This is build A'
              sh 'uname -a'

                script {
                    echo 'This is before checkout scm'
                    env.GIT_COMMIT = checkout scm
                    echo 'This is after checkout scm'
                    currentBuild.getBuildCauses()?.each { c -> echo "[INFO] ${currentBuild.getFullDisplayName()} (current): Cause: ${c}" }
                    currentBuild.getBuildVariables()?.each { k, v -> echo "[INFO] ${currentBuild.getFullDisplayName()} (current): ${k}: ${v}" }
                    echo ''

                    def manualTrigger = true
                    currentBuild.upstreamBuilds?.each { b ->
                        echo "[INFO] Upstream build: ${b.getFullDisplayName()}"
                        b.getBuildCauses()?.each { c ->
                              if (c.endsWith('$SCMTriggerCause')) {
                                echo "[INFO] ${b.getFullDisplayName()}: Cause: ${c}"
                                def build_vars = b.getBuildVariables()
                                if (build_vars) { echo "[INFO] ${build_vars['GIT_COMMIT']}" }
                                echo ''
                              }
                        }
                        manualTrigger = false
                    }
                    if (manualTrigger)
                    {
                        echo "[INFO] This build was triggered manually"
                    }
                }
                notifyBuild(currentBuild.result)
                build job: "mbextended/${BRANCH_NAME}", wait: false, quietPeriod: 5
            }
        }
    }
    //stages {
    //    stage ("Checkout") {
    //        steps {
    //            script {
    //                killPreviousRunningJobs()
    //            }
    //        }
    //    }
    //    stage ("Parallel CI") {
    //        steps {
    //            echo"--------Testing jobs---------------"
    //            echo"--------${BRANCH_NAME}---------------"
    //            //sleep(time:100,unit:"SECONDS")
    //        }
    //    }
    //}
    post {
        success{
            notifyBuild(currentBuild.result)
            build job: "mbextended/${BRANCH_NAME}", quietPeriod: 10
            //parameters: [string(name: 'MY_BRANCH_NAME', defaultValue: '${env.BRANCH_NAME}', description: 'pass branch value')],
            // currentbuild.result
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
