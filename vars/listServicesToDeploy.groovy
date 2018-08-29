#!/usr/bin/env groovy

import com.squaretrade.deploy.model.DeploymentConfigurationReader


/**
 * listServicesToDeploy.groovy
 *
 * Read the deployment-config and deployment-history repositories.
 * If versions differ for a matching service between history and config,
 * then the
 *
 * @param conf
 * @return
 */
def call(Map conf) {
//    String environment = conf.environment
//    String label = conf.label
//    String deploymentConfigDir = 'deployment-config-dev'
//    String historyConfigDir = 'deployment-history'
//    String serviceConfigurationFile = deploymentConfigDir + '/backend.json'
//    String historyConfigurationFile =
//            historyConfigDir + '/' + environment + '/backend-history.json'
//
//    echo "Create DeploymentConfigurationReader.."
//    DeploymentConfigurationReader deploymentConfigurationReader =
//            new DeploymentConfigurationReader(environment,
//                    serviceConfigurationFile,
//                    historyConfigurationFile)
//
//    echo "Done creating DeploymentConfigurationReader.."
//
//    // Read list of services to deploy (ServiceConfiguration objects)
//    return deploymentConfigurationReader.getServicesToDeploy(label)

    Map<String, String> servicesToDeploy = [
            'mint-das': 'hackathon2017',
            'warranty-das': 'release-20171005-warranty-das-hotfix-234',
            'warranty-migration-consumer': 'release-20170925-warranty-migration-hotfix-231',
            'warranty-migration-producer': 'release-20170925-warranty-migration-hotfix-231',
            'warranty-queuer': 'release-20170920-warranty-queuer-hotfix-228',
    ]

    return servicesToDeploy
}

//call(label: "dismintegrate", environment: "dev1")