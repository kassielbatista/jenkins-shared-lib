#!/usr/bin/env groovy
/**
 * automationServicePipeline.groovy
 *
 * A Jenkins declarative pipeline to run functional test suites from the Engineering/automation repository
 *
 * Required conf values
 *   - gitRepo - a microservice repo (example: Engineering/automation)
 *   - suites - the automation suite that need to run (examples: [WarrantyRegression, BulkRegression], [ClaimBVT])
 *   - planType - plan type to be executed (example: BVT1)
 *   - suiteType - suite type to be executed (example: MERCHANT_INTEGRATION)
 *
 * Optional conf values
 *   - cron - cron schedule to run
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: builds@squaretade.com)
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - maxThreads - Max number of threads (default: 8)
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
            skipDefaultCheckout(true)
        }

        triggers {
            cron(conf?.cron ?: '')
        }

        parameters {
            choice( name: 'ENVIRONMENT',
                    choices: conf?.environment ?: stageEnv('getAutomationEnvironments'),
                    description: 'the staging environment (required).'
            )
            string( name: 'GIT_BRANCH_OR_TAG',
                    defaultValue: 'dev-automation',
                    description: 'Git branch or tag name to build'
            )
            booleanParam( name: 'GIT_IS_TAG',
                          defaultValue: false,
                          description: 'Check if this is a tag name'
            )
            choice( name: 'RELEASE_TYPE',
                    choices: "release\n" +
                             "hotfix",
                    description: 'The release type to run'
            )
            booleanParam( name: 'REPORT_TEST',
                    defaultValue: false,
                    description: 'Check this only if you want to report the test results to test rail. (Default: false)'
            )
        }

        environment {
            // Disable the gradle daemon for all stages
            GRADLE_OPTS = '-Dorg.gradle.daemon=false'
            STENV = "${params.ENVIRONMENT}"
            MAX_THREADS = '8'
            PLAN_TYPE = "${conf.planType}"
            SUITE_TYPE = "${conf.suiteType}"
        }

        stages {

            stage('Setup') {
                steps {
                    script {
                        def requiredParams = ['gitRepo',
                                              'suites[0]',
                                              'planType',
                                              'suiteType']

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
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/automationServicePipeline.groovy')

                        // determine which node we are running on
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        MAX_THREADS = conf.maxThreads ?: MAX_THREADS

                        JSON_DIR = 'src/main/resources/testrail-configs/'

                        automationEnv.sendSlackInfo()
                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out ${params.GIT_BRANCH_OR_TAG} from ${conf.gitRepo}..."

                    //noinspection GroovyAssignabilityCheck
                    checkoutGitBranchOrTag url: "git@github.squaretrade.com:${conf.gitRepo}.git",
                            credentialsId: conf.gitCredentialsId,
                            branchOrTag: params.GIT_BRANCH_OR_TAG,
                            isTag: params.GIT_IS_TAG,
                            submodules: true
                }
                post {
                    failure {
                        echo 'Checkout failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)

                        script {
                            automationEnv.sendSlackFailure()
                        }
                    }
                }
            }

            stage('Update TestRails') {
                when {
                    expression { params.REPORT_TEST == true }
                }
                steps {
                    sshagent(credentials: ['st-github-builds']) {
                        script {
                            automationEnv.updateFloweeTestReport()
                        }
                    }
                }
                post {
                    failure {
                        echo 'Build failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)

                        script {
                            automationEnv.sendSlackFailure()
                        }
                    }
                }
            }

            stage('Automation Suite') {
                steps {
                    echo '==> Executing Automation Suite'

                    sh "cat ${JSON_DIR}release.json; cat ${JSON_DIR}hotfix.json"

                    script {
                        for (suite in conf.suites) {
                            sh "./gradlew :clean${suite}Suite :${suite}Suite -s -PmaxParallelTests=${MAX_THREADS} --max-workers=${MAX_THREADS}"
                        }
                    }
                }
                post {
                    always {
                        //In case of failing tests, gradle suite task always return `ERROR`, junit is running under `always` post action to ensure test reports.
                        junit '**/build/test-results/**/*.xml'
                    }
                    failure {
                        echo 'Build failed!'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)

                        script {
                            automationEnv.sendSlackFailure()
                        }
                    }
                }
            }

            stage ('JaCoCo Coverage Report') {
                steps {
                    echo '==> Publishing JaCoCo coverage report'

                    sh "./gradlew -s jacocoTestReport"

                    jacoco(execPattern: '**/**.exec', sourcePattern: '**/src/main/java')
                }
                post {
                    failure {
                        echo 'Failed to publish JaCoCo report'
                        //noinspection GroovyAssignabilityCheck
                        emailNotification('failed', conf)

                        script {
                            automationEnv.sendSlackFailure()
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
                script {
                    automationEnv.sendSlackSuccess()
                }
                echo "It's all good."
            }
            failure {
                echo 'Not so good.'
            }
        }
    }
}