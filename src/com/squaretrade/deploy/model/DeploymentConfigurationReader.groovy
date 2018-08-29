package com.squaretrade.deploy.model

import groovy.json.JsonSlurper

//@ToString(includeFields = false, includeNames = true, includePackage = false)
class DeploymentConfigurationReader {

    // Map of serviceName -> ServiceConfiguration object.
    Map<String, ServiceConfiguration> deployServiceConfigurations = [:]

    HistoryConfiguration historyConfiguration

    String environment

    DeploymentConfigurationReader(environment,
                                  serviceConfigurationFile,
                                  historyConfigurationFile) {
        // println('Create DeploymentConfigurationReader for environment' + environment)

        this.environment = environment

        readDeployServiceConfiguration(serviceConfigurationFile)

        if(historyConfigurationFile != null) {
            readDeployHistoryConfiguration(historyConfigurationFile)

            // Check Environment name specified in the DeploymentConfigurationReader
            // is not same as the one in the historyConfigurationFileURL
            // This should not really happen.
            if(!historyConfiguration.environment.equals(this.environment)) {
                // println('** Error: HistoryConfiguration file ' + historyConfigurationFileURL +
                //' does not contain same environment name as ' + this.environment)
                this.historyConfiguration = null
            }
        }
    }

    void readDeployServiceConfiguration(fileContent) {

        // Parse JSON file that defines services to deploy
        JsonSlurper jsonSlurper = new JsonSlurper()
        Object jsonObject = jsonSlurper.parseText(fileContent)

        // Read services to deploy from the JSON object read above
        def servicesToDeploy = jsonObject.keySet()

        // Create ServiceConfiguration objects
//        servicesToDeploy.each { serviceName ->
//            this.deployServiceConfigurations.put(
//                    serviceName,
//                    new ServiceConfiguration(serviceName, jsonObject.get(serviceName), this.environment))
//        }

        for(String serviceName : servicesToDeploy) {
            this.deployServiceConfigurations.put(serviceName,
                    new ServiceConfiguration(serviceName, jsonObject.get(serviceName), this.environment))
        }
    }

    /**
     * Read deployment-history json file.
     */
    void readDeployHistoryConfiguration(fileContent) {

        // Parse JSON file that defines services to deploy
        JsonSlurper jsonSlurper = new JsonSlurper()
        Object jsonObject = jsonSlurper.parseText(fileContent)

        this.historyConfiguration = new HistoryConfiguration(jsonObject)
    }

