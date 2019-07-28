#!/usr/bin/env groovy

Boolean isRunningOnJenkinsInfra() {
    return env.JENKINS_URL == 'https://ci.jenkins.io/' || isTrusted()
}

Boolean isTrusted() {
    return env.JENKINS_URL == 'https://trusted.ci.jenkins.io:1443/'
}

Object withDockerCredentials(Closure body) {
    if (isRunningOnJenkinsInfra()) {
      env.DOCKERHUB_ORGANISATION =  ( isTrusted() ? 'jenkins' : 'jenkins4eval')
      withCredentials([[$class: 'ZipFileBinding', credentialsId: 'jenkins-dockerhub', variable: 'DOCKER_CONFIG']]) {
          return body.call()
      }
    }
    else {
        echo 'Cannot use Docker credentials outside of jenkins infra environment'
    }
}

Object checkout(String repo = null) {
    if (env.BRANCH_NAME) {
        checkout scm
    } else if ((env.BRANCH_NAME == null) && (repo)) {
        git repo
    } else {
        error 'buildPlugin must be used as part of a Multibranch Pipeline *or* a `repo` argument must be provided'
    }
}

/**
 * Retrieves Settings file to be used with Maven.
 * If {@code MAVEN_SETTINGS_FILE_ID} variable is defined, the file will be retrieved from the specified
 * configuration ID provided by Config File Provider Plugin.
 * Otherwise it will fallback to a standard Jenkins infra resolution logic.
 * @param settingsXml Absolute path to the destination settings file
 * @param jdk Version of JDK to be used
 * @return {@code true} if the file has been defined
 */
boolean retrieveMavenSettingsFile(String settingsXml, String jdk = 8) {
    if (env.MAVEN_SETTINGS_FILE_ID != null) {
        configFileProvider([configFile(fileId: env.MAVEN_SETTINGS_FILE_ID, variable: 'mvnSettingsFile')]) {
            if (isUnix()) {
                sh "cp ${mvnSettingsFile} ${settingsXml}"
            } else {
                bat "copy ${mvnSettingsFile} ${settingsXml}"
            }
        }
        return true
    } else if (jdk.toInteger() > 7 && isRunningOnJenkinsInfra()) {
        /* Azure mirror only works for sufficiently new versions of the JDK due to Letsencrypt cert */
        writeFile file: settingsXml, text: libraryResource('settings-azure.xml')
        return true
    }
    return false
}

/**
 * Runs Maven for the specified options in the current workspace.
 * Maven settings will be added by default if needed.
 * @param jdk JDK to be used
 * @param options Options to be passed to the Maven command
 * @param extraEnv Extra environment variables to be passed when invoking the command
 * @return nothing
 * @see #retrieveMavenSettingsFile(String)
 */
Object runMaven(List<String> options, String jdk = 8, List<String> extraEnv = null, String settingsFile = null) {
    List<String> mvnOptions = [
        '--batch-mode',
        '--show-version',
        '--errors',
        '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn',
    ]
    if (settingsFile != null) {
        mvnOptions += "-s $settingsFile"
    } else if (jdk.toInteger() > 7 && isRunningOnJenkinsInfra()) {
        /* Azure mirror only works for sufficiently new versions of the JDK due to Letsencrypt cert */
        def settingsXml = "${pwd tmp: true}/settings-azure.xml"
        if (retrieveMavenSettingsFile(settingsXml)) {
            mvnOptions += "-s $settingsXml"
        }
    }
    mvnOptions.addAll(options)
    mvnOptions.unique()
    String command = "mvn ${mvnOptions.join(' ')}"
    runWithMaven(command, jdk, extraEnv)
}

/**
 * Runs the command with Java and Maven environments.
 * The command may be either Batch or Shell depending on the OS.
 * @param command Command to be executed
 * @param jdk JDK version to be used
 * @param extraEnv Extra environment variables to be passed
 * @return nothing
 */
Object runWithMaven(String command, String jdk = 8, List<String> extraEnv = null) {
    List<String> env = [
        "PATH+MAVEN=${tool 'mvn'}/bin"
    ]
    if (extraEnv != null) {
        env.addAll(extraEnv)
    }

    runWithJava(command, jdk, env)
}

/**
 * Runs the command with Java environment.
 * {@code PATH} and {@code JAVA_HOME} will be set.
 * The command may be either Batch or Shell depending on the OS.
 * @param command Command to be executed
 * @param jdk JDK version to be used
 * @param extraEnv Extra environment variables to be passed
 * @return nothing
 */
