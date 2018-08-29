#!/usr/bin/env groovy
import com.squaretrade.deploy.model.VeracodeScanMethodEnum
import com.squaretrade.deploy.model.VeracodeScanTypeEnum
import com.squaretrade.deploy.model.VeracodeApi

/**
 * veracodeSandboxScan.groovy
 *
 * Upload build artifacts and run a Veracode static scan in a sandbox
 *
 * @param vConf.appName - Veracode application name (example: mint-das)
 * @param vConf.sandboxName - Veracode sandbox name.  Set to empty string or null to perform a Policy scan (default: 'Cloudbees Sandbox')
 * @param vConf.credentialsId - Veracode credentials ID (default: '5021cb1f-98a3-4d5b-af99-d6adf29a8d66')
 * @param vConf.includesPattern - Veracode upload includes pattern (default: 'build/libs/*.jar')
 * @param vConf.excludesPattern - Veracode upload excludes pattern (example: 'build/libs/*sources.jar,build/libs/*javadoc.jar')
 * @param vConf.timeout - Optional time to wait in minutes for the scan to complete (example: 60).
 * @param scanType - type of scan
 * @param scanName - name of the scan
 * @param deleteBuildBeforeScan
 * @param isDebug true for debug output
 *
 */
def call(Map vConf, String scanName, VeracodeScanTypeEnum scanType = VeracodeScanTypeEnum.SANDBOX,
         Boolean deleteBuildBeforeScan = false, Boolean isDebug = false, Boolean isPromote) {

    def vParams = [
            applicationName: vConf.appName,
            scanName: scanName,
            uploadIncludesPattern: vConf.includesPattern ?: 'build/libs/*.jar',
            canFailJob: true,
            criticality: 'Medium',
            debug: isDebug
    ] as Map

    // Setup for sandbox scan.  Nothing additional needs to be done for policy scans.
    if (scanType == VeracodeScanTypeEnum.SANDBOX) {
        vParams << [
                createSandbox: true,
                sandboxName: vConf.sandboxName ?: 'Cloudbees Sandbox'
        ]
    }

    if (vConf.excludesPattern) {
        vParams.put('uploadExcludesPattern', vConf.excludesPattern)
    }

    if (vConf.timeout) {
        vParams.put('timeout', vConf.timeout)
    }

    def vCredentialsId = vConf.credentialsId ?: '5021cb1f-98a3-4d5b-af99-d6adf29a8d66'

    withCredentials([usernamePassword(credentialsId: "${vCredentialsId}", passwordVariable: 'veracode_key', usernameVariable: 'veracode_id')]) {

        vParams << [
                useIDkey: true,
                vid     : "${veracode_id}",
                vkey    : "${veracode_key}"
        ]

        if (isDebug) {
            echo "Veracode plugin parameters: ${vParams}"
        }

        VeracodeApi vApi = new VeracodeApi(vParams.vid as String, vParams.vkey as String, this, isDebug)

        String appName = vParams.applicationName
        String sandboxName = scanType == VeracodeScanTypeEnum.SANDBOX ? vParams.sandboxName : null

        if (deleteBuildBeforeScan) {

            if (sandboxName) {
                echo "Determining if the last build of '${appName}' in sandbox '${sandboxName}' needs to be deleted..."
            } else {
                echo "Determining if the last build of '${appName}' needs to be deleted..."
            }

            vApi.deleteLastBuildIfRequiredForFileUpload(appName, sandboxName)
        }
		
        // upload build artifacts and scan
        veracode vParams

        boolean scanHealthy = vApi.isScanHealthy(appName, sandboxName, scanName)

        if (!scanHealthy) {

            echo "==> '${appName}' Sandbox scan could not be promoted to Policy scan because it is not in a healthy state."
            currentBuild.result = 'FAILURE'
            error('Veracode scan failed!')

        } else {

            if (isPromote && VeracodeScanTypeEnum.SANDBOX && vConf.containsKey('timeout')) {
                boolean promoteAllowed = vApi.isCurrentBuild(appName, sandboxName, scanName)
                if (promoteAllowed) {
                    echo "==> Promote '${appName}' Sandbox scan to Policy scan ..."
                    vApi.promoteScan(appName, sandboxName)
                } else {
                    echo "==> '${appName}' Sandbox scan could not be promoted to Policy scan. The current build is not in a valid state."
                }

            }

        }

    }

}

/**
 * Pipeline steps for performing a veracode scan. Call like this:
 * <pre>
 *  steps {
 *      script {
 *          veracodeScan.steps(conf, params.GIT_BRANCH_OR_TAG as String, env.BUILD_NUMBER as String, 'POLICY')
 *      }
 *  }
 * </pre>
 *
 * NOTE that <code>scanTypeStr</code> is type String to avoid having to import VeracodeScanTypeEnum in a pipeline
 * which makes the Jenkins pipeline linter unhappy.
 *
 * @param conf.gitRepo repo name
 * @param conf.veracodeApps veracode app configuration
 * @param branchOrTagName branch or tag name
 * @param buildNumber build number
 * @param scanTypeStr VeracodeScanTypeEnum string value
 *
 */
def steps(Map conf, String branchOrTagName, String buildNumber, String scanTypeStr = VeracodeScanTypeEnum.SANDBOX as String, String scanMethodStr = VeracodeScanMethodEnum.SERIAL as String, Boolean isPromoteEnabled = true) {

    VeracodeScanTypeEnum scanType = scanTypeStr as VeracodeScanTypeEnum
    VeracodeScanMethodEnum scanMethod = scanMethodStr as VeracodeScanMethodEnum

    echo "==> Uploading and Veracode scanning ${conf.gitRepo} artifacts..."
    def scanName = "${conf.gitRepo}-${branchOrTagName}-build-${buildNumber}-${scanTypeStr}"

    def veracodeScans = conf.veracodeApps.collectEntries { Map vConf->
        [(vConf.appName) : {
            dir(vConf.uploadFrom ?: conf.gitRepo) {
                if (vConf.containsKey('preBuild')) {
                    echo "==> Pre-build execution ..."
                    sh vConf.preBuild       
                }
                this.call(vConf, scanName, scanType, true, false, isPromoteEnabled)
            }
            echo "Upload of ${vConf.appName} complete."
        }]
    }

    if (scanMethod == VeracodeScanMethodEnum.PARALLEL) {
        echo "Executing in parallel"
        parallel veracodeScans
    } else {
        veracodeScans.each { stepName, step ->
            echo "Executing ${stepName}"
            step()
        }
    }

}

/**
 * Pipeline post/failure steps for Veracode scan. Call like this:
 * <pre>
 *      post {
 *          failure {
 *              script {
 *                  veracodeScan.postFailure(conf, currentBuild)
 *              }
 *          }
 *      }
 * </pre>
 * @param conf pipeline conf
 * @param currentBuild pipeline currentBuild
 *
 */
def postFailure(Map conf, def currentBuild) {
    def errmsg = "#${currentBuild.number} Veracode scan failed!"
    echo errmsg
    currentBuild.displayName = errmsg
    emailNotification.call('failed', conf)
}