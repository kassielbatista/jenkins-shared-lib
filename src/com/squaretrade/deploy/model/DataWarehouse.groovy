package com.squaretrade.deploy.model

/**
 * Data Warehouse Helper Class
 */
class DataWarehouse {
    /**
     * FIXME: if steps.sh fails then the dsl calls 'failure' which does not exist in this context.
     * FIXME: move into a script instead
     */
    static def deploy(String remoteHostname, String deployBaseDir, String backupScript, String dwhScriptsSrcDir, String deployDirName, def steps) {
        // backup
        // Example: scp -Cqp dwhscripts_backup2.sh informatica-1.stage1.squaretrade.com:/opt/eng
        //          ssh -v informatica-1.stage1.squaretrade.com /opt/eng/dwhscripts_backup2.sh /opt/eng jenkins-dwhscripts
        scp(steps, "${backupScript} ${remoteHostname}:${deployBaseDir}")
        ssh(steps, "${remoteHostname} chmod g=rwx,u=rwx ${deployBaseDir}/${backupScript}")
        ssh(steps, "${remoteHostname} ${deployBaseDir}/${backupScript} ${deployBaseDir} ${deployDirName}")

        // deploy
        // e.g. scp -Cqp -r dwhscripts informatica-1.stage1.squaretrade.com:/opt/eng/jenkins-dwhscripts
        scp(steps, "-r ${dwhScriptsSrcDir} ${remoteHostname}:${deployBaseDir}/${deployDirName}")
    }

    static def scp(def steps, def command) {
        steps.sh "scp -Cqp ${command}"
    }

    static def ssh(def steps, def command) {
        steps.sh "ssh ${command}"
    }

}
