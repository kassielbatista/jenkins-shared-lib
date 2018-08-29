#!/usr/bin/env groovy

/**
 * tagCreationPipeline.groovy
 *
 * A Jenkins declarative pipeline for building Pancake-based microservices
 *
 * Optional conf values
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - defaultBranch - Alternative default branch to build out of (default: 'master')
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: builds@squaretade.com)
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - commandTimeout - Set the timeout in seconds to stage COMMAND (default: 60)
 *   - localBranch - define if Cloudbees should checkout as local branch
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
            string( name: 'GIT_REPO',
                    description: 'Git repo to create the tag. e.g: Engineering/SourceRoot (required)'
            )
            //noinspection GroovyAssignabilityCheck
            string( name: 'GIT_BRANCH',
                    defaultValue: stageEnv('getDefaultBranch', conf),
                    description: 'Git branch where tag should be created. (required)')
            choice( name: 'TAG_TYPE',
                    choices: "build\nrc\nrelease",
                    description: 'The tag type that must be created. (required)'
            )
            string( name: 'MAIL_TO',
                    defaultValue: stageEnv('getDefaultMailList', conf),
                    description: "Email list to receive tag reports. \nIf not provided default will be 'engineers@squaretrade.com, qa@squaretrade.com'"
            )
        }
        stages {

            stage('Setup') {
                steps {
                    script {
                        try {
                            ArrayList requiredParams = [
                                    'GIT_REPO'
                            ]

                            assertRequiredConfParams.jobRequiredParams(params, requiredParams)
                        }
                        catch(IllegalArgumentException err) {
                            currentBuild.displayName = "#${currentBuild.number} ${err.message}"

                            error err.message
                        }

                        currentBuild.displayName = "#${currentBuild.number} ${params.GIT_BRANCH}"

                        //noinspection GroovyAssignabilityCheck
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/tagCreationPipeline.groovy')

                        // determine which node we are running on
                        //noinspection GroovyAssignabilityCheck
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        if(!conf.containsKey('notificationMailAddr')) {
                            conf.notificationMailAddr = params.MAIL_TO
                        }

                        GIT_CREDENTIALS = conf.gitCredentialsId ?: 'st-github-builds'
                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out ${params.GIT_BRANCH} from ${params.GIT_REPO}..."

                    // cloning repos in a subdirectory of the workspace result in *@tmp workspace folders getting created
                    // inside the current workspace. As a result, these folders get cleaned up by cleanupNodeWS
                    // in addition to everything else.
                    dir(params.GIT_REPO) {
                        //noinspection GroovyAssignabilityCheck
                        checkoutGitBranchOrTag url: "git@github.squaretrade.com:${params.GIT_REPO}.git",
                                credentialsId: conf.gitCredentialsId,
                                branchOrTag: params.GIT_BRANCH,
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
            stage('Tag') {
                steps {
                    echo "==> Creating TAG ..."

                    sshagent(credentials: [GIT_CREDENTIALS]) {
                        dir(params.GIT_REPO) {
                            script {
                                String createdTag = stageEnv.tagCreation(params.TAG_TYPE, conf.notificationMailAddr)
                                currentBuild.displayName += " ${createdTag}"
                                echo "Tag ${createdTag} successfully created..."
                            }
                        }
                    }
                }
                post {
                    failure {
                        echo 'TAG creation failed!'
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