    // This function finds the services that need to be deployed
    // It returns a Map of serviceName -> ServiceConfiguration objects.
    Map getServicesToDeploy() {

        // println('Find servicesToDeploy for environment' + environment)

        // Nothing to deploy
        if(deployServiceConfigurations == null) {
            return [:]
        }

        // No deployment history found. So deploy all services
        if(historyConfiguration == null) {
//            return deployServiceConfigurations
//                    .values().collectEntries{[ (it.serviceName) : it]}

            Collection deployServiceConfigurationsValues = deployServiceConfigurations.values();
            Map serviceNameConfigurationMap = [:]
            for(ServiceConfiguration sc : deployServiceConfigurationsValues) {
                serviceNameConfigurationMap.put(sc.serviceName, sc)
            }

            return serviceNameConfigurationMap
        }

        List servicesToDeploy = []

        Set serviceNames = deployServiceConfigurations.keySet()
        for(String serviceName : serviceNames) {
            ServiceConfiguration serviceConfiguration = deployServiceConfigurations.get(serviceName)
            // If service is not in the history configuration, then that service is not yet deployed
            // So add it to the servicesToDeploy list
            if(!historyConfiguration.buildConfigSnapshot.containsKey(serviceName)) {
                servicesToDeploy.add(serviceConfiguration)
            }
            else {
                // If service is already deployed then check the version of the
                // deployed service with the version of ServiceConfiguration
                // If both versions are same then no need to deploy this service
                // again. Otherwise add this ServiceConfiguration to the
                // servicesToDeploy list
                ServiceConfiguration historyServiceConfiguration =
                        historyConfiguration.buildConfigSnapshot.get(serviceName)

                if(!serviceConfiguration.isSameVersion(historyServiceConfiguration.getServiceVersion())) {
                    servicesToDeploy.add(serviceConfiguration)
                }
            }
        }

//        serviceNames.each { serviceName ->
//            ServiceConfiguration serviceConfiguration = deployServiceConfigurations.get(serviceName)
//            // If service is not in the history configuration, then that service is not yet deployed
//            // So add it to the servicesToDeploy list
//            if(!historyConfiguration.buildConfigSnapshot.containsKey(serviceName)) {
//                servicesToDeploy.add(serviceConfiguration)
//            }
//            else {
//                // If service is already deployed then check the version of the
//                // deployed service with the version of ServiceConfiguration
//                // If both versions are same then no need to deploy this service
//                // again. Otherwise add this ServiceConfiguration to the
//                // servicesToDeploy list
//                ServiceConfiguration historyServiceConfiguration =
//                        historyConfiguration.buildConfigSnapshot.get(serviceName)
//
//                if(!serviceConfiguration.getServiceVersionForEnvironment(environmentName)
//                    .equals(historyServiceConfiguration.getServiceVersionForEnvironment(environmentName))) {
//                    servicesToDeploy.add(serviceConfiguration)
//                }
//            }
//        }

//        return servicesToDeploy.collectEntries{[ (it.serviceName) : it]};

        // println('Converting servicesToDeploy array to a Map')
        Map serviceNameConfigurationMap = [:]
        for(ServiceConfiguration sc : servicesToDeploy) {
            serviceNameConfigurationMap.put(sc.serviceName, sc)
        }

        return serviceNameConfigurationMap
    }

    // This function finds the services that need to be deployed
    // as part of specified label.
    // It returns a Map of serviceName -> ServiceConfiguration objects.
    Map getServicesToDeploy(String labelName) {
        // println('Find servicesToDeploy for label ' + labelName)

        Map servicesToDeploy = getServicesToDeploy()

        if(labelName == null || 'NONE'.equals(labelName)) {
            return servicesToDeploy
        }

//        Closure labelClosure = { it.labels.contains(labelName) }
//
//        return servicesToDeploy
//                .values()
//                .findAll(labelClosure)
//                .collectEntries{[ (it.serviceName) : it]}

        Map servicesToDeployWithLabel = [:]
        Set servicesToDeployValues = servicesToDeploy.values();
        for(ServiceConfiguration sc : servicesToDeployValues) {
            if(sc.labels.contains(labelName)) {
                servicesToDeployWithLabel.put(sc.serviceName, sc)
            }
        }

        return servicesToDeployWithLabel
    }

    static Map listServicesToDeploy(environment,
                                    serviceConfigurationFile,
                                    historyConfigurationFile,
                                    labelName) {
        // Create DeploymentConfigurationReader with the configuration file and history file
        DeploymentConfigurationReader deploymentConfigurationReader =
                new DeploymentConfigurationReader(environment,serviceConfigurationFile, historyConfigurationFile)



        return deploymentConfigurationReader.getServicesToDeploy(labelName)
        }

    static void main(String[] args) {
        String environment = 'dev1'
        Map<String, String> env = System.getenv()

        // Create DeploymentConfigurationReader with the configuration file and history file
        DeploymentConfigurationReader deploymentConfigurationReader =
                new DeploymentConfigurationReader(environment,
                        new File(env['WORK_ROOT'] + "/deployment-config-dev/backend.json").text,
                        new File(env['WORK_ROOT'] + "/deployment-history/dev1/backend-history.json").text)

        Map<String, ServiceConfiguration> warrantyMigrationServices = deploymentConfigurationReader.getServicesToDeploy('dismintegrate')

        println('label:warranty-migration services to deploy...')
        warrantyMigrationServices.keySet().each {
            serviceName ->
                ServiceConfiguration svcConfig = warrantyMigrationServices.get(serviceName)
                println("${serviceName}: ${svcConfig.version} version ${svcConfig.versionType}")
        }
    }
}
