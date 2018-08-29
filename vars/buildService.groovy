#!/usr/bin/env groovy


/**
 * buildService.groovy
 *
 * Call operator that returns a method to build the given project
 *
 * Currently supported project types are: gradle, maven, python
 *
 * @param String projectType
 * @param Map conf
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String projectType, Map conf) {
    return "$projectType"(conf)
}

/**
 * Build Maven projects
 *
 * @param Map conf
 *
 * @return
 */
def maven(Map conf) {
    additionalBuildOpts = conf.additionalBuildOpts ?: ''
    subProject = conf.containsKey('subproject') ? "-f ${conf.subproject}/pom.xml" : ""

    withMaven (maven: 'Maven 3.5.3', options: [ artifactsPublisher(disabled: true) ]) {
        sh "mvn ${additionalBuildOpts} ${subProject} -DskipTests clean package"
    }
}

/**
 * Build Gradle projects
 *
 * @param Map conf
 *
 * @return
 */
def gradle(Map conf) {
    gradleTask = conf.gradleTask ?: 'assemble'
    additionalBuildOpts = conf.additionalBuildOpts ?: ''
    subproject = conf.containsKey('subproject') ? ":${conf.subproject}:" : ""

    sh "./gradlew -s ${additionalBuildOpts} clean ${subproject}${gradleTask}"
}

/**
 * Package Python projects
 *
 * @param Map conf
 *
 * @return
 */
def python(Map conf) {
    pythonFiles = stageEnv.getPythonFilesToSearch(conf.pythonProjectFiles)
    jobName = stageEnv.getJobBaseName(env.JOB_NAME)
    subproject = conf.subproject ?: ""

    sh "find ${subproject} \\( ${pythonFiles} \\) -print | zip -r ${jobName}.zip -@"
}