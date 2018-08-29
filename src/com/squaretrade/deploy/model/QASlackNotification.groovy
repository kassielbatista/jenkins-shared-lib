package com.squaretrade.deploy.model

import groovy.json.JsonOutput

/**
 * QA Slack notification class, used by automationEnv.groovy to format QA slack notification messages
 */
class QASlackNotification extends BaseSlackNotification {

    static String slackURL = 'https://hooks.slack.com/services/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'
    static String username = 'jenkins'
    static String channel = 'qa-automation-suites'

    QASlackNotification(String messageType, String jobName, Integer buildNumber, String buildURL, String causeDescription) {
        super(messageType, username, channel, slackURL, jobName, buildNumber, buildURL, causeDescription)
    }

    QASlackNotification(String messageType, String jobName, Integer buildNumber, String buildURL, String buildTime, String stEnv, String testStatus) {
        super(messageType, username, channel, slackURL, jobName, buildNumber, buildURL, buildTime, stEnv, testStatus)
    }

    /**
     * QA Info message for Slack
     *
     * @return
     */
    @Override
     String info() {
        return JsonOutput.toJson([
                channel: channel,
                username: username,
                icon_emoji: ":qa:",
                attachments: [
                        [
                                color: '#C0C0C0',
                                text: "Info: ${jobName} - #${buildNumber} ${causeDescription} <${buildURL}|(Open)>",
                        ]
                ]
        ])
    }

    /**
     * QA Success message for Slack
     *
     * @return
     */
    @Override
    String success() {
        return JsonOutput.toJson([
                channel: channel,
                username: username,
                icon_emoji: ":qa:",
                attachments: [
                        [
                                color: '#36a64f',
                                title: "Success: ${jobName} - #${buildNumber} ${stEnv} Success after ${buildTime}",
                                text: testStatus,
                                fallback: "Access your build here: ${buildURL}",
                                actions: [
                                        [
                                                type: 'button',
                                                text: 'Open in Cloudbees',
                                                url: "${buildURL}"
                                        ]
                                ]
                        ]
                ]
        ])
    }

    /**
     * QA Failure message for Slack
     *
     * @return
     */
    @Override
    String failure() {
        return JsonOutput.toJson([
                channel: channel,
                username: username,
                icon_emoji: ":qa:",
                attachments: [
                        [
                                color: '#ff0000',
                                title: "Failure: ${jobName} - #${buildNumber} ${stEnv} Failed after ${buildTime}",
                                text: testStatus,
                                fallback: "Access your build here: ${buildURL}",
                                actions: [
                                        [
                                                type: 'button',
                                                text: 'Open in Cloudbees',
                                                url: "${buildURL}"
                                        ]
                                ]
                        ]
                ]
        ])
    }
}
