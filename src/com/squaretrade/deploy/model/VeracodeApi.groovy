package com.squaretrade.deploy.model

import com.cloudbees.groovy.cps.NonCPS
import com.veracode.apiwrapper.wrappers.SandboxAPIWrapper
import com.veracode.apiwrapper.wrappers.UploadAPIWrapper

/**
 * Jenkins shared library for Veracode API calls
 *
 * Handles Jenkins shared library complexities related to {@link Serializable} and {@link NonCPS}
 *
 */
class VeracodeApi implements Serializable {

    String vId
    String vKey
    boolean isDebug = false
    def steps = null

    transient UploadAPIWrapper uApi
    transient SandboxAPIWrapper sbApi

    /**
     *
     * @param vId
     * @param vKey
     */
    VeracodeApi(String vId, String vKey) {
        this.vId = vId
        this.vKey = vKey
    }

    /**
     *
     * @param vId
     * @param vKey
     * @param steps
     * @param isDebug
     */
    VeracodeApi(String vId, String vKey, def steps, boolean isDebug) {
        this(vId, vKey)
        this.steps = steps
        this.isDebug = isDebug
    }

    /**
     * Checks the last build status for the given <code>appName</code>/<code>sandBoxName</code>.
     * and if a new build artifact cannot be uploaded, the last build is deleted.
     *
     * This allows another scan to take place if a scan is still in progress or if a previous file upload
     * failed due to network issues.
     *
     * @param appName
     * @param sandboxName
     */
    void deleteLastBuildIfRequiredForFileUpload(String appName, String sandboxName) {
        String appId = getAppId(appName)
        String sandboxId = getSandboxId(appId, sandboxName)
        String lastBuildInfoXml = getLastBuildInfo(appId, sandboxId)
        debug(lastBuildInfoXml)
        String buildStatus = getBuildStatus(lastBuildInfoXml)
        debug("Build status: ${buildStatus}")
        if (!canUploadNewBuild(buildStatus)) {
            info "Deleting last Vercode scan for '${appName}' because it's current status is '${buildStatus}'..."
            String xml = deleteLastBuild(appId, sandboxId)
            debug(xml)
        }
    }

    /**
     * Returns a Veracode application id for the given name
     *
     * @param appName
     * @return
     */
    String getAppId(String appName) {
        debug("Searching for appId for appName=${appName}")
        String xml = getAppList()
        debug(xml)
        getAppIdFromName(xml, appName)
    }


    /**
     * Returns a Veracode sandbox id for the given application id and sandbox name
     *
     * @param appId
     * @param sandboxName
     * @return
     */
    String getSandboxId(String appId, String sandboxName) {
        debug("Searching for sandboxId for appId=${appId}, sandboxName=${sandboxName}")
        String xml = getSandboxList(appId)
        debug(xml)
        getSandboxIdFromName(xml, sandboxName)
    }


    /**
     * Returns the latest build information for the given application and sandbox ids
     * @param appId
     * @param sandboxId
     * @return xml api result
     */
    @NonCPS
    String getLastBuildInfo(String appId, String sandboxId) {
        // FIXME: handle IOException
        getUploadApi().getBuildInfo(appId, null, sandboxId)
    }

    /**
     * Gets the build version of from <code>buildInfoXml</code>.  This must be @NonCPS due to use of {@link XmlSlurper}
     *
     * @param buildInfoXml
     * @return build version
     * @see <a href="https://help.veracode.com/reader/LMv_dtSHyb7iIxAQznC~9w/fXjoSQTxTaO8~rDBRm6HUg">API Build Version Information</a>
     */
    @NonCPS
    static String getBuildVersion(String buildInfoXml) {
        def xml = new XmlSlurper().parseText(buildInfoXml)
        xml.build.@version.toString()
    }

    /**
     * Validate if the last build version for the given <code>appName</code>/<code>sandBoxName</code>
     * has the same scanName being scanned
     *
     *
     * @param appName
     * @param sandboxName
     * @param scanName
     */
    boolean isCurrentBuild(String appName, String sandboxName, String scanName) {
        String appId = getAppId(appName)
        String sandboxId = getSandboxId(appId, sandboxName)
        String lastBuildInfoXml = getLastBuildInfo(appId, sandboxId)
        String buildVersion = getBuildVersion(lastBuildInfoXml)

        return buildVersion == scanName
    }


