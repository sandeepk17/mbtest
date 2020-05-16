#!/usr/bin/env groovy

def getTimestamp() {
  def timeStamp = sh (
    returnStdout: true,
    script: 'set +x; date +"%y%m%d%H%M"'
  ).trim()
  return timeStamp
}

def getUpStreamInfo(build) {
  Map upstreamInfo = [:]
  if ( 0 == build.size ) {
    upstreamInfo["project"]   = null
    upstreamInfo["fullDesc"]  = null
    upstreamInfo["buildNum"]  = null
  } else {
    Integer upIndex = build.modCount - 1
    upstreamInfo["shortDesc"] = build[upIndex].getBuildCauses()["shortDescription"][0]
    upstreamInfo["buildNum"]  = build[upIndex].getDisplayName()
    upstreamInfo["project"]   = build[upIndex].getFullDisplayName()
    upstreamInfo["fullUrl"]   = build[upIndex].getAbsoluteUrl()
    upstreamInfo["fullDesc"]  = getHTMLLink(build[upIndex].getAbsoluteUrl(), build[upIndex].getFullDisplayName())
  }
  return upstreamInfo
}

def showDesc(String descInfo, String version = null, String testscenario = null) {
  String descStr = "${descInfo}"
  Map upInfo = getUpStreamInfo(currentBuild.upstreamBuilds)

  if (env.NODE_NAME.length() < 30) {
    descStr += " \u25C2 ${env.NODE_NAME}"
  }
  if (upInfo.fullDesc) {
    descStr = upInfo["fullDesc"] + " \u25B8 " + descStr
  }
  if (version) {
    descStr += " \u00AB ${version} "
  }
  if (testscenario) {
    descStr += "\u2219 ${testscenario}"
  }
  currentBuild.description = descStr
}

def showError(String errInfo, String version = null, String testscenario = null) {
  String errStr
  Map upInfo = getUpStreamInfo(currentBuild.upstreamBuilds)
  if (upInfo.fullDesc) {
    errStr= upInfo["fullDesc"] + " \u25B8 ${errInfo} \u25C2 ${env.NODE_NAME}"
  } else {
    errStr = "${errInfo} \u25C2 ${env.NODE_NAME}"
  }
  if (version) {
    errStr += " \u25CF ${version} "
  }
  if (testscenario) {
    errStr += "\u25CA ${testscenario}"
  }
  currentBuild.description = errStr
  error (color.show('brred', errInfo))
}

def getHTMLLink(url, text) {
  return "<a href=\"${url}\" target=\"_blank\">${text}</a>"
}

def getBuildVersion(String patten) {
  String ver = null
  try {
    verFile = findFiles(glob: patten)[0]
    ver = readFile(verFile.path)
  } catch (ArrayIndexOutOfBoundsException e) {
    ver = 'unknow-build-version'
  }
  return ver
}

def getArtifacts(String artiName, String buildJobName, String buildNumber) {
  echo "== copy artifacts from ${buildJobName} build #${buildNumber}"
  copyArtifacts               filter : artiName              ,
                fingerprintArtifacts : true                  ,
                         projectName : buildJobName          ,
                            selector : specific(buildNumber)
}

def regroupArtifacts(String customer, String nand, String name, String curVer, String target){
  ver = getBuildVersion("*build/_out*/**/file_version.txt")
  target = "${ver}/${target}"
  if (findFiles (glob: "**/build/_out*/**/${name}*.suffix")) {
    def tarSt = sh (
      returnStatus: true,
      script: """
        set +x
        for dname in \$(dirname \$(find build -type f -iname "*${customer}*.suffix" -o -iname "*${nand}*.suffix") | uniq); do
          tname=\$(echo \${dname} | awk -F'/' '{print \$--NF}');
          mkdir -p "${target}/\${tname}/Intermediate"
          cp -r \${dname}/* ${target}/\${tname}/
          cp -r \${dname}/../Intermediate ${target}/\${tname}/
        done
      """
    )
    return tarSt
  }
}

def showJUnitReport(String xmlPath) {
  if (findFiles (glob: xmlPath)){
    step([
                 $class : "JUnitResultArchiver",
            testResults : xmlPath,
      allowEmptyResults : false
    ])
    return true
  } else {
    println color.show('brred', "ERROR: Could not find JUnit report at ${xmlPath}")
    return false
  }
}


def setupSSHKey(String dest) {
  def keySt = sh (
    returnStatus: true,
    script: """
      set +x
      cp ~/.ssh/jenkins@devops ${dest}/jenkins@devops
    """
  )
  return keySt
}

def getImageVersion(String dockerFile) {
  def f = readFile(dockerFile).split('\n')
  String ver = ''
  f.each { line ->
    if (line.contains('ARG VERSION=')) {
      ver = line.split('=')[-1]
    }
  }
  return ver
}

def uniqLabel(String label) {
  Date now = new Date()
  return label + '-' + now.format("HHmmss", TimeZone.getTimeZone('America/Los_Angeles'))
}

@NonCPS
def addEnvVarToCurBuild(String key, String value) {
  def curBuild = currentBuild.rawBuild
  def pa = new ParametersAction()
  def actions = curBuild.getActions(hudson.model.ParametersAction)
  if (actions) {
    pa = actions[0]
    curBuild.removeAction(actions[0])
  }
  try {
    def updatedAction = pa.merge( new ParametersAction([ new StringParameterValue(key, value) ]))
    curBuild.addAction(updatedAction)
  } catch(e) {
    if (actions) {
      curBuild.addAction(actions[0])
    }
    throw e
  }
}

@NonCPS
def addEnvVarToCurBuild(Map envVars) {
  def curBuild = currentBuild.rawBuild
  def pa = new ParametersAction()
  def actions = curBuild.getActions(hudson.model.ParametersAction)
  List<StringParameterValue> paramsList = new ArrayList<StringParameterValue>();
  if (actions) {
    pa = actions[0]
    curBuild.removeAction(actions[0])
  }
  try {
    envVars.each { entry ->
        paramsList << new StringParameterValue("${entry.key}", "${entry.value.toString()}")
    }
    def updatedAction = pa.merge(new ParametersAction(paramsList))
    curBuild.addAction(updatedAction)
  } catch(e) {
    if (actions) {
      curBuild.addAction(actions[0])
    }
    throw e
  }
}

// obsolete
def tarArtifacts(String name, String curVer, String target){
  if (findFiles (glob: "**/build/_out*/**/${name}*.suffix")) {
    def tarSt = sh (
      returnStatus: true,
      script: """
        set +x
        tar -C \$(dirname \$(find ${WORKSPACE} -type d -maxdepth 2 -name "${target}")) -czf "${target}-${curVer}".tar.gz ${target}
      """
    )
    return tarSt
  }
}

