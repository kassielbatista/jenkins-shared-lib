#!/usr/bin/env groovy

import groovy.transform.Field
import com.squaretrade.deploy.model.DataWarehouse
import com.squaretrade.deploy.model.QASlackNotification
import hudson.tasks.test.AbstractTestResultAction
import com.squaretrade.deploy.model.Tag

/**
 * Call operator to get around issue where methods cannot be called within 'parameters' declarative pipeline section
 *
 * @param method
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String method) {
    return "$method"()
}

/**
 * Call operator to get around issue where methods cannot be called within 'parameters' declarative pipeline section
 *
 * @param method
 * @param conf
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String method, args) {
    return "$method"(args)
}

/**
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def environments() {
    return libraryResource("com/squaretrade/preproductionEnvironments.txt")
}

/**
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def environments(Map conf) {
    def environment = (conf.containsKey("environmentList")) ? conf.environmentList : 'preproduction'
    return libraryResource("com/squaretrade/${environment}Environments.txt")
}

/**
 * Returns the list of environments excluding the ones that exist in excludedAutomationPreProductionEnvironments.txt
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getAutomationEnvironments() {
    def currentEnvironments = environments().split("\n").collect()

    def excludedEnvironments = libraryResource('com/squaretrade/excludedAutomationPreProductionEnvironments.txt').split("\n").collect()

    currentEnvironments.removeAll(excludedEnvironments)

    return currentEnvironments.join("\n")
}

/**
 * Returns the list of environments where dwhscripts can run
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getDwhscriptsEnvironments() {
    return libraryResource('com/squaretrade/dwhscriptsEnvironments.txt')
}

/**
 *  Returns a list of Stage operations based on conf
 *
 * @param conf Map of parameters.  The following are required:
 * <ul>
 *     <li>disableTests</li>
 *     <li>disableDbUpdates</li>
 * </ul>
 *
 * @return String list of operation parameters
 */
@SuppressWarnings("GrMethodMayBeStatic")
def operations(Map conf) {

    def actions = actions(conf)
    def commands = commands(conf)

    // define our overall 'operation' list (actions & commands)
    return actions.join('\n') + '\n' + commands.join('\n')
}

@SuppressWarnings("GrMethodMayBeStatic")
def dwhscriptsOperations() {
    def operations = ['NONE', 'policy-scan+deploy+tag', 'deploy+tag', 'deploy', 'policy-scan']

    return operations.join('\n')
}

@SuppressWarnings("GrMethodMayBeStatic")
def actions(Map conf) {

    // Define the default 'action' list
    def actions = ['NONE', 'build'] as LinkedHashSet<String>

    // add 'compound actions'
    // e.g. build+test+update-db, build+test+update-db+deploy, build+test+update-db+deploy+rollingRestart
    def optionalActions = ''
    if (!isTestsDisabled(conf)) {
        optionalActions = '+test'
        // have to use .toString() here to convert GString into a String so that dups are removed from action set
        actions.add("build${optionalActions}".toString())
    }
    if (!isLiquibaseDisabled(conf)) {
        optionalActions += '+update-db'
    }

    // have to use .toString() here to convert GString into a String so that dups are removed from action set
    actions.add("build${optionalActions}".toString())
    actions.add("build${optionalActions}+deploy".toString())

    if (conf.projectType != 'maven') {
        actions.add("build${optionalActions}+deploy+rollingRestart".toString())
    }

    return actions
}

@SuppressWarnings("GrMethodMayBeStatic")
def commands(Map conf) {

    // define the default 'command' list
    def commands = [] as LinkedHashSet<String>

    if (conf.projectType != 'maven'){
        commands.add('status')
        commands.add('rollingRestart')
        commands.add('start')
        commands.add('stop')
    }

    if (!isLiquibaseDisabled(conf)) {
        commands.add('update-db')
    }
    return commands
}

@SuppressWarnings("GrMethodMayBeStatic")
def isTestsDisabled(Map conf) {
    return conf.containsKey('disableTests') && conf.disableTests
}

@SuppressWarnings("GrMethodMayBeStatic")
def isLiquibaseDisabled(Map conf) {
    return conf.containsKey('disableDbUpdates') && conf.disableDbUpdates
}

@SuppressWarnings("GrMethodMayBeStatic")
def mavenTomcatDeploy(Map conf) {
    return conf.mavenTomcatDeploy ?: false
}

@SuppressWarnings("GrMethodMayBeStatic")
def datawarehouseCredentialsId(String env) {
    return 'datawarehouse-tomcat-manager-'+env
}

@SuppressWarnings("GrMethodMayBeStatic")
def defaultDbCredentialsId() {
    return 'stage-db-admin-password'
}

