#!/usr/bin/env groovy

/**
 * ciEnv.groovy
 *
 * Returns the current Jenkins nodes
 *
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(String method) {
    return "$method"()
}

/**
 * Returns the list of Jenkins nodes
 *
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def getNodes() {

    ciNodes = [
        'master', 
        'slavebee1',
        'slavebee2',
        'slavebee3'
    ]

    return ciNodes
}