// obsolete
def prepareArtifacts(String basePath) {
  def prepareStdout = sh (
    returnStdout: true,
    script: """
      set +x
      /bin/ls *.tar.gz | xargs -n1 tar -C ${basePath} -xzf
      mv ${basePath}/tsb_bics4 ${basePath}/tsb
      mv ${basePath}/micron_b27 ${basePath}/micron
      for type in tsb micron; do
        destfolder="${basePath}/\${type}/"
        for suffixbin in '*a*main*.suffix'    \
                      '*b*main*.suffix'      \
                      '*c*.suffix' \
                      '*d*main.suffix'   \
                      '*legacy*.suffix'           \
                      'file_version.txt';
        do
          find "\${destfolder}" -iname "\${suffixbin}" -exec cp {} "\${destfolder}" \\;
        done
        echo "\${destfolder}"
        ls -Altrh "\${destfolder}"
      done
    """
  ).trim()
  println prepareStdout
}

// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************

node{

  	//--- Getting the upstream build number
    echo "Reading the build parent number"
    def manualTrigger = true
  	def upstreamBuilds = ""
  	currentBuild.upstreamBuilds?.each {b ->
    	upstreamBuilds = "${b.getDisplayName()}"
    	manualTrigger = false
  	}
  	def xml_name = "$upstreamBuilds" + ".xml"
  	
  	//--- Reading current job config ---
	echo "Reading the job config"
  	def job_config = readFile "$JENKINS_HOME/jobs/BC-Vareta/builds/QueueJobs/$xml_name"
	def parser = new XmlParser().parseText(job_config)
	def job_name = "${parser.attribute("job")}"
	def build_ID ="${parser.attribute("build")}"
	def owner_name ="${parser.attribute("name")}"
	def notif_email ="${parser.attribute("email")}"
	def eeg = "${parser.attribute("EEG")}"
	def leadfield ="${parser.attribute("LeadField")}"
    def surface ="${parser.attribute("Surface")}"
	def scalp="${parser.attribute("Scalp")}"
	
	//Setting Build description
	def currentBuildName = "BUILD#$build_ID-$owner_name"
	currentBuild.displayName = "$currentBuildName"
	
	stage('DATA ACQUISITION'){
  		
  		 //--- Downloading BC-Vareta code from GitHub reporitory ---
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'github_dbuedo-id', url: 'https://github.com/denysbuedo/BC_VaretaModel.git']]])
  		
  		//--- Creating current matlab workspace
  		sh "mkdir $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName"
  		sh "mkdir $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName/result"
		
		//--- Moving data files to matlab workspace
		sh "cp -a $JENKINS_HOME/jobs/$JOB_NAME/workspace/. $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName"
		sh "mv $JENKINS_HOME/jobs/BC-Vareta/builds/$build_ID/fileParameters/xml_data_descriptor.xml $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName/data"
		sh "mv $JENKINS_HOME/jobs/BC-Vareta/builds/$build_ID/fileParameters/$eeg $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName/data"
		sh "mv $JENKINS_HOME/jobs/BC-Vareta/builds/$build_ID/fileParameters/$leadfield $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName/data"
		sh "mv $JENKINS_HOME/jobs/BC-Vareta/builds/$build_ID/fileParameters/$surface $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName/data"
		sh "mv $JENKINS_HOME/jobs/BC-Vareta/builds/$build_ID/fileParameters/$scalp $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName/data"
		
  		//--- Starting ssh agent on Matlab server ---
		sshagent(['fsf_id_rsa']) {      

			//--- Copying de data file to External_data folder in Matlab Server --- 
			sh 'ssh -o StrictHostKeyChecking=no root@192.168.17.129'
			sh "scp -r $JENKINS_HOME/jobs/$JOB_NAME/builds/$BUILD_ID/$currentBuildName root@192.168.17.129:/root/matlab/"
        } 
        
	}
  
	stage('DATA PROCESSING (BC-Vareta)'){
  		
  		//--- Starting ssh agent on Matlab Server ---
		sshagent(['fsf_id_rsa']) { 
		
			/*--- Goal: Execute the matlab command, package and copy the results in the FTP server and clean the workspace.  
			@file: jenkins.sh
        	@Parameter{
    			$1-action [run, delivery]
        		$2-Name of the person who run the task ($owner_name)
        		$3-EEG file ($eeg)
        		$4-LeadField ($leadfield)
        		$5-Surface ($surface)
        		$6-Scalp ($scalp) 
			} ---*/           
       		echo "--- Run Matlab command ---"
        	sh 'ssh -o StrictHostKeyChecking=no root@192.168.17.129'
			sh "ssh root@192.168.17.129 chmod +x /root/matlab/$currentBuildName/jenkins.sh"
        	sh "ssh root@192.168.17.129 /root/matlab/$currentBuildName/jenkins.sh run $owner_name $eeg $leadfield $surface $scalp $currentBuildName"	
		}
	}
  
	stage('DATA DELIVERY'){
		
		//--- Starting ssh agent on Matlab Server ---
		sshagent(['fsf_id_rsa']) { 
		
			/*--- Goal: Execute the matlab command, package and copy the results in the FTP server and clean the workspace.  
			@file: jenkins.sh
        	@Parameter{
    			$1-action [run, delivery]
        		$2-Name of the person who run the task ($owner_name)
        		$3-EEG file ($eeg)
        		$4-LeadField ($leadfield)
        		$5-Surface ($surface)
        		$6-Scalp ($scalp) 
			} ---*/           
       		echo "--- Tar and copy files result to FTP Server ---"
        	sh 'ssh -o StrictHostKeyChecking=no root@192.168.17.129'
        	sh "ssh root@192.168.17.129 /root/matlab/$currentBuildName/jenkins.sh delivery $owner_name $eeg $leadfield $surface $scalp $currentBuildName"	
		}
	}
  
	stage('NOTIFICATION AND REPORT'){
    	
    	//--- Inserting data in influxdb database ---/
		step([$class: 'InfluxDbPublisher', customData: null, customDataMap: null, customPrefix: null, target: 'influxdb'])
	}

}

// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************

