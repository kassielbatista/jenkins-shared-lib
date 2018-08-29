#!/usr/bin/env groovy

/**
 * dwhscriptsPipeline.groovy
 *
 * A Jenkins declarative pipeline for DataWarehouse DWHScripts
 *
 * Required conf values
 *   - gitRepo - git repo to checkout (example: Engineering/datawarehouse)
 *   - veracodeApps - An optional list of Veracode application map configurations.  Each application has the following configuration options:
 *      - appName - Veracode application name (example: datawarehouse)
 *      - includesPattern - Veracode upload includes pattern (default: '*.zip')
 *
 * Optional conf values
 *   - projectType - The type of project you want to build (default: 'gradle')
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: builds@squaretade.com)
 *   - pythonProjectFiles - Optional list of extensions to search when scanning Python projects (default: ['.py', '.html', '.js', '.css'])
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - veracodeApps - An optional list of Veracode application map configurations.  Each application has the following configuration options:
 *      - excludesPattern - Veracode upload excludes pattern (example: 'build/*.jar')
 *      - timeout -  Optional time to wait in minutes for the scan to complete (example: 60).
 *      - uploadFrom: - The location within the workspace to upload artifacts from (example: '.')
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
            choice(name: 'OPERATION',
                    choices: stageEnv('dwhscriptsOperations'),
                    description: 'The operation(s) to perform (required).'
            )
            //noinspection GroovyAssignabilityCheck
            choice(name: 'ENVIRONMENT',
                    choices: stageEnv('getDwhscriptsEnvironments'),
                    description: 'The dwhscripts environment (required).'
            )
            string(name: 'GIT_BRANCH_OR_TAG', defaultValue: 'master-stage',
                    description: 'Git branch or tag name to build')
            booleanParam(name: 'GIT_IS_TAG', defaultValue: false, description: 'Check if GIT_BRANCH_OR_TAG is a tag name')
            string(name: 'TAG_NAME',
                    description: 'Tag name to be created under TAG stage.')
        }

        stages {

            stage('Setup') {
                steps {
                    script {
                        // verify required config items are passed in
                        def requiredParams = [
                                'gitRepo',
                                "veracodeApps['appName']",
                                "veracodeApps['includesPattern']"
                        ]
                        //noinspection GroovyAssignabilityCheck
                        assertRequiredConfParams(conf, requiredParams)

                        // verify required choices are set
                        if (params.OPERATION == 'NONE') {
                            def errmsg = "#${currentBuild.number} OPERATION parameter is not set"
                            currentBuild.displayName = errmsg
                            error errmsg
                        }

                        if (!stageEnv.isEnvironmentParamSet(params.ENVIRONMENT as String, OPERATION as String)) {
                            def errmsg = "#${currentBuild.number} ENVIRONMENT parameter is not set"
                            currentBuild.displayName = errmsg
                            error errmsg
                        }

                        echo "==> Performing operation: ${params.OPERATION}"

                        // set build info
                        def envParam = ''
                        if (params.ENVIRONMENT != 'NONE') {
                            envParam = "${params.ENVIRONMENT}"
                        }

                        currentBuild.displayName = "#${currentBuild.number} ${envParam} ${params.GIT_BRANCH_OR_TAG} ${params.OPERATION}"

                        //noinspection GroovyAssignabilityCheck
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/dwhscriptsPipeline.groovy')

                        // determine which node we are running on
                        //noinspection GroovyAssignabilityCheck
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        PROJECT_TYPE = conf.projectType ?: "gradle"
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
                                isTag: params.GIT_IS_TAG
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

            stage('Package') {
                when {
                    expression { params.OPERATION.contains('policy-scan') }
                }
                steps {
                    echo "===> Packaging Python project..."

                    dir(conf.gitRepo) {
                        script {
                            buildService(PROJECT_TYPE, conf)
                        }
                    }
                }
                post {
                    failure {
                        echo 'Failed to package files!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Veracode Policy Scan') {
                when {
                    expression { params.OPERATION.contains('policy-scan') }
                }
                steps {
                    script {
                        veracodeScan.steps(conf, params.GIT_BRANCH_OR_TAG as String, env.BUILD_NUMBER as String, 'POLICY')
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

            stage('Deploy') {
                when {
                    expression { params.OPERATION.contains('deploy') }
                }
                steps {
                    echo "==> Deploying to ${params.ENVIRONMENT}..."
                    dir(conf.gitRepo) {
                        script {
                            String backupScriptFilename = 'dwhscripts_backup.sh'
                            String backupScript = libraryResource("com/squaretrade/backup/${backupScriptFilename}")
                            writeFile file: backupScriptFilename, text: backupScript
                            stageEnv.dwhscriptsDeploy(params.ENVIRONMENT as String, backupScriptFilename, '/opt/eng', this)
                        }
                    }
                }
                post {
                    failure {
                        failure {
                            echo 'Failed to deploy dwhscripts!'
                            //noinspection GroovyAssignabilityCheck
                            emailNotification('failed', conf)
                        }
                    }
                }
            }

            stage('Tag') {
                when {
                    expression { params.OPERATION.contains('tag') }
                }
                steps {
                    echo '===> Creating Tag...'

                    sshagent(credentials: ['st-github-builds']) {
                        dir(conf.gitRepo) {
                            script {
                                stageEnv.gitTag(params.TAG_NAME, env.JOB_NAME, currentBuild.number)
                            }
                        }
                    }
                }
                post {
                    failure {
                        echo 'Failed to create git tag!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
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
                        //noinspection GroovyAssignabilityCheck
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