    /**
     * Validate if the current scan has failed
     *
     *
     * @param appName
     * @param sandboxName
     * @param scanName
     */
    boolean isScanHealthy(String appName, String sandboxName, String scanName) {
        String appId = getAppId(appName)
        String sandboxId = getSandboxId(appId, sandboxName)
        String lastBuildInfoXml = getLastBuildInfo(appId, sandboxId)
        String buildStatus = getBuildStatus(lastBuildInfoXml)
        String buildVersion = getBuildVersion(lastBuildInfoXml)
        Boolean validStatus = sandboxName == null ? canUploadNewPolicyBuild(buildStatus) : canUploadNewBuild(buildStatus)

        return buildVersion == scanName && validStatus
    }

    /**
     * Gets the build status of from <code>buildInfoXml</code>.  This must be @NonCPS due to use of {@link XmlSlurper}
     *
     * @param buildInfoXml
     * @return build status
     * @see <a href="https://help.veracode.com/reader/LMv_dtSHyb7iIxAQznC~9w/fXjoSQTxTaO8~rDBRm6HUg">API Build Status Information</a>
     */
    @NonCPS
    static String getBuildStatus(String buildInfoXml) {
        def xml = new XmlSlurper().parseText(buildInfoXml)
        //debug(xml)
        xml.build.analysis_unit.@status.toString()
    }

    /**
     * Gets the build id of from <code>buildInfoXml</code>.  This must be @NonCPS due to use of {@link XmlSlurper}
     *
     * @param buildInfoXml
     * @return build id
     * @see <a href="https://help.veracode.com/reader/LMv_dtSHyb7iIxAQznC~9w/fXjoSQTxTaO8~rDBRm6HUg">API Build Status Information</a>
     */
    @NonCPS
    static String getCurrentBuildId(String buildInfoXml) {
        def xml = new XmlSlurper().parseText(buildInfoXml)
        xml.build.@build_id.toString()
    }

    /**
     *
     * Returns true if the given buildStatus indicates that a new file can be uploaded for scanning
     *
     * @see <a href="https://help.veracode.com/reader/LMv_dtSHyb7iIxAQznC~9w/fXjoSQTxTaO8~rDBRm6HUg">API Build Status Information</a>
     *
     * @param buildStatus
     * @return
     */
    static boolean canUploadNewBuild(String buildStatus) {
        return buildStatus == VeracodeStatusTypeEnum.RESULTS_READY.toString()
    }
    
    /**
     *
     * Returns true if the given buildStatus indicates that a new file can be uploaded for scanning
     *
     * @see <a href="https://help.veracode.com/reader/LMv_dtSHyb7iIxAQznC~9w/fXjoSQTxTaO8~rDBRm6HUg">API Build Status Information</a>
     *
     * @param buildStatus
     * @return
     */
    static boolean canUploadNewPolicyBuild(String buildStatus) {
		if (buildStatus.equals(VeracodeStatusTypeEnum.PRESCAN_SUBMITTED.toString()) || buildStatus.equals(VeracodeStatusTypeEnum.RESULTS_READY.toString()))
			return true
		else
			return false
    }    

    /**
     *
     * @param appId
     * @param sandboxId
     * @return xml api result
     */
    @NonCPS
    String deleteLastBuild(String appId, String sandboxId) {
        getUploadApi().deleteBuild(appId, sandboxId)
    }

    /**
     * This must be @NonCPS due to use of {@link UploadAPIWrapper}
     *
     * @return
     */
    @NonCPS
    private String getAppList() {
        // FIXME: handle IOException
        getUploadApi().getAppList()
    }

    /**
     * This must be @NonCPS due to use of {@link XmlSlurper} and also to avoid strange errors
     * when iterating with @fields like:
     * <pre>
     *   groovy.lang.MissingFieldException: No such field: app_id for class: java.lang.Boolean
     *      at groovy.lang.MetaClassImpl.getAttribute(MetaClassImpl.java:2846)
     *      at groovy.lang.MetaClassImpl.getAttribute(MetaClassImpl.java:3782)
     *      at org.codehaus.groovy.runtime.InvokerHelper.getAttribute(InvokerHelper.java:149)
     *      at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.getField(ScriptBytecodeAdapter.java:306)
     *      at com.cloudbees.groovy.cps.sandbox.DefaultInvoker.getAttribute(DefaultInvoker.java:48)
     *      at com.cloudbees.groovy.cps.impl.AttributeAccessBlock.rawGet(AttributeAccessBlock.java:20)
     *      at com.squaretrade.deploy.model.VeracodeApi.getAppId(file:/var/lib/jenkins/jobs/Veracode/jobs/Sandbox/jobs/tax-engine/builds/205/libs/devops-jenkins-pipeline-shared-library/src/com/squaretrade/deploy/model/VeracodeApi.groovy:59)
     * 	</pre>
     *
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-26481?focusedCommentId=271399&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-271399">[JENKINS-26481] Mishandling of binary methods accepting Closure - Jenkins JIRA</a><br/>
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-50736?focusedCommentId=335898&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-335898">[JENKINS-50736] Calling another vars step in inheritance context fails with MissingMethodException - Jenkins JIRA</a><br/>
     *
     * @param xml
     * @param appName
     * @return
     */
    @NonCPS
    private static String getAppIdFromName(String xml, String appName) {
        def appList = new XmlSlurper().parseText(xml)

        appList.app.find { it ->
            it.@app_name.toString() == appName
        }.@app_id
        // FIXME: cache appId
    }

