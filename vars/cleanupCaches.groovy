#!/usr/bin/env groovy

/**
 * cleanupCaches.groovy
 *
 * Cleanup Maven(.m2) and Gradle(.gradle/caches) caches
 *
 */
def call() {

    //noinspection GroovyAssignabilityCheck
    describePipelineDefinition('Pipeline: https://github.squaretrade.com/devops/jenkins-pipeline-shared-lib/blob/master/vars/cleanupCaches.groovy')
    
    for (String slave : ciEnv("getNodes")) {

        node(slave) {
            m2_folder = '.m2/repository'
            gradle_folder = '.gradle/caches'
            sh "hostname"
            sh "if [ -d $HOME/$m2_folder ]; then rm -r $HOME/$m2_folder/*; else echo 'No .m2 cache found'; fi"
            sh "if [ -d $HOME/$gradle_folder ]; then rm -r $HOME/$gradle_folder/*; else echo 'No .gradle cache found'; fi"
        }

    }

}