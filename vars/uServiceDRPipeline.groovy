#!/usr/bin/env groovy

/**
 * uServiceDRPipeline.groovy
 *
 * A Jenkins declarative pipeline for DR operations
 *
 * Required conf values
 *   - gitRepo - a github repo (example: devops/dr-operations)
 *
 * Optional conf values
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 */
def call(Map conf) {

    pipeline {
        agent any
        options {
            disableConcurrentBuilds()
            timeout(conf?.timeout ?: [time: 120, unit: 'MINUTES'])
            skipDefaultCheckout(true)
        }
        parameters {
            //noinspection GroovyAssignabilityCheck
            choice( name: 'OPERATION',
                    choices: drEnv('operations'),
                    description: 'The operation(s) to perform (required).'
            )
            //noinspection GroovyAssignabilityCheck
            choice( name: 'ENVIRONMENT',
                    choices: drEnv('environments'),
                    description: 'The staging environment (required).'
            )
            //noinspection GroovyAssignabilityCheck
            choice( name: 'APPLICATION',
                    choices: drEnv('applications'),
                    description: 'Applications available (required).'
            )
            string(name: 'MICROSERVICE_STARTUP_DELAY', defaultValue: '0', description: 'Delay between each service start invocation (in seconds)')
        }
        environment {
            // Disable the gradle daemon for all stages
            GRADLE_OPTS = '-Dorg.gradle.daemon=false'
            STENV = "${params.ENVIRONMENT}"
        }
        stages {

            stage('Setup') {
                steps {
                    script {

                        // verify required choices are set
                        if (params.OPERATION == 'NONE') {
                            def errmsg = "#${currentBuild.number} OPERATION parameter is not set"
                            currentBuild.displayName = errmsg
                            error errmsg
                        }
                        else {
                            // Use OPERATION var here on out so that we can modify it because 'params' is immutable.
                            OPERATION = params.OPERATION
                        }

                        APPLICATION = params.APPLICATION

                        echo "==> Performing operation: ${OPERATION}"

                        // set build info
                        def envParam = ''
                        if (params.ENVIRONMENT != 'NONE') {
                            envParam = "${params.ENVIRONMENT}"
                        }
                        String displayName = "#${currentBuild.number} ${envParam} ${OPERATION} ${APPLICATION}"

                        // List of applications which failed during the job execution
                        FAILED_APPS = [] as LinkedHashSet<String>

                        //noinspection GroovyAssignabilityCheck
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/uServiceDRPipeline.groovy')

                        // determine which node we are running on
                        //noinspection GroovyAssignabilityCheck
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out from ${conf.gitRepo}..."

                    // cloning repos in a subdirectory of the workspace result in *@tmp workspace folders getting created
                    // inside the current workspace. As a result, these folders get cleaned up by cleanupNodeWS
                    // in addition to everything else.

                    dir(params.APPLICATION) {
                        //noinspection GroovyAssignabilityCheck
                        checkoutGitBranchOrTag url: "git@github.squaretrade.com:${conf.gitRepo}.git",
                                branchOrTag: "master",
                                shallowClone: true
                    }
                }
                post {
                    failure {
                        echo 'Checkout failed!'
                    }
                }
            }

            stage('Read YAML data') {
                steps {
                    echo "==> Getting YAML DATA..."

                    dir(APPLICATION) {

                        script {

                            YAML_DATA = sh(returnStdout: true, script: "./gradlew --refresh-dependencies -PSTENV=${STENV} -q printStenvConfig")
                            YAML_DATA = drEnv.stEnvironmentsYML(YAML_DATA)

                            //Get sorted list of applications
                            APPLICATION_LIST = drEnv.getSortedApplicationFromYaml(YAML_DATA)

                            //Get environment data
                            ENV_DATA = drEnv.getDataFromYaml(YAML_DATA, "environment", STENV)

                            //Get cluster data from
                            CLUSTER_DATA = drEnv.getDataFromYaml(YAML_DATA, "clusterType", ENV_DATA.clusterType)

                        }

                    }
                }
                post {
                    failure {
                        echo 'Error trying to get YAML data!'
                    }
                }
            }

            stage("Enable/Disable startup") {
                when {
                    expression {
                        OPERATION.contains('enableStartup') ||
                                OPERATION.contains('disableStartup')
                    }
                }
                steps {
                    echo "==> Invoking Command: ${OPERATION}..."
                    dir(APPLICATION) {

                        script {

                            def cmdStatus
                            def startupOperation = OPERATION.contains('enableStartup') ? "enableStartup" : "disableStartup"

                            if (APPLICATION == "all") {

                                APPLICATION_LIST.each { app ->

                                    APP_DATA = app

                                    if (!APP_DATA.appName.startsWith("jboss-")) {

                                        echo "==> ${startupOperation} for ${APP_DATA.appName} on ${STENV} ..."

                                        cmdStatus = sh(returnStatus: true, script: "./gradlew ${startupOperation} -PSTENV='${STENV}' -PappName='${APP_DATA.appName}' -PappUser='${APP_DATA.appUser}' -PappCluster='${APP_DATA.cluster}' -PhttpsPort=${APP_DATA.port} -PdeployUser='${APP_DATA.appUser}' -PdeployDir='/opt/eng/${APP_DATA.appName}'")
                                        if (cmdStatus != 0)
                                            FAILED_APPS.add(startupOperation + " -> " + APP_DATA.appName)

                                    }

                                }

                            } else {

                                APP_DATA = drEnv.getApplicationFromYaml(YAML_DATA, APPLICATION)

                                echo "==> ${startupOperation} for ${APP_DATA.appName} on ${STENV} ..."

                                if (!APP_DATA.appName.startsWith("jboss-")) {
                                    cmdStatus = sh(returnStatus: true, script: "./gradlew ${startupOperation} -PSTENV='${STENV}' -PappName='${APP_DATA.appName}' -PappUser='${APP_DATA.appUser}' -PappCluster='${APP_DATA.cluster}' -PhttpsPort=${APP_DATA.port} -PdeployUser='${APP_DATA.appUser}' -PdeployDir='/opt/eng/${APP_DATA.appName}'")
                                    if (cmdStatus != 0)
                                        FAILED_APPS.add(startupOperation + " -> " + APP_DATA.appName)
                                }

                            }

                        }
                    }
                }
                post {
                    failure {
                        echo 'Error enabling/disabling startup!'
                    }
                }
            }

            stage("Enable/Disabling JBoss guardian") {
                when {
                    expression {
                        ((OPERATION.contains('enableStartup') ||
                                OPERATION.contains('disableStartup')) &&
                         (APPLICATION == "all" ||
                                 APPLICATION.startsWith('jboss-')))
                    }
                }
                steps {
                    echo "==> Invoking Command: ${OPERATION}..."
                    dir(APPLICATION) {

                            script {

                                def cmdStatus, hostPath
                                def guardianOperation = OPERATION.contains('enableStartup') ? "enable-guardian" : "disable-guardian"

                                if (APPLICATION == "all") {

                                    APPLICATION_LIST.each { app ->

                                        APP_DATA = app

                                        if (APP_DATA.appName.startsWith('jboss-')){

                                            echo "==> ${guardianOperation} for ${APP_DATA.appName} on ${STENV} ..."

                                            ST_HOSTS = drEnv.getHostsFromClusterType(CLUSTER_DATA, APP_DATA.cluster.toString())

                                            ST_HOSTS.each { host ->

                                                if (host.startsWith('jboss-mint-'))
                                                    hostPath = "/opt/eng/dist-mint"
                                                else
                                                    hostPath = "/opt/eng/dist"

                                                cmdStatus = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no -l jenkins ${host} sudo -u jboss ${hostPath}/jboss/bin/jboss-mgr ${guardianOperation}")
                                                if (cmdStatus != 0)
                                                    FAILED_APPS.add(guardianOperation + " -> " + APP_DATA.appName)

                                            }

                                        }

                                    }

                                } else {

                                    APP_DATA = drEnv.getApplicationFromYaml(YAML_DATA, APPLICATION)

                                    echo "==> ${guardianOperation} for ${APP_DATA.appName} on ${STENV} ..."

                                    if (APP_DATA.appName.startsWith('jboss-')){

                                        ST_HOSTS = drEnv.getHostsFromClusterType(CLUSTER_DATA, APP_DATA.cluster.toString())

                                        ST_HOSTS.each { host ->

                                            if (host.startsWith('jboss-mint-'))
                                                hostPath = "/opt/eng/dist-mint"
                                            else
                                                hostPath = "/opt/eng/dist"

                                            cmdStatus = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no -l jenkins ${host} sudo -u jboss ${hostPath}/jboss/bin/jboss-mgr ${guardianOperation}")
                                            if (cmdStatus != 0)
                                                FAILED_APPS.add(guardianOperation + " -> " + APP_DATA.appName + " | Host: " + host)

                                        }

                                    }

                                }

                            }

                    }
                }
                post {
                    failure {
                        echo 'Error enabling/disabling JBoss guardian!'
                    }
                }
            }

            stage('Service operations') {
                when {
                    expression {
                        OPERATION.contains('restart') ||
                                OPERATION.contains('rollingRestart') ||
                                OPERATION.contains('start') ||
                                OPERATION.contains('stop') ||
                                OPERATION.contains('status')
                    }
                }
                steps {
                    echo "==> Invoking Command: ${OPERATION}..."
                    dir(APPLICATION) {

                        script {

                            def cmdStatus, serviceOperation
                            def serviceJbossOperation = ""

                            if (OPERATION.contains('stop')) {
                                serviceOperation = "stopRemoteService"
                                serviceJbossOperation = "stop"
                            } else if (OPERATION.contains('start')) {
                                serviceOperation = "startRemoteService"
                                serviceJbossOperation = "start"
                            } else if (OPERATION.contains('status')) {
                                serviceOperation = "statusRemoteService"
                            } else if (OPERATION.contains('restart')) {
                                serviceOperation = "restartRemoteService"
                            } else {
                                serviceOperation = "rollingRestart"
                            }

                            if (APPLICATION == "all") {

                                APPLICATION_LIST.each { app ->

                                    APP_DATA = app

                                    echo "==> Running ${OPERATION} for ${APP_DATA.appName} on ${STENV} ..."

                                    if (!APP_DATA.appName.startsWith("jboss-")) {
                                        cmdStatus = sh(returnStatus: true, script: "./gradlew ${serviceOperation} -PSTENV='${STENV}' -PappName='${APP_DATA.appName}' -PappUser='${APP_DATA.appUser}' -PappCluster='${APP_DATA.cluster}' -PhttpsPort='${APP_DATA.port}' -PdeployUser='${APP_DATA.appUser}' -PdeployDir='/opt/eng/${APP_DATA.appName}'")
                                        if (cmdStatus != 0)
                                            FAILED_APPS.add(serviceOperation + " -> " + APP_DATA.appName)

                                        if (serviceOperation == "startRemoteService")
                                            sleep MICROSERVICE_STARTUP_DELAY.toInteger()

                                    } else {

                                        if (serviceJbossOperation != "") {

                                            ST_HOSTS = drEnv.getHostsFromClusterType(CLUSTER_DATA, APP_DATA.cluster.toString())

                                            ST_HOSTS.each { host ->

                                                if (host.startsWith('jboss-mint-'))
                                                    hostPath = "/opt/eng/dist-mint"
                                                else
                                                    hostPath = "/opt/eng/dist"

                                                cmdStatus = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no -l jenkins ${host} sudo -u jboss ${hostPath}/jboss/bin/jboss-mgr ${serviceJbossOperation}")
                                                if (cmdStatus != 0)
                                                    FAILED_APPS.add(serviceJbossOperation + " -> " + APP_DATA.appName + " | Host: " + host)

                                            }

                                        }

                                    }

                                }

                            } else {

                                APP_DATA = drEnv.getApplicationFromYaml(YAML_DATA, APPLICATION)

                                echo "==> Running ${OPERATION} ${APP_DATA.appName} on ${STENV} ..."

                                if (!APP_DATA.appName.startsWith("jboss-"))
                                    sh "./gradlew ${serviceOperation} -PSTENV='${STENV}' -PappName='${APP_DATA.appName}' -PappUser='${APP_DATA.appUser}' -PappCluster='${APP_DATA.cluster}' -PhttpsPort='${APP_DATA.port}' -PdeployUser='${APP_DATA.appUser}' -PdeployDir='/opt/eng/${APP_DATA.appName}'"
                                else {

                                    if (serviceJbossOperation != "") {

                                        ST_HOSTS = drEnv.getHostsFromClusterType(CLUSTER_DATA, APP_DATA.cluster.toString())

                                        ST_HOSTS.each { host ->

                                            if (host.startsWith('jboss-mint-'))
                                                hostPath = "/opt/eng/dist-mint"
                                            else
                                                hostPath = "/opt/eng/dist"

                                            cmdStatus = sh(returnStatus: true, script: "ssh -o StrictHostKeyChecking=no -l jenkins ${host} sudo -u jboss ${hostPath}/jboss/bin/jboss-mgr ${serviceJbossOperation}")
                                            if (cmdStatus != 0)
                                                FAILED_APPS.add(serviceJbossOperation + " -> " + APP_DATA.appName + " | Host: " + host)

                                        }

                                    }

                                }

                            }

                        }

                    }
                }
                post {
                    failure {
                        echo 'Error when trying to restart services!'
                    }
                }
            }

        }
        post {
            always {

                script {
                    if (FAILED_APPS.size() > 0) {
                        echo 'The following applications failed during the job execution: '
                        echo FAILED_APPS.join('\n')
                    }
                }

                //noinspection GroovyAssignabilityCheck
                cleanupNodeWS(WORK_NODE_LABEL)
            }
            changed {
                script {
                    if (currentBuild.currentResult == 'SUCCESS') {
                        echo 'Job recovered!'
                    }
                }
            }
            success {
                echo 'It\'s all good.'
            }
            failure {
                echo 'Not so good.'
            }
        }
    }
}
