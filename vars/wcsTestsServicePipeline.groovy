#!/usr/bin/env groovy
/**
 * automationServicePipeline.groovy
 *
 * A Jenkins declarative pipeline to run functional test suites from the Engineering/automation repository
 *
 * Required conf values
 *   - gitRepo - a microservice repo (example: Engineering/automation)
 *   - behaviorTestsDir - the directory where the behavior tests can be found (example: 'tests/wcs/features')
 *
 * Optional conf values
 *   - testRailCredentialsId - username/password credentials that should be used to authenticate in testrail (default: 'testrail')
 *   - pythonVersion - python version that PyEnv plugin should use to create the Virtual Env. (Obs.: this will only work if the version provided is installed in Cloudbees host machine) (example: '2.7')
 *   - loadTestsDir - the directory where the behavior tests can be found (example: 'tests/wcs/load/wcs.load.yml')
 *   - cron - cron schedule to run
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - defaultBranch - Alternative default branch to build out of (default: 'master')
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: builds@squaretade.com)
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - environment - The environment where the job should run (default: NONE)
 *
 * @param conf
 * @return
 */
def call(Map conf) {
    pipeline {
        agent {
            label "${conf.nodeToRun ?: ''}"
        }

        options {
            disableConcurrentBuilds()
            timeout(conf?.timeout ?: [time: 30, unit: 'MINUTES'])
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
                    defaultValue: stageEnv('getDefaultBranch', conf),
                    description: 'Git branch or tag name to build'
            )
            booleanParam( name: 'GIT_IS_TAG',
                    defaultValue: false,
                    description: 'Check if this is a tag name'
            )
        }

        environment {
            STENV = "${params.ENVIRONMENT}"
            TESTRAIL = credentials("${conf.testRailCredentialsId ?: 'testrail'}")
        }

        stages {

            stage('Setup') {
                steps {
                    script {
                        def requiredParams = ['gitRepo', 'behaviorTestsDir']

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
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/wcsTestsServicePipeline.groovy')

                        // determine which node we are running on
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        PYTHON_VERSION = conf.pythonVersion ? "python${conf.pythonVersion}" : 'python'

                        /* This is needed to avoid using `withCredentials()` which is a scripted way of getting credentials, as per the jenkins documentation
                           if some credential is needed as an environment variable in a declarative pipeline, that should be accomplished by using `credentials()`
                           in environment block, which can be seen in this pipeline.

                           As the automation tests expect an specific var called TESTRAIL_USER and TESTRAIL_KEY it was necessary to re-assign the values to the correct vars.
                           //TODO: check with john correa if the environment var can be chaged to TESTRAIL_USR and TESRAIL_PSW, by doing that we can avoid these variables re-assignment.
                        */
                        env.TESTRAIL_USER = env.TESTRAIL_USR
                        env.TESTRAIL_KEY = env.TESTRAIL_PSW
                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out ${params.GIT_BRANCH_OR_TAG} from ${conf.gitRepo}..."

                    checkoutGitBranchOrTag url: "git@github.squaretrade.com:${conf.gitRepo}.git",
                            credentialsId: conf.gitCredentialsId,
                            branchOrTag: params.GIT_BRANCH_OR_TAG,
                            isTag: params.GIT_IS_TAG,
                            submodules: true
                }
                post {
                    failure {
                        echo 'Checkout failed!'

                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Installing Dependencies') {
                steps {
                    echo '==> Executing dependencies installation...'

                    withPythonEnv(PYTHON_VERSION) {
                        pysh "pip install -r requirements.txt"

                    }
                }
                post {
                    failure {
                        echo 'Requirements failed to be installed...'

                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Behavior Tests') {
                steps {
                    echo '==> Executing Behavior tests...'

                    withPythonEnv(PYTHON_VERSION) {
                        pysh "behave ${conf.behaviorTestsDir}"
                    }
                }
                post {
                    //TODO: check with @jcorrea if behave is generating the test reports to the folder 'testReports/behavior'
                    //always {}

                    failure {
                        echo 'Behavior tests failed to execute...'

                        emailNotification('failed', conf)
                    }
                }
            }

            stage('Load tests') {
                when {
                    expression {
                        conf.containsKey('loadTestsDir')
                    }
                }
                steps {
                    withPythonEnv(PYTHON_VERSION) {
                        pysh "bzt ${conf.loadTestsDir}"
                    }
                }
                post {
                    //TODO: check with @jcorrea if bzt is generating the test reports to the folder 'testReports/load'
                    //always{}

                    failure {
                        echo "Load tests failed to execute..."

                        emailNotification('failed', conf)
                    }
                }
            }
        }
        post {
            always {
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
                echo "It's all good."
            }
            failure {
                echo 'Not so good.'
            }
        }
    }
}