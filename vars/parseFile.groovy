//import jenkins.model.Jenkins
def call(Map stageParams) {
 
    checkout([
        $class: 'GitSCM',
        branches: [[name:  stageParams.branch ]],
        userRemoteConfigs: [[ url: stageParams.url ]]
    ])
}

def killPreviousRunningJobs() {
    def branchName = build.environment.get("GIT_BRANCH_NAME")
    
    def buildNo = build.environment.get("BUILD_NUMBER")
    
    println "checking if need to clean the queue for" + branchName + "  build      number : " + buildNo
    
    def q = Jenkins.instance.queue
    q.items.each { 
        println("${it.task.name}:")
    }    
    q.items.findAll { it.task.name.startsWith(branchName) }.each {
      q.cancel(it.task) 
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