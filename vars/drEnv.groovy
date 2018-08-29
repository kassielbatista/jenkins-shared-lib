#!/usr/bin/env groovy

import org.yaml.snakeyaml.Yaml

/**
 * Call operator to get around issue where methods cannot be called within 'parameters' declarative pipeline section
 *
 * @param method
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String method) {
    return "$method"()
}

/**
 * Call operator to get around issue where methods cannot be called within 'parameters' declarative pipeline section
 *
 * @param method
 * @param conf
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String method, args) {
    return "$method"(args)
}

/**
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def environments() {
    return libraryResource('com/squaretrade/productionEnvironments.txt')
}

/**
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def stEnvironments() {
    return libraryResource('com/squaretrade/st-environments.yml')
}

/**
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def stEnvironmentsYML(def yamlST) {
    Yaml parser = new Yaml()
    Map yamlData = (Map) parser.load(yamlST)

    return yamlData
}

/**
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def applications() {
    return libraryResource('com/squaretrade/applications.txt')
}

/**
 *  Returns a list of DR operations based on conf
 *
 * @return String list of DR operation parameters
 */
@SuppressWarnings("GrMethodMayBeStatic")
def operations() {

    def operations = [] as LinkedHashSet<String>

    operations.add('NONE')

    operations.add('start')
    operations.add('stop')
    operations.add('status')
    operations.add('restart')
    operations.add('rollingRestart')
    operations.add('disableStartup')
    operations.add('disableStartup+stop')
    operations.add('enableStartup')
    operations.add('enableStartup+start')

    return operations.join('\n')
}

/**
 * Get the Application sorted by priority from YAML
 *
 * @param yaml
 * @return
 */
def getSortedApplicationFromYaml(Map yamlContent){

    def appGroup
    def newAppList = []

    //Get the list of applications
    yamlContent.each { key, value ->
        if (key == "application" && value instanceof Map){
            appGroup = value
        }
    }

    //Add the application name into each application map
    appGroup.each { key, value ->
        value.put("appName", key)
        newAppList.add(value)
    }

    def sortedByPriority = newAppList.sort{ it.priority }

    return sortedByPriority

}

/**
 * Get the Application data from YAML
 *
 * @param yaml
 * @param application
 * @return
 */
def getApplicationFromYaml(Map yamlContent, String application){

    def appGroup
    def appData

    //Get the list of applications
    yamlContent.each { key, value ->
        if (key == "application" && value instanceof Map){
            appGroup = value
        }
    }

    //Find application by the application param
    appGroup.each { key, value ->
        if (key == application) {
            appData = value
        }
    }

    return appData

}

/**
 * Get data from YAML
 *
 * @param yaml
 * @param property
 * @param keyword
 * @return
 */
def getDataFromYaml(Map yamlContent, String property, String keyword){

    def groupContent
    def dataContent

    yamlContent.each { key, value ->
        if (key == property && value instanceof Map){
            groupContent = value
        }
    }

    groupContent.each { key, value ->
        if (key == keyword) {
            dataContent = value
        }
    }

    return dataContent

}

/**
 * Get the Cluster Hosts from Cluster type
 *
 * @param yaml
 * @param appCluster
 * @return
 */
def getHostsFromClusterType(Map yamlContent, String appCluster){

    def clusterHosts

    yamlContent.each { key, value ->
        if (key == appCluster){
            clusterHosts = value
        }
    }

    return clusterHosts.hosts

}