pipeline{
    agent any
    options{
        retry(1)
    }
    
    parameters {
    	// Equivalent to UI interface configuration: This project is parameterized
        string (name: 'DEPLOY_DIR', defaultValue: '/ app / rpcusr / auto_deploy', description: 'Directory to execute the pull script on the deployment server')
        // string (name: 'gitl_url', description: 'git project address--')
    }
    
    environment {
    	// Custom configuration
    	gitl_url = "$gitl_url"
        gitl_user = "$gitl_user"
        gitl_branch = "$gitl_branch"
        build_child_dir = "$build_child_dir"
    
		// other
        workspace = pwd()
        codedir = "code"
        mvnHome = "$MAVEN_HOME"
        jdkHome = "$JAVA_HOME"
        
        PATH = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"
    }
    
    post {
          // write some post code
        always {
            script {
                echo "post:always-------------"
            }
        }
        changed {
            script {
                echo "post: changed ------------- Has the status changed?"
            }
        }
        failure {
            script {
                echo "post: failure ------------- Failed? Send email?"
            }
        }
        success {
            script {
                echo "post: success ------------- Successful? Send email?"
            }
        }
    }
    
    triggers {
    	// The spring-boot-child task is triggered when the task is successfully built, which is equivalent to the UI interface configuration: Build after other projects are built
    	upstream(upstreamProjects: 'spring-boot-child', threshold: hudson.model.Result.SUCCESS)
    }
    
    stages{
        stage('HookConfig') { 
            steps{
                script{
                	//if(!githook_object_kind.trim().isEmpty() && githook_object_kind.trim() != 'null'){
					if (env.githook_object_kind != null && !env.githook_object_kind.trim().isEmpty()) {
                		echo "This build is triggered by a hook"
                		
                		// gitlabhook related configuration information, depends on Generic Webhook Trigger plugin configuration Post content parameters [Variable: githook, Expression: $]
				        githook_ref = "$githook_ref" // refs/heads/master
				        githook_repository_name = "$githook_repository_name"	// spring-boot-parent-hzw
				        githook_object_kind = "$githook_object_kind"
				        
                	    // Check whether the project name triggered by the hook matches the git path of the job target
	                    if (!gitl_url.matches(".*/" + githook_repository_name + ".git")){
	                        error "The target item $ {githook_repository_name} cannot match the target address $ {gitl_url}"
	                    }
	                    
	                    def ref_list = githook_ref.tokenize('/')
	                    def ref_type = ref_list[1]	// heads、tags
	                    def ref_name = ref_list[2]	// master、v0.1
	                    
	                    // Set the branch name in the hook
	                    gitl_branch = ref_name
                	}else{
                		echo "This build is not triggered by hook"
                	}
                }
            }
        }
        
        stage('InitConfig'){
        	steps{
        		script{
        			echo "Initialize build parameters"
        		}
        	}
        }
        
        stage('CodeCheck') {
            steps{
                // git branch: "master", credentialsId: 'd87b6196-bae0-4195-88d6-7f85d76f52b1', url: 'http://172.27.0.2/gitlabtest/spring-boot-parent-hzw.git'
                script{
                    url="${gitl_url}"
                    branch = "$ {gitl_branch}" // The target branch can also be a tag number
                    credentialsId="${gitl_user}"
                    
                    echo "=checkout url:${url} branch:${branch}="
                }
             
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: "$branch"]], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [
                        [$class: 'CleanBeforeCheckout'], 
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "$codedir"], 
                        [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', timeout: 3, trackingSubmodules: false]
                    ], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [
                        [credentialsId: "$credentialsId", url: "$url"]
                    ]
                ]) 
            }
        }
        
        stage('Build') {
            steps{
            	script{
	            	echo "Start building ..."
					builddir="${workspace}/${codedir}"
					
					if (env.build_child_dir != null && !env.build_child_dir.trim().isEmpty() && env.build_child_dir.trim()!='null') {
						echo "Build subdirectory configured: $ {build_child_dir}"
						builddir="${builddir}/${build_child_dir}"
					}
					
					if (env.build_mvn_opt != null && !env.build_mvn_opt.trim().isEmpty() && env.build_mvn_opt.trim()!='null') {
						echo "Build instruction configured: $ {build_mvn_opt}"
						build_mvn_opt="${build_mvn_opt}"
					}else{
						echo "The build command is not configured, use the default command: clean install"
						build_mvn_opt="clean install"
					}
					
					echo "Build directory: $ {builddir}, build instruction: $ {build_mvn_opt}"
					
					echo "-----------${currentBuild.fullDisplayName}"
            	}
				
            	// maven build generates binary packages
                dir("${builddir}"){
            		// The Jenkins plugin is required here: Pipeline Maven Integration
            		withMaven() {
                		//sh "mvn clean deploy -Dmaven.test.skip=true"
                		
                		sh "mvn ${build_mvn_opt}"
        			}
        			
        			
                }
                
            }
        }
        
        stage('Deploy-test') {
            steps{
            	script{
            		echo "12312312"
            		// TODO obtains the compiled output list information
            		
            		//echo "==============================="
            		//
            		//echo "==============================="
            		echo sh(returnStdout: true, script: 'env')
            		//echo "==============================="
            		//echo "currentBuild.number=${currentBuild.number}"
					//echo "currentBuild.result=${currentBuild.result}"
					//echo "currentBuild.currentResult=${currentBuild.currentResult}"
					////echo "currentBuild.resultIsBetterOrEqualTo(String)=${currentBuild.resultIsBetterOrEqualTo(String)}"
					////echo "currentBuild.resultIsWorseOrEqualTo(String)=${currentBuild.resultIsWorseOrEqualTo(String)}"
					//echo "currentBuild.displayName=${currentBuild.displayName}"
					//echo "currentBuild.fullDisplayName=${currentBuild.fullDisplayName}"
					//echo "currentBuild.projectName=${currentBuild.projectName}"
					//echo "currentBuild.fullProjectName=${currentBuild.fullProjectName}"
					//echo "currentBuild.description=${currentBuild.description}"
					//echo "currentBuild.id=${currentBuild.id}"
					//echo "currentBuild.timeInMillis=${currentBuild.timeInMillis}"
					//echo "currentBuild.startTimeInMillis=${currentBuild.startTimeInMillis}"
					//echo "currentBuild.duration=${currentBuild.duration}"
					//echo "currentBuild.durationString=${currentBuild.durationString}"
					//echo "currentBuild.previousBuild=${currentBuild.previousBuild}"
					//echo "currentBuild.previousBuildInProgress=${currentBuild.previousBuildInProgress}"
					//echo "currentBuild.previousBuiltBuild=${currentBuild.previousBuiltBuild}"
					//echo "currentBuild.previousCompletedBuild=${currentBuild.previousCompletedBuild}"
					//echo "currentBuild.previousFailedBuild=${currentBuild.previousFailedBuild}"
					//echo "currentBuild.previousNotFailedBuild=${currentBuild.previousNotFailedBuild}"
					//echo "currentBuild.previousSuccessfulBuild=${currentBuild.previousSuccessfulBuild}"
					//echo "currentBuild.nextBuild=${currentBuild.nextBuild}"
					//echo "currentBuild.absoluteUrl=${currentBuild.absoluteUrl}"
					//echo "currentBuild.buildVariables=${currentBuild.buildVariables}"
					//echo "currentBuild.changeSets=${currentBuild.changeSets}"
					//echo "currentBuild.upstreamBuilds=${currentBuild.upstreamBuilds}"
					////echo "currentBuild.rawBuild=${currentBuild.rawBuild}"
					//echo "currentBuild.keepLog=${currentBuild.keepLog}"
            	}
            	
            	
            	
            	
            	sh label: '', script: 'echo $JAVA_HOME'
            }
        }
        
        stage('Utility Steps method') {
            steps {
                script {
                	jobsdir="${JENKINS_HOME}/jobs/${currentBuild.projectName}/builds/${currentBuild.id}/archive"
                	dir ("$ {jobsdir}") {
	                    def files = findFiles(glob: '**/*.jar')
	                    
	                    def archiveMsg=""
	                    
	                    for(jarfile in files){
	                    	if(jarfile.path.endsWith("sources.jar")) continue
	                    	def pomfile=jarfile.path.substring(0, jarfile.path.lastIndexOf(".")) + ".pom"
	                    	archiveMsg += sh(script: "echo 'jenkins \${project.groupId}    \${project.artifactId}    \${project.version}'| mvn -DforceStdout help:evaluate -f ${jobsdir}/${pomfile} | grep 'jenkins' |sed 's/jenkins\\s*//g'",returnStdout: true).trim() + "\n"
	                    	//archiveMsg += sh(script: "echo '\${project.groupId}    \${project.artifactId}    \${project.version}'| mvn -q -DforceStdout help:evaluate -f ${jobsdir}/${pomfile}",returnStdout: true).trim() + "\n"
	                    	//archiveMsg += sh(script: "echo '\${project.groupId}    \${project.artifactId}    \${project.version}'| mvn -q -DforceStdout org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -f ${jobsdir}/${pomfile}",returnStdout: true).trim() + "\n"
	                    	
	                    	echo "${archiveMsg}"
	                    }

						// TODO filters out the id to be pulled
						
						

	                    echo "=========="
	                    echo "${archiveMsg}"
	                    echo "=========="
	                    
	                    def remote = [:]
	                    remote.name='rpcdev2xxx'
	                    remote.host='10.250.20.145'
	                    remote.user='rpcusr'
	                    remote.password='rpcusr'
	                    remote.allowAnyHosts= true
	                    //remote.pty=true
	                    
	                    //writeFile file:"deploy-${currentBuild.projectName}.sh", text: "#!/bin/bash\n if [ -f ~/.bash_profile ]; then \n source ~/.bash_profile \n fi \n cd ${DEPLOY_DIR} \n echo \"====111\${APPHOME}\"111"
	                    //writeFile file:"deploy-${currentBuild.projectName}.sh", text: "cd ${DEPLOY_DIR} \n echo \"${archiveMsg}\" > pcklist.temp \n bash pkJenkins.sh -f pcklist.temp \n sleep 1 \n rm pcklist.temp"
	                    // Add the declaration #! / Bin / bash --login to the file header generated here to ensure the integrity of the environment variables when executing the script
	                    writeFile file:"deploy-${currentBuild.projectName}.sh", text: "#!/bin/bash --login\n cd ${DEPLOY_DIR} \n echo \"${archiveMsg}\" > jenkinslist\$\$.temp \n bash pkJenkins.sh -f jenkinslist\$\$.temp \n rm jenkinslist\$\$.temp"
	                    //writeFile file:"deploy-${currentBuild.projectName}.sh", text: "#!/bin/bash\n if [ -f ~/.bash_profile ]; then \n source ~/.bash_profile \n fi \n cd ${DEPLOY_DIR} \n echo \"${archiveMsg}\" > pcklist.temp \n sh pkJenkins.sh -f pcklist.temp \n sleep 1 \n rm pcklist.temp"
	                    
	                    //sshScript remote: remote,script: "deploy-${currentBuild.projectName}.sh"
	                    
	                }
                }
            }
        }

    }
}


// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
// ***********************************************************************************
import java.nio.file.Paths

def jobName = "<%= job_name %>"
def jobPackageName = "<%= package_name %>"
def upstreamJobNames = [<%= upstream_jobs.each_key.map { |job_name| "'#{job_name}'" }.join(", ") %>]
def upstreamPackageNames = [<%= upstream_jobs.each_value.map { |package_name| "'#{package_name}'" }.join(", ") %>]
<%= render_template('library.pipeline') %>
def triggeredByUpstream = false
def upstreamBuilds = []

stage('waiting for upstream jobs to finish') {
    def triggerBuild    = getTriggerBuild(currentBuild)
    if (triggerBuild) {
        triggeredByUpstream = true;
    }
    else {
        triggeredByUpstream = false;
        triggerBuild = [null, null];
    }

    upstreamBuilds = getUpstreamBuilds(upstreamJobNames, triggerBuild[0], triggerBuild[1])
    if (upstreamBuilds == null)
    {
        currentBuild.result = 'NOT_BUILT';
        return;
    }
    if (!waitForUpstreamBuilds(upstreamBuilds)) {
        currentBuild.result = 'NOT_BUILT';
        return
    }
}

if (currentBuild.result == 'NOT_BUILT')
{
    return;
}

node(label: 'autoproj-jenkins') {
    def fullWorkspaceDir = pwd()
    def autoproj = "${fullWorkspaceDir}/dev/.autoproj/bin/autoproj"
    dir('dev/install/log') { deleteDir() }

    def jobPackagePrefix = null
    def upstreamPackagePrefixes = null

    stage('bootstrap') {
        <%= render_template('bootstrap.pipeline', seed: seed, poll: false, vcs: buildconf_vcs, gemfile: gemfile, autoproj_install_path: autoproj_install_path, vcs_credentials: vcs_credentials, indent: 4) %>

        def jenkins_dependency_overrides = "<%= render_template 'jenkins_dependency_overrides.rb', escape: true, package_name: package_name, upstream_jobs: upstream_jobs %>"
        writeFile file: 'dev/autoproj/overrides.d/99_jenkins_dependency_overrides.rb',
            text: jenkins_dependency_overrides

        def packagePrefixes = sh(script: "${autoproj} locate --no-cache --prefix '${jobPackageName}' ${upstreamPackageNames.join(" ")}", returnStdout: true).
            split("\n")

        jobPackagePrefix        = packagePrefixes[0]
        upstreamPackagePrefixes = packagePrefixes.tail()
    }

    stage('install upstream artifacts') {
        installUpstreamArtifacts(autoproj, fullWorkspaceDir,
            jobPackageName, jobPackagePrefix,
            upstreamJobNames, upstreamPackagePrefixes, upstreamBuilds)
    }

    dir('dev') {
        stage('prepare build') {
            <%= render_template("import-#{vcs.type}.pipeline",
                                poll: true,
                                patch: true,
                                allow_unused: true,
                                package_dir: package_dir,
                                vcs: vcs,
                                credentials: vcs_credentials.for(vcs),
                                package_name: package_name,
                                indent: 8) %>
            sh "${autoproj} test disable '<%= package_name %>'"
            sh "${autoproj} osdeps '<%= package_name %>' 'pkg-config'"
        }

        stage('build') {
            try {
                sh "${autoproj} build --force --deps=f '<%= package_name %>' -p1"
            }
            catch(err) {
                archive includes: 'install/<%= package_name %>/log/<%= package_name %>-*.log'
                archive includes: 'install/log/autoproj-osdeps.log'
                throw(err)
            }
            archive includes: 'install/<%= package_name %>/log/<%= package_name %>-*.log'
            archive includes: 'install/log/autoproj-osdeps.log'
        }
    }

    stage('handle downstream') {
        handleDownstream(autoproj, fullWorkspaceDir,
            jobName, jobPackagePrefix, "<%= artifact_glob %>")
        if (! triggeredByUpstream) {
            <% downstream_jobs.each_key do |job_name| %>
            build job: "<%= job_name %>", wait: false
            <% end %>
        }
    }

    stage('tests') {
        def test_timestamp_path = "${fullWorkspaceDir}/test-timestamp"
        touch file: test_timestamp_path
        def test_output_path    = "${fullWorkspaceDir}/test"
        def autoproj_test_failed = false
        dir('dev')
        {
            try {
                sh "${autoproj} test enable '<%= package_name %>'"
                sh "${autoproj} osdeps '<%= package_name %>'"
                sh "${autoproj} build --deps=f '<%= package_name %>' -p1"
                sh "${autoproj} test -p=1 '<%= package_name %>'"
            }
            catch(err) { autoproj_test_failed = true }

            try { sh "${autoproj} jenkins postprocess-tests --after=${test_timestamp_path} ${test_output_path} '<%= package_name %>'" }
            catch(err) { autoproj_test_failed = true }
        }
        try { junit allowEmptyResults: true, keepLongStdio: true, testResults: "test/*.xml" }
        catch(err) { autoproj_test_failed = true }

        if (autoproj_test_failed)
        {
            currentBuild.result = 'UNSTABLE'
        }
    }

    // Move the current package prefix to a separate folder, to ensure that
    // other workspaces don't have access to it. It's not strictly required,
    // but is a good sanity check
    dir('lastPrefix') { deleteDir() }
    sh "mv '${jobPackagePrefix}' lastPrefix"
}




