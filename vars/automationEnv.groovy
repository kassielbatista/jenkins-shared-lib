#!/usr/bin/env groovy

import com.squaretrade.deploy.model.QASlackNotification
import hudson.tasks.test.AbstractTestResultAction

/**
 * We already perform a submodule checkout within checkoutGitBranchOrTag.groovy,
 * but QA needs to merge the differences in TestRails with some submodule commands that checkout do not perform.
 *
 * @return
 */
def updateFloweeTestReport() {
    sh "git submodule update --init --recursive"
    sh "git submodule foreach --recursive git fetch"
    sh "git submodule foreach git merge origin master"
}

/**
 * Get the test status from Jenkins build action, As we are manually formatting th QA slack messages,
 * we need to get the test status as it is one of their requirements for the notifications.
 *
 * @return
 */
def getTestStatus() {
    AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)

    if (testResultAction != null) {
        total = testResultAction.totalCount
        failed = testResultAction.failCount
        skipped = testResultAction.skipCount
        passed = total - failed - skipped

        return "Test Status:\n\tPassed: ${passed}, Failed: ${failed} ${testResultAction.failureDiffString}, Skipped: ${skipped}"
    }

    return "No tests found."
}


/**
 * Sends out Slack notification to inform build cause
 *
 * @param environment
 * @return
 */
def sendSlackInfo() {
    description = currentBuild.rawBuild.getCauses().first().getShortDescription()

    QASlackNotification notification = new QASlackNotification('info', stageEnv.getJobBaseName(env.JOB_NAME), currentBuild.number, env.BUILD_URL, description)

    notification.sendMessage(this)
}

/**
 * Sends out Slack notification for successful build executions
 *
 * @param environment
 * @return
 */
def sendSlackSuccess() {
    QASlackNotification notification = new QASlackNotification('success', stageEnv.getJobBaseName(env.JOB_NAME), currentBuild.number, env.BUILD_URL, currentBuild.durationString, environment, getTestStatus())

    notification.sendMessage(this)
}

/**
 * Sends out Slack notification for failed build executions
 *
 * @param environment
 * @return
 */
def sendSlackFailure() {
    QASlackNotification notification = new QASlackNotification('failure', stageEnv.getJobBaseName(env.JOB_NAME), currentBuild.number, env.BUILD_URL, currentBuild.durationString, environment, getTestStatus())

    notification.sendMessage(this)
}

def zipUIAutomationTestReportFiles() {
    sh "find resources/output \\( -name '*.png' -o -name '*.html' \\) -print | zip -ru UIAutomationHTMLReport.zip -@"
}