    /**
     * This must be @NonCPS due to use of {@link XmlSlurper} and also to avoid strange errors
     * when iterating with @fields like:
     * <pre>
     *   groovy.lang.MissingFieldException: No such field: app_id for class: java.lang.Boolean
     *      at groovy.lang.MetaClassImpl.getAttribute(MetaClassImpl.java:2846)
     *      at groovy.lang.MetaClassImpl.getAttribute(MetaClassImpl.java:3782)
     *      at org.codehaus.groovy.runtime.InvokerHelper.getAttribute(InvokerHelper.java:149)
     *      at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.getField(ScriptBytecodeAdapter.java:306)
     *      at com.cloudbees.groovy.cps.sandbox.DefaultInvoker.getAttribute(DefaultInvoker.java:48)
     *      at com.cloudbees.groovy.cps.impl.AttributeAccessBlock.rawGet(AttributeAccessBlock.java:20)
     *      at com.squaretrade.deploy.model.VeracodeApi.getAppId(file:/var/lib/jenkins/jobs/Veracode/jobs/Sandbox/jobs/tax-engine/builds/205/libs/devops-jenkins-pipeline-shared-library/src/com/squaretrade/deploy/model/VeracodeApi.groovy:59)
     * 	</pre>
     *
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-26481?focusedCommentId=271399&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-271399">[JENKINS-26481] Mishandling of binary methods accepting Closure - Jenkins JIRA</a><br/>
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-50736?focusedCommentId=335898&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-335898">[JENKINS-50736] Calling another vars step in inheritance context fails with MissingMethodException - Jenkins JIRA</a><br/>
     *
     * @param xml
     * @param sandboxName
     * @return
     */
    @NonCPS
    private static String getSandboxIdFromName(String xml, String sandboxName) {
        def sandboxList = new XmlSlurper().parseText(xml)

        sandboxList.sandbox.find { it ->
            it.@sandbox_name.toString() == sandboxName
        }.@sandbox_id
        // FIXME: cache sandboxId
    }

    /**
     * This must be @NonCPS due to use of {@link UploadAPIWrapper}
     *
     * @param appId
     * @return
     */
    @NonCPS
    private String getSandboxList(String appId) {
        // FIXME: handle IOException
        getSandboxApi().getSandboxList(appId)
    }

    /**
     *
     * @param buildId
     * @return
     */
    void promoteScan(String appName, String sandboxName) {

        String appId = getAppId(appName)
        String sandboxId = getSandboxId(appId, sandboxName)
        String lastBuildInfoXml = getLastBuildInfo(appId, sandboxId)
        String buildId = getCurrentBuildId(lastBuildInfoXml)
        String buildStatus = getBuildStatus(lastBuildInfoXml)

        if (canUploadNewBuild(buildStatus)) {
            info"==> Promote in progress ..."
            getSandboxApi().promoteSandbox(buildId.toString())
        } else {
            info "==> Cannot promote last sandbox scan for ${sandboxName} because the last scan state is ${buildStatus}"

        }

    }

    @NonCPS
    private synchronized UploadAPIWrapper getUploadApi() {
        if (!uApi) {
            uApi = new UploadAPIWrapper()
            uApi.setUpApiCredentials(vId, vKey)
        }
        return uApi
    }

    @NonCPS
    private synchronized SandboxAPIWrapper getSandboxApi() {
        if (!sbApi) {
            sbApi = new SandboxAPIWrapper()
            sbApi.setUpApiCredentials(vId, vKey)
        }
        return sbApi
    }

    private void debug(def text) {
        if (isDebug) {
            info(text)
        }
    }

    private void info(def text) {
        if (steps) {
            steps.echo text.toString()
        }
        else {
            println text.toString()
        }
    }

}