//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
@NonCPS
def isUpstreamOK(jobName, buildId)
{
    def job = Jenkins.instance.getItem(jobName)
    if (!job)
    {
        error("cannot find upstream job ${jobName}")
    }

    def build = job.getBuild(buildId.toString())
    if (!build)
    {
        error("cannot find build ${buildId} of job ${jobName}")
    }

    def result = build.getResult()
    if (result)
    {
        if (result == Result.SUCCESS || result == Result.UNSTABLE)
        {
            return 'OK'
        }
        else
        {
            return 'FAILED'
        }
    }
    else
    {
        return 'IN_PROGRESS'
    }
}

@NonCPS
def getTriggerBuild(currentBuild)
{
    def triggerBuild = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
    if (triggerBuild) {
        return [triggerBuild.getUpstreamProject(), triggerBuild.getUpstreamBuild()]
    }
    return null
}

@NonCPS
def findBuildTriggeredBy(job, triggerJob, triggerBuild)
{
    def jobBuilds = job.getBuilds()
    for (buildIndex = 0; buildIndex < jobBuilds.size(); ++buildIndex)
    {
        def build = jobBuilds[buildIndex]
        def buildCause = build.getCause(hudson.model.Cause$UpstreamCause)
        if (buildCause)
        {
            def causeJob   = buildCause.getUpstreamProject()
            def causeBuild = buildCause.getUpstreamBuild()
            if (causeJob == triggerJob && causeBuild == triggerBuild)
            {
                return build.getNumber()
            }
        }
    }
    return null
}

def getUpstreamBuilds(upstreamJobNames, triggerJob, triggerBuild)
{
    def upstreamBuilds = []

    // Iterate list -- NOTE: we cannot use groovy style or even modern java style iteration
    for (jobIndex = 0; jobIndex < upstreamJobNames.size(); ++jobIndex)
    {
        def jobName = upstreamJobNames[jobIndex]
        if (jobName == triggerJob)
        {
            echo "upstream build: ${jobName}#${triggerBuild}"
            upstreamBuilds << [jobName, triggerBuild]
        }
        else
        {
            def job = Jenkins.instance.getItem(jobName)
            if (!job)
            {
                echo "${jobName} does not exist yet, aborting"
                return null
            }

            def matchingBuild = findBuildTriggeredBy(job, triggerJob, triggerBuild)
            if (!matchingBuild)
            {
                if (triggerJob) {
                    echo "no build from ${jobName} has been triggered by ${triggerJob}#${triggerBuild}, using last successful build"
                }
                else {
                    echo "manual build trigger, using last successful build for ${jobName}"
                }
                if (job.getLastSuccessfulBuild())
                    matchingBuild = job.getLastSuccessfulBuild().getNumber()
                else
                {
                    echo "${jobName} has no successful build, aborting"
                    return null
                }
            }
            echo "upstream build: ${jobName}#${matchingBuild}"
            upstreamBuilds << [jobName, matchingBuild]
        }
    }
    return upstreamBuilds
}

def waitForUpstreamBuilds(upstreamBuilds)
{
    // Iterate list -- NOTE: we cannot use groovy style or even modern java style iteration
    for (upstreamBuildIndex = 0; upstreamBuildIndex < upstreamBuilds.size(); ++upstreamBuildIndex)
    {
        def entry = upstreamBuilds[upstreamBuildIndex]
        def upstreamJobName = entry[0]
        def upstreamBuildId = entry[1]
        while (true)
        {
            def status = isUpstreamOK(upstreamJobName, upstreamBuildId)
            if (status == 'OK')
            {
                break
            }
            else if (status == 'IN_PROGRESS')
            {
                echo "waiting for job ${upstreamJobName}#${upstreamBuildId} to finish"
                sleep 10
            }
            else if (status == 'FAILED')
            {
                echo "${upstreamJobName}#${upstreamBuildId} did not finish successfully, aborting this build"
                return false
            }
        }
    }
    return true
}

