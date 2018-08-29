#!/usr/bin/env groovy
/**
 * getNode.groovy
 *
 * Returns the current Jenkins node/slave the job is running on
 *
 * @param env
 * @return returns the current node name
 */
def call(def env) {
    return env.NODE_LABELS.find(~/\s*slavebee\d\s*|master/)?.trim()
}