@SuppressWarnings("GrMethodMayBeStatic")
def defaultTagType(Map conf) {
    return conf.tagType ?: "build"
}

@SuppressWarnings("GrMethodMayBeStatic")
def createTag(Map conf) {
    return conf.createTag ?: false
}

@SuppressWarnings("GrMethodMayBeStatic")
def defaultScanType(Map conf) {
    return conf.veracodeScanType ?: "SANDBOX"
}

@SuppressWarnings("GrMethodMayBeStatic")
def defaultScanMethod(Map conf) {
    return conf.veracodeMethodType ?: "SERIAL"
}

@SuppressWarnings("GrMethodMayBeStatic")
def isPromoteEnabled(Map conf) {
    return !(conf.containsKey('isPromote') && conf.isPromote == false) ?: false
}

/**
 * Returns true if the environment param must be set
 *
 * @param environment
 * @param operation
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def isEnvironmentParamSet(String environment, String operation) {

    if (operation in ['build', 'build+test', 'policy-scan']) {
        return true
    }
    else if (environment != 'NONE') {
        return true
    }
    return false
}

/**
 * Returns true if the given pipeline configuration has at least one veracode application defined
 *
 * @param conf
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
boolean canVeracodeScan(Map conf) {
    conf?.veracodeApps?.getAt(0)?.appName
}

/**
 *
 * @param conf pipeline configuration
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getDefaultBranch(Map conf) {
    if (conf.containsKey("veracodeScanType") && conf.veracodeScanType == 'POLICY')
        return 'rc-'
    else
        return conf.defaultBranch ?: 'master'
}

@SuppressWarnings("GrMethodMayBeStatic")
def getDefaultMailList(Map conf) {
    return conf.notificationMailAddr ?: 'engineers@squaretrade.com, qa@squaretrade.com'
}

/**
 *
 * @param conf pipeline configuration
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getDefaultGitTagState(Map conf) {
    if (conf.containsKey("veracodeScanType") && conf.veracodeScanType == 'POLICY')
        return true
    else
        return false
}

/**
 * Get the file extensions that should be compressed for Veracode scan (Python projects only)
 *
 * @param List<String> filesToSearch
 * @return
 */
def getPythonFilesToSearch(List<String> filesToSearch = []) {
    extensions = filesToSearch ?: ['.py', '.html', '.js', '.css']

    pythonFilesToSearch = ''
    for (extension in extensions) {
        if (extension == extensions.last()) {
            pythonFilesToSearch = pythonFilesToSearch + "-name '*${extension}'"
        }
        else {
            pythonFilesToSearch = pythonFilesToSearch + "-name '*${extension}' -o "
        }
    }

    return pythonFilesToSearch
}

/**
 * Returns the job base name
 * @param jobName
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getJobBaseName(jobName) {
    "${jobName}".split('/').last()
}

/**
 * Tag a git repo
 *
 * @param String tagName
 * @param String jobName
 * @param Integer buildNumber
 * @return
 */
def gitTag(String tagName, String jobName, Integer buildNumber) {
    sh "git tag -a ${tagName} -m Generated by Cloudbees ${getJobBaseName(jobName)} job: ${buildNumber}'"

    sh "git push origin --tags"
}

/**
 * Tag creation step
 *
 * @param environment
 * @param conf
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def tagCreation(String tagType, String mailAddr) {
    try {
        Tag tag = new Tag(tagType, mailAddr, this)
        tag.buildProperties()

        return tag.handleTagging()
    } catch (RuntimeException e) {
        error e.message
    }
}

/**
 * Validates if policy scan should be automatically triggered
 *
 * @param params
 * @return
 */
boolean isAutomaticPolicyScan(Map params) {
    if (!params.GIT_IS_TAG) {
        return false
    }

    validOperations = [
            'build',
            'test',
            'deploy'
    ]

    for (operation in validOperations) {
        if (!(operation in params.OPERATION.split("\\+"))) {
            return false
        }
    }

    if (!(params.ENVIRONMENT.toLowerCase().matches("stage[0-9]+") || params.ENVIRONMENT.toLowerCase().matches("hotfix"))) {
        return false
    }

    if (!params.GIT_BRANCH_OR_TAG.startsWith("rc-")) {
        return false
    }

    return true
}

/**
 * Data Warehouse deploy step
 *
 * @param remoteHostname
 * @param backupScriptFilename dwhscripts_backup.sh
 * @param steps pass in 'this'
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def dwhscriptsDeploy(String remoteHostname, String backupScriptFilename, String deployTargetDir, def steps) {
    DataWarehouse.deploy(remoteHostname, deployTargetDir, backupScriptFilename, 'dwhscripts', 'jenkins-dwhscripts', steps)
}