def makeUpstreamArtifactImporters(autoproj, fullWorkspaceDir, upstreamDir,
    upstreamJobNames, upstreamPrefixes, upstreamBuilds)
{
    def fullUpstreamDir = "${fullWorkspaceDir}/${upstreamDir}"
    dir(upstreamDir) { deleteDir() }

    def upstreamArtifactImporters = [:]
    for (jobIndex = 0; jobIndex < upstreamJobNames.size(); ++jobIndex)
    {
        def jobName        = upstreamJobNames[jobIndex]
        def fullPrefix     = upstreamPrefixes[jobIndex]
        def buildId        = upstreamBuilds[jobIndex][1]
        def relativePrefix = Paths.get(fullWorkspaceDir).relativize(Paths.get(fullPrefix)).toString()
        upstreamArtifactImporters[jobName] = {
            dir(upstreamDir) {
                step ([$class: 'CopyArtifact',
                    projectName: jobName,
                    filter: "${jobName}-prefix.zip",
                    selector: [$class: 'SpecificBuildSelector', buildNumber: buildId.toString()]])
                dir(jobName) {
                    unzip zipFile: "${fullUpstreamDir}/${jobName}-prefix.zip"
                    sh "${autoproj} jenkins relativize ./ '@WORKSPACE_ROOT@' '${fullWorkspaceDir}'"
                }
            }
            dir(relativePrefix) {
                sh "rsync '${fullUpstreamDir}/${jobName}/' './' --delete --recursive --safe-links --perms --checksum"
            }
        }
    }

    return upstreamArtifactImporters
}

def installUpstreamArtifacts(autoproj, fullWorkspaceDir,
        jobPackageName, jobPackagePrefix,
        upstreamJobNames, upstreamPackagePrefixes, upstreamBuilds)
{
    def upstreamDir = "artifacts/upstream"
    parallel(makeUpstreamArtifactImporters(
            autoproj, fullWorkspaceDir, upstreamDir,
            upstreamJobNames, upstreamPackagePrefixes, upstreamBuilds)
    )
    // We don't need the upstream artifacts anymore, clear some disk space
    dir(upstreamDir) { deleteDir() }

    if (fileExists("lastPrefix")) {
        sh "mv lastPrefix '${jobPackagePrefix}'"
    }
    return null
}

def handleDownstream(autoproj, fullWorkspaceDir,
        jobName, jobPackagePrefix, artifactGlob)
{
    def downstreamDir = "artifacts/downstream"
    def targetArtifactPath = "${fullWorkspaceDir}/${downstreamDir}/${jobName}-prefix.zip"

    dir(downstreamDir) { deleteDir() }
    dir("${downstreamDir}/${jobName}") {
        sh "rsync '${jobPackagePrefix}/' './' -a --delete"
        sh "${autoproj} jenkins relativize ./ '${fullWorkspaceDir}' '@WORKSPACE_ROOT@'"
        zip zipFile: targetArtifactPath, glob: artifactGlob
    }
    dir(downstreamDir) {
        archiveArtifacts artifacts: "*.zip"
        deleteDir()
    }
    return null
}


def triggerDownstreamJobs(jobNames) {
    for (jobIndex = 0; jobIndex < jobNames.size(); ++jobIndex)
    {
        build job: jobNames[jobIndex], wait: false
    }
    return null
}



//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
//**************************************************************************************************************
import jenkins.model.Jenkins

import  hudson.*

import hudson.model.*

import jenkins.*

//Remove everything which is currently queued

Jenkins.instance.queue.clear()

def buildingJobs = Jenkins.instance.getAllItems(Job.class).findAll

{

    it.isBuilding()

}


buildingJobs.each{


    def jobName = it.toString()

    def val = jobName.split("\\[|\\]")

    //'Abort jobs' is the name of the job I have created, and I do not want it to abort itself.

    if((val[1].trim())!='Abort jobs'){

        def job = Jenkins.instance.getItemByFullName(val[1].trim())

        for (build in job.builds) {

            if (build.isBuilding()){

                println(build)

                build.doStop();

            }


        }

    }

}




//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************



t also aborts running jobs 
when there is a job in the queue with the same cause (what you see when you hover over the job in the build queue, 
only looking at the first line). I’m running this as a job with build step “Execute System Groovy Script”.

import hudson.model.Result
import jenkins.model.CauseOfInterruption
import jenkins.model.*;

[ // setup job names here
'my-jobname-here'   
].each {jobName ->  
  def queue = Jenkins.instance.queue  
  def q = queue.items.findAll { it.task.name.equals(jobName) }  
  def r = [:]  
  def projs = jenkins.model.Jenkins.instance.items.findAll { it.name.equals(jobName) }  

  projs.each{p ->  
    x = p._getRuns()  
    x.each{id, y ->  
      r.put(id, y)  
    }  
  }  

  TreeMap queuedMap = [:]  
  TreeMap executingMap = [:]  

  q.each{i->  
    queuedMap.put(i.getId(), i.getCauses()[0].getShortDescription()) //first line  
  }  
  r.each{id, run->  
    def exec = run.getExecutor()  
    if(exec != null){  
      executingMap.put(id, run.getCauses()[0].getShortDescription()) //first line  
    }  
  }  

  println("Queued:")  
  queuedMap.each{ k, v -> println "${k}:${v}" }  
  println("Executing:")  
  executingMap.each{ k, v -> println "${k}:${v}" }  

  // First, if there is more than one queued entry, cancel all but the highest one.  
  // Afterwards, if there is a queued entry, cancel the running ones  

  def queuedNames = queuedMap.values();  
  queuedNames.each{n ->  
    def idsForName = []  
    queuedMap.each{ id, name ->  
      if(name.equals(n)){  
        idsForName.add(id)  
      }  
    }  
    if (idsForName.size() > 1){  
      println("Cancelling queued job: "+n)  
    }  
    // remove all but the latest from queue  
    idsForName.sort().take(idsForName.size() - 1).each { queue.doCancelItem(it) }  
  }  
  executingMap.each{ id, name ->  
    if(queuedMap.values().contains(name)){  
      r.each{rid, run->  
        if (id == rid){  
          def exec = run.getExecutor()  
          if(exec != null){  
            println("Aborting running job: "+id+": "+name)  
            exec.interrupt(Result.ABORTED)  
          }  
        }  
      }  
    }  
  }  
}  
return "Done"




def q = Jenkins.instance.queue
//Find items in queue that match <project name>
def queue = q.items.findAll { it.task.name.startsWith('sample_project') }
//get all jobs id to list
def queue_list = []
queue.each { queue_list.add(it.getId()) }
//sort id's, remove last one - in order to keep the newest job, cancel the rest
queue_list.sort().take(queue_list.size() - 1).each { q.doCancelItem(it) }

//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************


