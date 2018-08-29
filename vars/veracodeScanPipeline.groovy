#!/usr/bin/env groovy
/**
 * veracodeScanPipeline.groovy
 *
 * A Jenkins declarative pipeline for Veracode sandbox scanning project build artifacts
 *
 * Required conf values
 *   - gitRepo - a microservice repo (example: Engineering/mint-das)
 *
 * Optional conf values
 *   - projectType - The type of project you want to build (default: 'gradle')
 *   - cron - cron schedule to run
 *   - defaultBranch - Alternative default branch to build out of (default: 'master')
 *   - timeout.time - Timeout time for the job (default = 30)
 *   - timeout.unit - Timeout units for the job (default = MINUTES)
 *   - gitCredentialsId - alternate Jenkins Credentials ID (default: 'st-github-builds')
 *   - disableEmail - set to true if email notifications should be disabled (default: false)
 *   - notificationEmailAddr - specify an alternate address for email notifications (default: builds@squaretade.com)
 *   - subproject - specify if <code>gitRepo</code> has multiple microservices (example: warranty-migration-consumer)
 *   - gradleTask - Alternative Gradle task to build upload artifacts (default: 'assemble')
 *   - additionalBuildOpts - Additional config option for the build step
 *   - pythonProjectFiles - Optional list of extensions to search when scanning Python projects (default: ['.py', '.html', '.js', '.css'])
 *   - nodeToRun - Specify the node where the job should run (example: slavebee1)
 *   - isPromote - Promote the Sandbox scan to a Policy scan (default: true)
 *   - veracodeScanType - POLICY or SANDBOX (default: SANDBOX)
 *   - veracodeMethodType - SERIAL or PARALLEL (default: SERIAL)
 *   - veracodeApps - A list of Veracode applications to scan.  Each application has the following configuration map options:
 *      - preBuild - Specify a command line that will be used to build a custom artifact specifically for Veracode policy scanning
 *      - appName - Veracode application name (example: mint-das)
 *      - sandboxName - Veracode sandbox name (default: 'Cloudbees Sandbox')
 *      - credentialsId - Veracode credentials ID (default: '5021cb1f-98a3-4d5b-af99-d6adf29a8d66')
 *      - includesPattern - Veracode upload includes pattern (default: 'build/libs/*.jar')
 *      - excludesPattern - Veracode upload excludes pattern (default: 'build/libs/*sources.jar,build/libs/*javadoc.jar')
 *      - timeout -  Optional time to wait in minutes for the scan to complete (example: 60).
 *      - uploadFrom: - The location within the workspace to upload artifacts from (default: '.')
 *
 * @param conf
 */
def call(Map conf) {

    pipeline {
        agent {
            label "${conf.nodeToRun ?: ''}"
        }
        options {
            disableConcurrentBuilds()
            timeout(conf?.timeout ?: [time: 30, unit: 'MINUTES'])
            skipDefaultCheckout(true)
        }
        triggers {
            cron(conf?.cron ?: '')
        }
        parameters {
            string(name: 'GIT_BRANCH_OR_TAG', defaultValue: stageEnv('getDefaultBranch', conf),
                    description: 'Git branch or tag name to build. For Policy Scan, normally this should be a release candidate tag.')
            booleanParam(name: 'GIT_IS_TAG', defaultValue: stageEnv('getDefaultGitTagState', conf), description: 'Check if this is a tag name')
        }
        environment {
            // Disable the gradle daemon for all stages
            GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        }
        stages {

            stage('Setup') {
                steps {
                    script {
                        // verify required config items are passed in
                        def requiredParams = ['gitRepo', 'veracodeApps', 'veracodeApps[0].appName']
                        //noinspection GroovyAssignabilityCheck
                        assertRequiredConfParams(conf, requiredParams)

                        // update job information
                        currentBuild.displayName = "#${currentBuild.number} ${params.GIT_BRANCH_OR_TAG}"
                        //noinspection GroovyAssignabilityCheck
                        describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/veracodeScanPipeline.groovy')

                        // determine which node we are running on
                        //noinspection GroovyAssignabilityCheck
                        WORK_NODE_LABEL = getNode(env)
                        echo "Working on ${WORK_NODE_LABEL}"

                        PROJECT_TYPE = conf.projectType ?: "gradle"

                        VERACODE_SCAN_TYPE = stageEnv.defaultScanType(conf)

                        VERACODE_SCAN_METHOD = stageEnv.defaultScanMethod(conf)

                    }
                }
            }

            stage('Checkout') {
                steps {
                    echo "==> Checking out ${params.GIT_BRANCH_OR_TAG} from ${conf.gitRepo} into ${conf.gitRepo} subdirectory..."

                    dir(conf.gitRepo) {
                        //noinspection GroovyAssignabilityCheck
                        checkoutGitBranchOrTag url: "git@github.squaretrade.com:${conf.gitRepo}.git",
                                credentialsId: conf.gitCredentialsId,
                                branchOrTag: params.GIT_BRANCH_OR_TAG,
                                isTag: params.GIT_IS_TAG,
                                shallowClone: params.GIT_IS_TAG ? false : true
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

            stage('Veracode Sandbox Scan') {
                when {
                    expression { VERACODE_SCAN_TYPE == "SANDBOX" &&
                            stageEnv.canVeracodeScan(conf) }
                }                
                steps {
                    script {
                        veracodeScan.steps(conf, params.GIT_BRANCH_OR_TAG as String, env.BUILD_NUMBER as String, VERACODE_SCAN_TYPE, VERACODE_SCAN_METHOD, stageEnv.isPromoteEnabled(conf))
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

            stage('Veracode Policy Scan') {
                when {
                    expression { VERACODE_SCAN_TYPE == "POLICY" &&
                            stageEnv.canVeracodeScan(conf) }
                }
                steps {
                    script {
                        veracodeScan.steps(conf, params.GIT_BRANCH_OR_TAG as String, env.BUILD_NUMBER as String, VERACODE_SCAN_TYPE, VERACODE_SCAN_METHOD)
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
        }
    }

}