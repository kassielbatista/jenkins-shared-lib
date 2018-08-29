package com.squaretrade.deploy.model

import groovy.json.JsonOutput

class TestQASlackNotification extends GroovyShellTestCase{
    void testInfoPayload() {
        QASlackNotification notification = new QASlackNotification(
                'info',
                'jobName',
                1,
                'https://testing.squaretrade',
                'test description')

        def expected = JsonOutput.toJson([
                channel: 'qa-automation-suites',
                username: 'jenkins',
                icon_emoji: ":qa:",
                attachments: [
                        [
                                color: '#C0C0C0',
                                text: "Info: jobName - #1 test description <https://testing.squaretrade|(Open)>",
                        ]
                ]
        ])

        assertEquals(expected, notification.info())
    }

    void testSuccessPayload() {
        QASlackNotification notification = new QASlackNotification(
                'success',
                'anotherJobName',
                2,
                'https://testing.squaretrade.com',
                '5min',
                'testEnvironment',
                'TestStatus')

        def expected = JsonOutput.toJson([
                channel: 'qa-automation-suites',
                username: 'jenkins',
                icon_emoji: ":qa:",
                attachments: [
                        [
                                color: '#36a64f',
                                title: "Success: anotherJobName - #2 testEnvironment Success after 5min",
                                text: 'TestStatus',
                                fallback: "Access your build here: https://testing.squaretrade.com",
                                actions: [
                                        [
                                                type: 'button',
                                                text: 'Open in Cloudbees',
                                                url: "https://testing.squaretrade.com"
                                        ]
                                ]
                        ]
                ]
        ])

        assertEquals(expected, notification.success())
    }

    void testFailurePayload() {
        QASlackNotification notification = new QASlackNotification(
                'failure',
                'oopsAnotherJobName',
                3,
                'https://testing.squaretrade.eu',
                '10min',
                'anotherTestEnvironment',
                'anotherTestStatus')

        def expected = JsonOutput.toJson([
                channel: 'qa-automation-suites',
                username: 'jenkins',
                icon_emoji: ":qa:",
                attachments: [
                        [
                                color: '#ff0000',
                                title: "Failure: oopsAnotherJobName - #3 anotherTestEnvironment Failed after 10min",
                                text: 'anotherTestStatus',
                                fallback: "Access your build here: https://testing.squaretrade.eu",
                                actions: [
                                        [
                                                type: 'button',
                                                text: 'Open in Cloudbees',
                                                url: "https://testing.squaretrade.eu"
                                        ]
                                ]
                        ]
                ]
        ])

        assertEquals(expected, notification.failure())
    }
}