pipeline {
  agent any
  stages {
    stage('BeginProcess') {
      steps {
        parallel(
          "BeginProcess": {
            echo 'Building Neo4jAccountLibrary'
            script {
              echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Started the pipeline (<${env.BUILD_URL}|Open>)"
              slackSend color: '#cecece', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Started the pipeline (<${env.BUILD_URL}|Open>)"
            }
          },
          "Delete old build": {
            sh 'rm -rf dockerbuild/'
          }
        )
      }
    }
    stage('Build') {
      steps {
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Compiling Spring application"
          slackSend color: '#3e6be8', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Compiling Spring application"
        }
        
        sh 'chmod 0755 ./gradlew;./gradlew clean build --refresh-dependencies'
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Compiled Spring application"
          slackSend color: '#42e565', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Compiled Spring application"
        }
        
      }
    }
    stage('Docker Build') {
      steps {
        parallel(
          "Build Docker Image": {
            script {
              echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Building Docker image"
              slackSend color: '#3e6be8', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Building Docker image"
            }
            
            sh '''mkdir dockerbuild/
cp build/libs/*.jar dockerbuild/app.jar
cp Dockerfile dockerbuild/Dockerfile
cd dockerbuild/
docker build -t nucleoteam/neo4jdockeraccountservice:latest ./'''
            script {
              echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Built Docker image"
              slackSend color: '#42e565', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Built Docker image"
            }
            
            
          },
          "Save Artifact": {
            script {
              echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Archived artifacts"
              slackSend color: '#f1f430', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Archived artifacts"
            }
            
            archiveArtifacts(artifacts: 'build/libs/*.jar', onlyIfSuccessful: true)
            
          }
        )
      }
    }
    stage('Publish Latest Image') {
      steps {
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Docker image publishing to DockerHub"
          slackSend color: '#3e6be8', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Docker image publishing to DockerHub"
        }
        
        sh 'docker push nucleoteam/neo4jdockeraccountservice:latest'
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Docker image published to DockerHub"
          slackSend color: '#42e565', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Docker image published to DockerHub"
        }
        
      }
    }
    stage('Deploy') {
      steps {
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Deploying docker image to Rancher"
          slackSend color: '#3e6be8', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Deploying docker image to Rancher"
        }
        
        rancher(environmentId: '1a5', ports: '8000:8080', environments: '1i180', confirm: true, image: 'nucleoteam/neo4jdockeraccountservice:latest', service: 'testapp/AccountManager', endpoint: 'http://212.47.248.38:8080/v2-beta', credentialId: 'rancher-server')
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Deployed docker image to Rancher"
          slackSend color: '#42e565', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Deployed docker image to Rancher"
        }
        
      }
    }
    stage('Finished') {
      steps {
        script {
          echo "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Finished pipeline"
          slackSend color: '#09a31e', message: "[${env.JOB_NAME} #${env.BUILD_NUMBER}] Finished pipeline"
        }
        
      }
    }
  }
}




