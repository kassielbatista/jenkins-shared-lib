package com.squaretrade.deploy.model
//@ToString(includeFields = false, includeNames = true, includePackage = false)
class ServiceConfiguration implements Serializable {

    static String branchType = 'branch'
    static String tagType = 'tag'
    static String artifactType = 'artifact'

    String serviceName

    String repo

    String version

    String versionType

    String environment

    // Map of environment-name -> Version
    // Stores version overrides for specific environment-name
    Map versionOverrides = [:]

    List<String> labels

    ServiceConfiguration(String serviceName, Map configurationData, String environment) {
        this.serviceName = serviceName

        readVersion(configurationData)

        readRepo(configurationData)

        readVersionOverrides(configurationData)

        readLabels(configurationData)

        this.environment = environment
    }

    void readVersion(configurationData) {
        String versionString = configurationData.'version'

        def versionArray = readVersionAsArray(versionString)
        version = versionArray[0]
        versionType = versionArray[1]
    }

    def readVersionAsArray(String versionString) {
        if(versionString.startsWith(branchType + ':')) {
            return [versionString.substring(branchType.length() + 1), branchType]
        }
        else if(versionString.startsWith(tagType + ':')) {
            return [versionString.substring(tagType.length() + 1), tagType]
        }
        else {
            return [versionString.substring(artifactType.length() + 1), artifactType]
        }
    }

    void readRepo(configurationData) {
        repo = configurationData.'repo'
    }

    void readVersionOverrides(configurationData) {
        def versionOverridesData = configurationData.'version-overrides'

        if(versionOverridesData == null) {
            return
        }

//        versionOverridesData.keySet().each { env ->
//            versionOverrides.put(env, new Version(versionOverridesData.get(env))) }

        Set versionOverridesDataKeySet = versionOverridesData.keySet()
        for(String env : versionOverridesDataKeySet) {
            versionOverrides.put(env, readVersionAsArray(versionOverridesData.get(env)))
        }
    }

    void readLabels(configurationData) {
        labels = configurationData.'labels'
    }

    // Read version of the service for specific environment name
    // This function checks if there is any environment specific
    // overrides defined, if so, it will return the overriden version
    // otherwise it returns version from this object.
    def getServiceVersion() {
        def environmentSpecificVersion = [version, versionType]
        if(versionOverrides.isEmpty())
            return environmentSpecificVersion

        if(versionOverrides.containsKey(this.environment)) {
            environmentSpecificVersion = versionOverrides.get(this.environment)
        }

        return environmentSpecificVersion
    }

    boolean isSameVersion(compareWithVersion) {

        return this.version.equals(compareWithVersion[0]) &&
                this.versionType.equals(compareWithVersion[1])
    }
}