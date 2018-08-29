package com.squaretrade.deploy.model

import groovy.json.JsonOutput

/**
 * Base Slack notification class
 */
class BaseSlackNotification {

    //Slack variables
    protected String username
    protected String channel
    protected String slackURL

    protected String messageType

    //Jenkins job variables
    protected Integer buildNumber
    protected String buildURL
    protected String jobName
    protected String causeDescription
    protected String buildTime
    protected String stEnv
    protected String testStatus

    BaseSlackNotification(String messageType, String username, String channel, String slackURL, String jobName, Integer buildNumber, String buildURL, String causeDescription) {
        this.username = username
        this.channel = channel
        this.slackURL = slackURL
        this.buildNumber = buildNumber
        this.jobName = jobName
        this.buildURL = buildURL
        this.causeDescription = causeDescription
        this.messageType = messageType
    }

    BaseSlackNotification(String messageType, String username, String channel, String slackURL, String jobName, Integer buildNumber, String buildURL, String buildTime, String stEnv, String testStatus) {
        this.username = username
        this.channel = channel
        this.slackURL = slackURL
        this.buildNumber = buildNumber
        this.buildURL = buildURL
        this.jobName = jobName
        this.buildTime = buildTime
        this.stEnv = stEnv
        this.testStatus = testStatus
        this.messageType = messageType
    }

    /**
     * Returns a default info message
     * @return
     */
    protected info() {
        return JsonOutput.toJson([
                channel: channel,
                username: username,
                attachments: [
                        [
                                color: '#C0C0C0',
                                text: "Info: Job started",
                        ]
                ]
        ])
    }

    /**
     * Returns a default success message
     * @return
     */
    protected success() {
        return JsonOutput.toJson([
                channel: channel,
                username: username,
                attachments: [
                        [
                                color: '#36a64f',
                                title: "Success",
                                text: "Job successfully executed."
                        ]
                ]
        ])
    }

    /**
     * Returns a default failure message
     * @return
     */
    protected failure() {
        return JsonOutput.toJson([
                channel: channel,
                username: username,
                attachments: [
                        [
                                color: '#ff0000',
                                title: "Failure",
                                text: "Job failed to execute"
                        ]
                ]
        ])
    }

    /**
     * Sends out a slack message
     * @param jenkins
     */
    void sendMessage(def steps) {
        def payload = "$messageType"()

        steps.sh "curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}"
    }
}