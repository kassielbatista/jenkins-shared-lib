#!/usr/bin/env bash

# Validates the Jenkinsfile in the current directory
# from https://jenkins.io/doc/book/pipeline/development/#linter
#
# Example:
#   ./lint_Jenkinsfile.sh

JENKINS_URL=https://ci.squaretrade.com
JENKINS_USER=louis
JENKINS_API_TOKEN=438db46c72328da21224b0398fddb183
# JENKINS_CRUMB is needed if your Jenkins master has CRSF protection enabled as it should
JENKINS_CRUMB=`curl -s -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" "$JENKINS_URL/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"`
curl -s -u "${JENKINS_USER}:${JENKINS_API_TOKEN}" -X POST -H $JENKINS_CRUMB -F "jenkinsfile=<${1}" $JENKINS_URL/pipeline-model-converter/validate
