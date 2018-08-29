#!/usr/bin/env groovy


/**
 * emailNotification.groovy
 *
 * Call operator that returns a method with the desired email notification
 *
 * Currently supported methods are: failed, unstable, recovered
 *
 * @param method
 * @param conf
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String method, conf) {
    if (conf.containsKey('disableEmail')) {
        echo 'Email notifications disabled.'
    }else {
        emailAddr = conf.notificationEmailAddr ?: 'builds@squaretrade.com'

        return "$method"(emailAddr)
    }
}

/**
 * Returns the default configuration to send an email.
 *
 * @param emailAddr alternate email address to send notifications (default: builds@squaretrade.com)
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getDefaultEmailConf(String emailAddr){
    return [
            to: "${emailAddr}",
            from: 'cloudbees@squaretrade.com',
            recipientProviders: [[$class: 'DevelopersRecipientProvider']],
            replyTo: 'noreply@squaretrade.com',
            attachLog: true,
            mimeType: 'text/html'
    ]
}

/**
 * Returns the base job name without namespaces.
 *
 * @return
 */
def getBaseJobName(){
    return "${env.JOB_NAME}".split('/').last()
}

/**
 * Send a failed build email notification.
 *
 * @param emailAddr alternate email address to send notifications (default: builds@squaretrade.com)
 */
def failed(String emailAddr) {
    email = getDefaultEmailConf(emailAddr) << [
            subject: "Build failed in Cloudbees: Job ${getBaseJobName()} ${currentBuild.displayName}",
            body: """<p>FAILED: Job <b>${getBaseJobName()} ${currentBuild.displayName}</b>:</p>
                <p>Executon Status: <h4 style='color:red'> ${currentBuild.currentResult}</h4></p>
                <p>Check console output at: <a href='${env.BUILD_URL}'>${currentBuild.displayName}</a></p>"""
    ]

    emailext(email)
}

/**
 * Send a unstable build email notification.
 *
 * @param emailAddr alternate email address to send notifications (default: builds@squaretrade.com)
 */
def unstable(String emailAddr) {
    email = getDefaultEmailConf(emailAddr) << [
            subject: "Build unstable in Cloudbees: Job ${getBaseJobName()} ${currentBuild.displayName}",
            body: """<p>UNSTABLE: Job <b>${getBaseJobName()} ${currentBuild.displayName}<b>:</p>
                <p>Executon Status: <h4 style='color:yellow'> ${currentBuild.currentResult}</h4></p>
                <p>One or more tests have failed, please check console output at: <a href='${env.BUILD_URL}'>${currentBuild.displayName}</a></p>"""
    ]

    emailext(email)
}

/**
 * Send a recovered build email notification.
 *
 * @param emailAddr alternate email address to send notifications (default: builds@squaretrade.com)
 */
def recovered(String emailAddr) {
    email = getDefaultEmailConf(emailAddr) << [
            subject: "Build is back to normal in Cloudbees: Job ${getBaseJobName()} ${currentBuild.displayName}",
            body: """<p>RECOVERED: Job <b>${getBaseJobName()} ${currentBuild.displayName}</b>:</p>
                <p> Current Status: <h4 style='color: green'>${currentBuild.currentResult}</h4>
                <p> Previous Status: <h4 style='color:red'> ${currentBuild.getPreviousBuild().result}</h4>
                <p>Check console output at: <a href='${env.BUILD_URL}'>${currentBuild.displayName}</a></p>"""
    ]

    emailext (email)
}