Object runWithJava(String command, String jdk = 8, List<String> extraEnv = null) {
    String jdkTool = "jdk${jdk}"
    List<String> env = [
        "JAVA_HOME=${tool jdkTool}",
        'PATH+JAVA=${JAVA_HOME}/bin',
    ]
    if (extraEnv != null) {
        env.addAll(extraEnv)
    }

    withEnv(env) {
        if (isUnix()) { // TODO JENKINS-44231 candidate for simplification
            sh command
        } else {
            bat command
        }
    }
}

/**
 * Gets a specification for a jenkins version or war and downloads and stash it under the name provided
 * @param Specification for a jenkins war, can be a jenkins URI to the jenkins.war, a Jenkins version or one of "latest", "latest-rc", "lts" and "lts-rc". Defaults to "latest". For local war files use the file:// protocol
 * @param stashName The name used to stash the jenkins war file, defaults to "jenkinsWar"
 */
void stashJenkinsWar(String jenkins, String stashName = "jenkinsWar") {
    def isVersionNumber = (jenkins =~ /^\d+([.]\d+)*(-rc[0-9]+[.][0-9a-f]{12})?$/).matches()
    def isLocalJenkins = jenkins.startsWith("file://")
    def mirror = "http://mirrors.jenkins.io/"

    def jenkinsURL

    if (jenkins == "latest") {
        jenkinsURL = mirror + "war/latest/jenkins.war"
    } else if (jenkins == "latest-rc") {
        jenkinsURL = mirror + "/war-rc/latest/jenkins.war"
    } else if (jenkins == "lts") {
        jenkinsURL = mirror + "war-stable/latest/jenkins.war"
    } else if (jenkins == "lts-rc") {
        jenkinsURL = mirror + "war-stable-rc/latest/jenkins.war"
    }

    if (isLocalJenkins) {
        if (!fileExists(jenkins - "file://")) {
            error "Specified Jenkins file does not exists"
        }
    }
    if (!isVersionNumber && !isLocalJenkins) {
        if (jenkinsURL == null) {
            error "Not sure how to interpret $jenkins as a version, alias, or URL"
        }
        echo 'Checking whether Jenkins WAR is available…'
        sh "curl -ILf ${jenkinsURL}"
    }

    if (isVersionNumber) {
        List<String> downloadCommand = [
                "dependency:copy",
                "-Dartifact=org.jenkins-ci.main:jenkins-war:${jenkins}:war",
                "-DoutputDirectory=.",
                "-Dmdep.stripVersion=true"
        ]
        dir("deps") {
            runMaven(downloadCommand)
            sh "cp jenkins-war.war jenkins.war"
            stash includes: 'jenkins.war', name: stashName
        }
    } else if (isLocalJenkins) {
        dir(pwd(tmp: true)) {
            sh "cp ${jenkins - 'file://'} jenkins.war"
            stash includes: "*.war", name: "jenkinsWar"
        }
    } else {
        sh("curl -o jenkins.war -L ${jenkinsURL}")
        stash includes: '*.war', name: 'jenkinsWar'
    }
}

/**
 * Make sure the code block is run in a node with the all the specified nodeLabels as labels, if already running in that
 * it simply executes the code block, if not allocates the desired node and runs the code inside it
 * Node labels must be specified as String formed by a comma separated list of labels
 * Please note that this step is not able to manage complex labels and checks for them literally, so do not try to use labels like 'docker,(lowmemory&&linux)' as it will result in
 * the step launching a new node as is unable to find the label '(lowmemory&amp;&amp;linux)' in the list of labels for the current node
 *
 * @param env The run environment, used to access the current node labels
 * @param nodeLabels The node labels, a string containing the comma separated labels
 * @param body The code to run in the desired node
 */
void ensureInNode(env, nodeLabels, body) {
    def inCorrectNode = true
    def splitted = nodeLabels.split(",")
    if (env.NODE_LABELS == null) {
        inCorrectNode = false
    } else {
        for (label in splitted) {
            if (!env.NODE_LABELS.contains(label)) {
                inCorrectNode = false
                break
            }
        }
    }

    if (inCorrectNode) {
        body()
    } else {
        node(splitted.join("&&")) {
            body()
        }
    }
}

/**
 * When appropriate, publish artifacts from the current build to the Incrementals repository.
 * See INFRA-1571 and JEP-305.
 */
void maybePublishIncrementals() {
        stage('Deploy') {
            node('linux') {
                withCredentials([string(credentialsId: 'incrementals-publisher-token', variable: 'FUNCTION_TOKEN')]) {
                    sh '''
curl -i -H 'Content-Type: application/json' -d '{"build_url":"'$BUILD_URL'"}' "https://jenkins-incrementals.azurewebsites.net/api/incrementals-publisher?clientId=default&code=$FUNCTION_TOKEN" || echo 'Problem calling Incrementals deployment function'
                    '''
                }
            }
        }
}
