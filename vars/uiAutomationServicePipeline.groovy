#!/usr/bin/env groovy

/**
 * uiAutomationServicePipeline.groovy
 *
 * A Jenkins declarative pipeline to run functional test suites from the Engineering/UIAutomation repository
 *
 * Required conf values
 *   - gitRepo - a microservice repo (example: Engineering/automation)
 *   - testSuite - the automation suite that need to run (examples: Smoke)
 *
 * Optional conf values
 *   - cron - cron schedule to run
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: qa-automation@squaretade.com)
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - environment - The environment where the job should run (default: NONE)
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
        }

        triggers {
            cron(conf?.cron ?: '')
        }

        parameters {
            //noinspection GroovyAssignabilityCheck
            choice( name: 'ENVIRONMENT',
                    choices: conf?.environment ?: stageEnv('getAutomationEnvironments'),
                    description: 'the staging environment (required).'
            )
            string( name: 'GIT_BRANCH_OR_TAG',
                    defaultValue: 'dev-ui-automation',
                    description: 'Git branch or tag name to build'
            )
            booleanParam( name: 'GIT_IS_TAG',
                    defaultValue: false,
                    description: 'Check if this is a tag name'
            )
            choice( name: 'TESTRAIL_UPDATE',
                    choices: "no\n" + "yes\n",
                    description: 'Update automation result output into Testrail?'
            )
            file(   name: 'TEST_DATA_FILE',
                    description: 'Upload test data file'
            )
        }

        environment {
            // Disable the gradle daemon for all stages
            GRADLE_OPTS = '-Dorg.gradle.daemon=false'
            RunOn = "${conf.runOn}"
        }

        stages {

            stage('Setup') {
                steps {
                    script {
                        def requiredParams = ['gitRepo', 'testSuite']

                        //noinspection GroovyAssignabilityCheck
                        assertRequiredConfParams(conf, requiredParams)

                        //fails the build if 'NONE' is set as ENVIRONMENT
                        if (params.ENVIRONMENT == 'NONE') {
                            def errmsg = "#${currentBuild.number} ENVIRONMENT parameter is not set"
                            currentBuild.displayName = errmsg
                            error errmsg
                        }

                        // set build info
                        def envParam = ''
                        if (params.ENVIRONMENT != 'NONE') {
                            envParam = "${params.ENVIRONMENT}"
                        }

                        currentBuild.displayName = "#${currentBuild.number} ${envParam} ${params.GIT_BRANCH_OR_TAG}"

                        //noinspection GroovyAssignabilityCheck
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/uiAutomationServicePipeline.groovy')

                        // determine which node we are running on
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        workDir = conf.gitRepo + '/ST-UIAutomation'
                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out ${params.GIT_BRANCH_OR_TAG} from ${conf.gitRepo}..."

                    dir(conf.gitRepo) {
                        //noinspection GroovyAssignabilityCheck
                        checkoutGitBranchOrTag url: "git@github.squaretrade.com:${conf.gitRepo}.git",
                                credentialsId: conf.gitCredentialsId,
                                branchOrTag: params.GIT_BRANCH_OR_TAG,
                                isTag: params.GIT_IS_TAG,
                                submodules: true
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

            stage('UI Automation Suite') {
                steps {
                    echo '==> Executing Automation Suite'

                    dir(workDir) {
                        sh "./gradlew clean test -P${conf.testSuite} -Denv=${params.ENVIRONMENT} -DtestingType=${conf.testSuite} -DtoRecipient=${conf.notificationEmailAddr ?: 'qa-automation@squaretrade.com'} -DtestrailUpdate=${params.TESTRAIL_UPDATE}"
                    }
                }
                post {
                    always {
                        dir(workDir) {
                            //Archive report files.
//                            script {
//                                automationEnv.zipUIAutomationTestReportFiles()
//                            }

                            archiveArtifacts 'resources/output/report*/*.*'

                            //In case of failing tests, gradle suite task always return `ERROR`, junit is running under `always` post action to ensure test reports.
                            junit 'build/test-results/**/*.xml'
                        }
                    }
                    failure {
                        echo 'Build failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)
                    }
                }
            }
        }
    }
}