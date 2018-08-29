package com.squaretrade.deploy.model


//@ToString(includeFields = false, includeNames = true, includePackage = false)

class HistoryConfiguration  implements Serializable {

    Date timestamp

    String environment

    String buildConfigRepo

    String buildConfigurationCommit

    String buildConfigurationType

    String buildConfigurationTagBranchName

    // Map of deployed service name to ServiceConfiguration
    // This stores services deployed on this environment.
    Map<String, ServiceConfiguration> buildConfigSnapshot = [:]

    HistoryConfiguration(configurationData) {

        readTimestamp(configurationData)

        readEnvironment(configurationData)

        readBuildConfigRepo(configurationData)

        readBuildConfigurationCommit(configurationData)

        readBuildConfigurationType(configurationData)

        readBuildConfigurationTagBranchName(configurationData)

        readBuildConfigSnapshot(configurationData)
    }

    void readTimestamp(configurationData) {
        this.timestamp = new Date(configurationData.'timestamp')
    }

    void readEnvironment(configurationData) {
        this.environment = configurationData.'environment'
    }

    void readBuildConfigRepo(configurationData) {
        this.buildConfigRepo = configurationData.'buildConfigRepo'
    }

    void readBuildConfigurationCommit(configurationData) {
        this.buildConfigurationCommit = configurationData.'buildConfigurationCommit'
    }

    void readBuildConfigurationType(configurationData) {
        this.buildConfigurationType = configurationData.'buildConfigurationType'
    }

    void readBuildConfigurationTagBranchName(configurationData) {
        this.buildConfigurationTagBranchName = configurationData.'buildConfigurationTagBranchName'
    }

    void readBuildConfigSnapshot(configurationData) {
        def snapshot = configurationData.'buildConfigSnapshot'
//        snapshot.keySet().each { key ->
//            this.buildConfigSnapshot.put(key, new ServiceConfiguration(key, snapshot.get(key)))
//        }

        Set snapshotKeySet = snapshot.keySet();
        for(String key : snapshotKeySet) {
            this.buildConfigSnapshot.put(key, new ServiceConfiguration(key, snapshot.get(key), this.environment))
        }
    }
}
