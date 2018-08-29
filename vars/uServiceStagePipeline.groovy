#!/usr/bin/env groovy

/**
 * uServiceStagePipeline.groovy
 *
 * A Jenkins declarative pipeline for building Pancake-based microservices
 *
 * Required conf values
 *   - gitRepo - a microservice repo (example: Engineering/mint-das)
 *
 * Optional conf values
 *   - projectType - The type of project you want to build (default: 'gradle')
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - defaultBranch - Alternative default branch to build out of (default: 'master')
 *   - disableTests - set to true if this microservice does not have tests to run (default: false)
 *   - includeIntegrationTests - set to true if the Test stage should include integration tests (default: false)
 *   - disableDbUpdates - set to true if this microservice does not liquibase for database updates (default: false)
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: builds@squaretade.com)
 *   - subproject - specify if <code>gitRepo</code> has multiple microservices (example: warranty-migration-consumer)
 *   - dbCreateCredentialsId - Jenkins Credential ID for the <code>gradlew liquibaseUpdate<code> create password (default: 'stage-db-admin-password')
 *   - dbMainCredentialsId - Jenkins Credential ID for the microservice <code>gradlew liquibaseUpdate<code> main password (default: 'stage-db-admin-password')
 *   - dbInitCredentialsId - Jenkins Credential ID for the microservice <code>gradlew liquibaseUpdate<code> init password (default: 'stage-db-admin-password')
 *   - enableJacocoReport - Set to true if this microservice have JaCoCo coverage reports (default: false)
 *   - deployTaskOverride - Override the current gradle deploy task (default: deploy)
 *   - mavenTomcatDeploy - set to true if the project needs to be deployed in a Tomcat instance (default: false)
 *   - additionalBuildOpts - Additional config option for the build step
 *   - environmentList - Alternative environment list (default: preproduction) (availables: preproduction, dwhscripts, gridapp)
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - commandTimeout - Set the timeout in seconds to stage COMMAND (default: 60)
 *   - localBranch - define if Cloudbees should checkout as local branch
 *   - veracodeMethodType - SERIAL or PARALLEL (default: SERIAL)
 *   - veracodeApps - An optional list of Veracode application map configurations.  Each application has the following configuration options:
 *      - preBuild - Specify a command line that will be used to build a custom artifact specifically for Veracode policy scanning
 *      - appName - Veracode application name (example: mint-das)
 *      - sandboxName - Veracode sandbox name (default: 'Cloudbees Sandbox')
 *      - credentialsId - Veracode credentials ID (default: '5021cb1f-98a3-4d5b-af99-d6adf29a8d66')
 *      - includesPattern - Veracode upload includes pattern (default: 'build/libs/*.jar')
 *      - excludesPattern - Veracode upload excludes pattern (default: 'build/libs/*sources.jar,build/libs/*javadoc.jar')
 *      - timeout -  Optional time to wait in minutes for the scan to complete (example: 60).
 *      - uploadFrom: - The location within the workspace to upload artifacts from (default: '.')
 *   - triggerJob - An optional list of params to trigger a job inside uServiceStagePipeline
 *      - jobName - Name of the job to to be triggered (example: 'warranty-queuer', '../Automation/wcs-test')
 *      - parameters - An optional list of parameters to be passed to the triggered job (example: [string(name: 'ENVIRONMENT', value: 'stage2')])
 *                     note: You can pass in how many params you want, always using the example structure above,
 *                           also it is highly recommended that you just inform the params that needs to be overridden,
 *                           if the param has a default value and there is no explicit need to override them, then it is not necessary to include them in the params list.
 *
 * @param conf
 * @return
 */
