@Library('devops-jenkins-pipeline-shared-library')_

Map conf = [
        notificationEmailAddr: "ron@squaretrade.com,louis@squaretrade.com,kbatista@squaretrade.com,fribeiro@squaretrade.com"
]

pipeline {
    agent any
    triggers {
        cron('H 17 * * 1-5')
    }
    environment {
        GRADLE_OPTS='-Dorg.gradle.daemon=false'
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('Build and Unit Test') {
            steps {
                echo '==> Building jenkins-pipeline-shared-lib...'
                sh './gradlew clean test'
                junit '**/build/test-results/**/TEST-*.xml'
            }
            post {
                failure {
                    echo 'Build failed!'
                    emailNotification('failed', conf)
                }
            }
        }
    }
    post {
        always {
            //noinspection GroovyAssignabilityCheck
            cleanupNodeWS(getNode(env))
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