//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//  ******************************************************************************
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
	agent {
        label "ngraph_onnx && controller"
    }
    environment {
        PROJECT_NAME = "ngraph"
        WORKDIR = "${WORKSPACE}/${BUILD_NUMBER}"
        JENKINS_GITHUB_CREDENTIAL_ID = "7157091e-bc04-42f0-99fd-dc4da2922a55"
        CI_DIR = "ngraph-onnx/.ci/jenkins"
        NGRAPH_ONNX_REPO_ADDRESS = "git@github.com:NervanaSystems/ngraph-onnx.git"
        NGRAPH_REPO_ADDRESS = "git@github.com:NervanaSystems/ngraph.git"
        NGRAPH_ONNX_BRANCH = "${CHANGE_BRANCH}"
        NGRAPH_BRANCH = "${CHANGE_BRANCH}"
    }
    options {
        skipDefaultCheckout true
    }
    stages {
        stage ("Checkout") {
            steps {
                script {
                    killPreviousRunningJobs()
                    checkoutSources()
                }
            }
        }
        stage ("Parallel CI") {
            steps {
                script {
                    dir("${WORKDIR}/${CI_DIR}") {
                        CI_FUNCTIONS = load "ci.groovy"
                        dockerfilesDir = "./dockerfiles"
                        parallelStagesMap = CI_FUNCTIONS.getConfigurationsMap(dockerfilesDir, NGRAPH_ONNX_BRANCH, NGRAPH_BRANCH)
                        parallel parallelStagesMap
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                gitPrInfo = getGitPrInfo(PROJECT_NAME)
                notifyByEmail(gitPrInfo)
            }
        }
        cleanup {
            deleteDir()
        }
    }
}

//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
//******************************************************************************
@Library('abort-previous-builds-library') _

pipeline {
    agent any
    stages {
        stage('Abort') {
            when {  // https://jenkins.io/doc/book/pipeline/syntax/#when
                beforeAgent true
                not {  // Nested when condition "not" requires exactly 1 child condition.
                    anyOf {
                        branch 'origin/master';  // Note that this only works on a multibranch Pipeline. ¯\_(ツ)_/¯
                        branch 'origin/develop';  // Note that this only works on a multibranch Pipeline. ¯\_(ツ)_/¯
                    }
                }
            }
            steps {
                println "Aborting previous builds if exists."
                abortPreviousBuilds()
            }
        }
        stage('Build') {
            steps {
                script {
                    def gitCommit = env.GIT_COMMIT
                    def shortGitCommit = gitCommit[0..6]
                    sh "echo ${shortGitCommit}"

                    def branchName = env.GIT_BRANCH
                    if (branchName.startsWith('origin/mast')) {
                        sh 'echo Filtered branch detected.'
                    }

                    if (env.GIT_BRANCH == 'origin/master' || env.GIT_BRANCH == 'origin/development') {
                        echo 'Allowed branch detected.'
                        // abortPreviousBuilds()
                    } else {
                        echo 'Blocked concurrent branch detected.'
                        abortPreviousBuilds()
                    }
                }
                sh 'env | sort'
                // https://issues.jenkins-ci.org/browse/JENKINS-46285
                println '\${BUILD_NUMBER}:'
                sh "echo ${env.BUILD_NUMBER}"
                println '\${GIT_BRANCH}:'
                sh "echo ${env.GIT_BRANCH}"
                println '\${GIT_COMMIT}:'
                sh "echo ${env.GIT_COMMIT}"
                println '\${GIT_PREVIOUS_COMMIT}:'
                sh "echo ${env.GIT_PREVIOUS_COMMIT}"
                println '\${GIT_PREVIOUS_SUCCESSFUL_COMMIT}:'
                sh "echo ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
                sleep 42
            }
        }
    }
}



//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
//************************************************************************************
#!/usr/bin/env groovy

//https://stackoverflow.com/a/48421660/4763512
def jobs = ["JobA", "JobB", "JobC"]

def parallelStagesMap = jobs.collectEntries {
    ["${it}" : generateStage(it)]
}

def generateStage(job) {
    return {
        stage("Parallel Sub-Stage: ${job}") {
            node("${job}") {
                echo "Message from the generated ${job} scripted parallel stage."
                sh script: "sleep 8"
            }
        }
    }
}

pipeline {
    agent any

    stages {
        stage('Sequential') {
            steps {
                echo 'Message from the declarative non-parallel stage.'
            }
        }

        stage('Parallel') {
            steps {
                script {
                    parallel parallelStagesMap
                }
            }
        }
    }
}




//*********************************************************
//*********************************************************
//*********************************************************
//*********************************************************
//*********************************************************
//*********************************************************
#!/usr/bin/env groovy
def getLastCommit() {
  sh "$GITHUB_LAST_COMMIT > .git/last-commit"
  return readFile(".git/last-commit").trim()
}
pipeline {
    agent any
    environment { 
        LAST_COMMIT = getLastCommit()
    }
    stages {
        stage('Build') {
            when {
                expression {
                    return !env.LAST_COMMIT.contains(env.JENKINS_SKIP_BUILD)
                }
            }
            steps { 
                echo 'npm install'
                sh 'npm install'
                echo 'kill process to avoid issues later'
                sh(returnStdout: true, script: 'lsof -i:$TEST_PORT -t | xargs -r kill -9') 
                echo 'npm test'
                sh 'npm test'
                echo 'kill process to avoid issues later'
                sh(returnStdout: true, script: 'lsof -i:$E2E_PORT -t | xargs -r kill -9') 
                echo 'npm run e2e'
                sh 'npm run e2e'
                echo 'kill process to avoid issues later'
                sh(returnStdout: true, script: 'lsof -i:$PROFILE_PORT -t | xargs -r kill -9') 
                sh 'npm run profile'
                echo 'git commit'
                sh 'git commit -m "$JENKINS_SKIP_BUILD upload profile result by build $BUILD_NUMBER"'
                echo 'git pull and push to avoid slow build and new commits'
                sh 'git pull origin $GITHUB_PUSH_BRANCH'
                sh 'git push origin HEAD:$GITHUB_PUSH_BRANCH' 
             }
        }
        stage ('Skip Build') {
            when {
                expression {
                    return env.LAST_COMMIT.contains(env.JENKINS_SKIP_BUILD)
                }
            }
            steps {
                echo 'skipped build.'
            }
        }
    }
    post {
          success { 
             githubNotify description: 'Build successfully by jenkins build '+ env.BUILD_NUMBER,  status: 'SUCCESS',sha:env.GIT_COMMIT, 
              credentialsId: env.GITHUB_CREDENTIALS_ID, account: env.GITHUB_USER_NAME, repo: env.GITHUB_REPO_NAME
          }
          failure { 
             githubNotify description: 'Build failed by Jenkins build '+ env.BUILD_NUMBER,  status: 'FAILURE',sha:env.GIT_COMMIT, 
              credentialsId: env.GITHUB_CREDENTIALS_ID, account: env.GITHUB_USER_NAME, repo: env.GITHUB_REPO_NAME
          }
    }
}

//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************


pipeline {
    agent{
        label 'jenkins-workers'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: "10")) // keep only last 10 builds
    }
    
    environment {
        BUILD_TAG = sh label: 'Generating build tag', returnStdout: true, script: 'python3 pipeline/scripts/tag.py ${GIT_BRANCH} ${BUILD_NUMBER} ${GIT_COMMIT}'
        BUILD_TAG_LOWER = sh label: 'Lowercase build tag', returnStdout: true, script: "echo -n ${BUILD_TAG} | tr '[:upper:]' '[:lower:]'"
        ENVIRONMENT_ID = "nhais-build"
    }    

    stages {
        stage('Build and Test Locally') {
            stages {
                stage('Build Docker Images') {
                    steps {
                        script {
                            sh label: 'Stopping running containers (preventative maintenance)', script: 'docker-compose down -v'
                            sh label: 'Running docker-compose build', script: 'docker-compose build --build-arg BUILD_TAG=${BUILD_TAG}'
                        }
                    }
                }
                stage('Deploy Locally') {
                    steps {
                        sh label: 'Starting containers', script: 'docker-compose up -d rabbitmq dynamodb nhais'
                        echo "Waiting 10 seconds for containers to start"
                        sleep 10
                        sh label: 'Show all running containers', script: 'docker ps'
                    }
                }
                stage('Run tests') {
                    steps {
                        sh label: 'Running tests', script: 'docker-compose run nhais-tests'
                    }
                    post {
                        always {
                            sh label: 'Copy test reports to folder', script: 'docker cp "$(docker ps -lq)":/usr/src/app/nhais/test-reports .'
                            junit '**/test-reports/*.xml'
                            sh label: 'Copy test coverage to folder', script: 'docker cp "$(docker ps -lq)":/usr/src/app/nhais/coverage.xml ./coverage.xml'
                            cobertura coberturaReportFile: '**/coverage.xml'
                        }
                    }
                }
                stage('Push image') {
                    when {
                        expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
                    }
                    steps {
                        script {
                            sh label: 'Pushing nhais image', script: "packer build -color=false pipeline/packer/nhais.json"
                        }
                    }
                }
            }
            post {
                always {
                    sh label: 'Create logs directory', script: 'mkdir logs'
                    sh label: 'Copy nhais container logs', script: 'docker-compose logs nhais > logs/nhais.log'
                    sh label: 'Copy dynamo container logs', script: 'docker-compose logs dynamodb > logs/outbound.log'
                    sh label: 'Copy rabbitmq logs', script: 'docker-compose logs rabbitmq > logs/inbound.log'
                    sh label: 'Copy nhais-tests logs', script: 'docker-compose logs nhais-tests > logs/nhais-tests.log'
                    archiveArtifacts artifacts: 'logs/*.log', fingerprint: true
                    sh label: 'Stopping containers', script: 'docker-compose down -v'
                }
            }
        }
        stage('Deploy and Integration Test') {
            when {
                expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            }
            stages {
                stage('Deploy using Terraform') {
                    steps {
                        echo 'TODO deploy NHAIS using terraform'
                    }
                }
                stage('Run integration tests') {
                    steps {
                        echo 'TODO run integration tests'
                        echo 'TODO archive test results'
                    }
                 }
            }

        }
        stage('Run SonarQube analysis') {
            steps {
                runSonarQubeAnalysis()
            }
        }
    }
    post {
        always {

            sh label: 'Stopping containers', script: 'docker-compose down -v'
            sh label: 'Remove all unused images not just dangling ones', script:'docker system prune --force'
            sh 'docker image rm -f $(docker images "*/*:*${BUILD_TAG}" -q) $(docker images "*/*/*:*${BUILD_TAG}" -q) || true'
        }
    }
}

void runSonarQubeAnalysis() {
    sh label: 'Running SonarQube analysis', script: "sonar-scanner -Dsonar.host.url=${SONAR_HOST} -Dsonar.login=${SONAR_TOKEN}"
}




//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************
//***********************************************************************