def call(Map conf) {

    pipeline {
        agent any
        options {
            disableConcurrentBuilds()
            timeout(conf?.timeout ?: [time: 30, unit: 'MINUTES'])
            skipDefaultCheckout(true)
        }
        parameters {
            //noinspection GroovyAssignabilityCheck
            choice( name: 'OPERATION',
                    choices: stageEnv('operations', conf),
                    description: 'The operation(s) to perform (required).'
            )
            //noinspection GroovyAssignabilityCheck
            choice( name: 'ENVIRONMENT',
                    choices: stageEnv('environments', conf),
                    description: 'The staging environment (required).'
            )
            string(name: 'GIT_BRANCH_OR_TAG', defaultValue: stageEnv('getDefaultBranch', conf),
                    description: 'Git branch or tag name to build')
            booleanParam(name: 'GIT_IS_TAG', defaultValue: false, description: 'Check if this is a tag name')
            booleanParam(name: 'VERACODE_POLICY_SCAN', defaultValue: false,
                    description: 'Check if build artifacts should be uploaded to Veracode for a policy scan after building.')
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
                        // verify required config items are passed in
                        def requiredParams = ['gitRepo']
                        //noinspection GroovyAssignabilityCheck
                        assertRequiredConfParams(conf, requiredParams)

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

                        if (!stageEnv.isEnvironmentParamSet(params.ENVIRONMENT as String, OPERATION as String)) {
                            def errmsg = "#${currentBuild.number} ENVIRONMENT parameter is not set"
                            currentBuild.displayName = errmsg
                            error errmsg
                        }

                        echo "==> Performing operation: ${OPERATION}"

                        // set build info
                        def envParam = ''
                        if (params.ENVIRONMENT != 'NONE') {
                            envParam = "${params.ENVIRONMENT}"
                        }
                        String displayName = "#${currentBuild.number} ${envParam} ${params.GIT_BRANCH_OR_TAG} ${OPERATION}"
                        if (params.VERACODE_POLICY_SCAN) {
                            displayName += '+policy-scan'
                        }
                        currentBuild.displayName = displayName

                        //noinspection GroovyAssignabilityCheck
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/uServiceStagePipeline.groovy')

                        // determine which node we are running on
                        //noinspection GroovyAssignabilityCheck
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        PROJECT_TYPE = conf.projectType ?: "gradle"

                        // check for subproject config
                        SUB_PROJECT = conf.containsKey('subproject') ? ":${conf.subproject}:" : ''

                        // check for additional build options
                        ADD_BUILD_OPTS = conf.containsKey('additionalBuildOpts') ? "${conf.additionalBuildOpts}" : ''

                        // check for maven subproject
                        ADD_BUILD_OPTS += (conf.projectType == 'maven' && conf.containsKey('subproject')) ? " -f ${conf.subproject}/pom.xml" : ""

                        //Default value for the commandTimeout is 8 minutes since the rollingRestart takes longer than a standard restart
                        CMD_TIMEOUT = conf.commandTimeout ?: 60 * 8

                        if (params.VERACODE_POLICY_SCAN && !stageEnv.canVeracodeScan(conf)) {
                            echo "Warning: VERACODE_POLICY_SCAN requested for a project that doesn't have a Veracode application defined."

                            PERFORM_POLICY_SCAN = false
                        } else {
                            PERFORM_POLICY_SCAN = (OPERATION.contains('build') && params.VERACODE_POLICY_SCAN) ?: stageEnv.isAutomaticPolicyScan(params)
                        }

                        TRIGGER_JOB = conf.triggerJob ?: ''

                        VERACODE_SCAN_METHOD = stageEnv.defaultScanMethod(conf)
                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out ${params.GIT_BRANCH_OR_TAG} from ${conf.gitRepo}..."

                    // cloning repos in a subdirectory of the workspace result in *@tmp workspace folders getting created
                    // inside the current workspace. As a result, these folders get cleaned up by cleanupNodeWS
                    // in addition to everything else.
                    dir(conf.gitRepo) {
                        //noinspection GroovyAssignabilityCheck
                        checkoutGitBranchOrTag url: "git@github.squaretrade.com:${conf.gitRepo}.git",
                                credentialsId: conf.gitCredentialsId,
                                branchOrTag: params.GIT_BRANCH_OR_TAG,
                                isTag: params.GIT_IS_TAG,
                                localBranch: conf.localBranch
                    }
                }
                post {
                    failure {
                        echo 'Checkout failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Build') {
                when {
                    expression { OPERATION.contains('build') }
                }
                steps {
                    echo '==> Building...'
                    script {                        
                        dir(conf.gitRepo) {
                            buildService(PROJECT_TYPE, conf)
                        }
                    }
                }
                post {
                    failure {
                        echo 'Build failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }

            stage("Trigger job") {
                when {
                    expression { TRIGGER_JOB && TRIGGER_JOB.containsKey('jobName') }
                }

                steps {
                    build job: TRIGGER_JOB.jobName, parameters: TRIGGER_JOB.parameters
                }
                post {
                    failure {
                        echo 'Triggered job execution failed!'

                        emailNotification('failed', conf)
                    }
                    unstable {
                        echo 'Triggered job returned an unstable execution!'

                        emailNotification('unstable', conf)
                    }
                }
            }

            stage('Unit Tests') {
                when {
                    expression { OPERATION.contains('test') }
                }
                steps {
                    echo '==> Running Unit Tests...'
                    dir(conf.gitRepo) {
                        sh "./gradlew -s ${SUB_PROJECT}test"
                        junit '**/build/test-results/**/TEST-*.xml'
                    }
                }
                post {
                    failure {
                        echo 'Tests failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                    unstable {
                        echo 'One or more tests failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('unstable', conf)
                        error 'One or more tests failed!'
                    }
                }
            }

            stage('Veracode Policy Scan') {
                when {
                    expression { PERFORM_POLICY_SCAN }
                }
                steps {
                    script {
                        veracodeScan.steps(conf, params.GIT_BRANCH_OR_TAG as String, env.BUILD_NUMBER as String, 'POLICY', VERACODE_SCAN_METHOD)
                    }
                }
                post {
                    failure {
                        script {
                            veracodeScan.postFailure(conf, currentBuild)
                        }
                    }
                }
            }

            stage('Update DB') {
                when {
                    expression { OPERATION.contains('update-db') }
                }
                steps {
                    echo '==> Updating Database...'
                    dir(conf.gitRepo) {
                        //noinspection GroovyAssignabilityCheck
                        liquibaseUpdate(conf, stageEnv.defaultDbCredentialsId(), params.ENVIRONMENT)
                    }
                }
                post {
                    failure {
                        echo 'Update DB failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Integration Tests') {
                when {
                    expression { OPERATION.contains('test') && conf.containsKey('includeIntegrationTests') }
                }
                steps {
                    echo '==> Running Integration Tests...'
                    dir(conf.gitRepo) {
                        sh "./gradlew -s ${SUB_PROJECT}integTest"
                        junit '**/build/test-results/**/TEST-*.xml'
                    }
                }
                post {
                    failure {
                        echo 'Tests failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                    unstable {
                        echo 'One or more tests failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('unstable', conf)
                        error 'One or more tests failed!'
                    }
                }
            }

            stage ('JaCoCo Coverage Report') {
                when {
                    expression { OPERATION.contains('test') && conf.containsKey('enableJacocoReport') }
                }

                steps {
                    echo '==> Publishing JaCoCo coverage report'
                    dir(conf.gitRepo) {
                        sh "./gradlew -s ${SUB_PROJECT}jacocoTestReport"
                        jacoco(execPattern: '**/**.exec', sourcePattern: '**/src/main/java')
                    }
                }
                post {
                    failure {
                        echo 'Failed to publish JaCoCo report'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Deploy') {
                when {
                    expression { OPERATION.contains('deploy') }
                }
                steps {

                    echo '==> Deploying...'

                    script {
                        dir(conf.gitRepo) {                        
                            if (conf.projectType == 'maven' && stageEnv.mavenTomcatDeploy(conf)){
                                withMaven ( maven: 'Maven 3.5.3',
                                            options: [ artifactsPublisher(disabled: true) ]
                                ) {
                                    withCredentials([usernamePassword(credentialsId: stageEnv.datawarehouseCredentialsId("$STENV"), usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                                        sh "mvn -P${params.ENVIRONMENT} ${ADD_BUILD_OPTS} -Dtomcat.manager.username=$USERNAME -Dtomcat.manager.password=$PASSWORD tomcat7:deploy"
                                    }
                                }                            
                            }
                            else {
                                // default task for deploy
                                def gradleDeployTask = conf.deployTaskOverride ?: "deploy"
                                sh "./gradlew ${SUB_PROJECT}${gradleDeployTask}"
                            }
                        }
                    }                      


                }
                post {
                    success {
                        script {
                            // handle special case where we have compound OPERATION with 'deploy+rollingRestart'
                            // Reset the OPERATION to just perform a 'rollingRestart' in the Command stage
                            if (OPERATION.contains('rollingRestart')) {
                                OPERATION = 'rollingRestart'
                            }
                        }

                    }
                    failure {
                        echo 'Deploy failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Command') {
                when {
                    expression {
                        !OPERATION.contains('update-db') &&         // handled by 'Update DB' stage
                                !OPERATION.contains('+') &&         // exclude compound operations
                                stageEnv.commands(conf).contains(OPERATION)  // only run real commands
                    }
                }
                steps {
                    echo "==> Invoking Command: ${OPERATION}..."
                    dir(conf.gitRepo) {
                        timeout(time: CMD_TIMEOUT, unit: 'SECONDS') {
                            sh "./gradlew ${SUB_PROJECT}${OPERATION}"
                        }
                    }
                }
            }
        }
        post {
            always {
                //noinspection GroovyAssignabilityCheck
                cleanupNodeWS(WORK_NODE_LABEL)
            }
            changed {
                script {
                    if (currentBuild.currentResult == 'SUCCESS') {
                        echo 'Job recovered!'

                        emailNotification('recovered', conf)
                    }
                }
            }
            success {
                echo 'Its all good.'
            }
            failure {
                echo 'Not so good.'
            }
        }
    }
}
