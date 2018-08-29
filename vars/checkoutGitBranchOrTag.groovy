#!/usr/bin/env groovy

/**
 * checkoutGitBranchOrTag: a custom step to clone a repo and checkout a tag or branch.
 * This is a replacement for the standard <code>git</code> pipeline step
 * that only supports branch as input.
 *
 * <p/>
 * @see <a href="https://stackoverflow.com/questions/46241980/checkout-a-tag-in-jenkins-pipeline">
 *      StackOverflow: Checkout a tag in Jenkins pipeline</a>
 *
 * @param conf.url String git repo to checkout
 * @param conf.branchOrTag String branch or tag name
 * @param conf.isTag Boolean true if branchOrTag is a tag (default: false)
 * @param conf.credentialsId String credentials ID for SquareTrade Github repo (default: st-github-builds)
 * @param conf.shallowClone Boolean true if a shallow clone should be performed to reduce repo size (default: false)
 * @param conf.submodules Boolean true if a git submodule should be used during checkout (default: false)
 *
 */
def call(Map conf) {
    String branchOrTag = conf.branchOrTag

    if (!branchOrTag) {
        error 'No git branch or tag provided'
    }

    String credentialsId = conf.credentialsId ? conf.credentialsId : 'st-github-builds'
    String url = conf.url
    Boolean isTag = conf.containsKey('isTag') ? conf.isTag : false
    Boolean shallowClone = conf.shallowClone ?: false
    Boolean submodules = conf.submodules ?: false
    Boolean localBranch = conf.localBranch ?: false

    // disambiguate branchOrTag name
    // see "$class: GitSCM userRemoteConfigs" branches, name section
    // https://jenkins.io/doc/pipeline/steps/workflow-scm-step/#code-checkout-code-general-scm
    branchOrTag = (isTag ? 'refs/tags/' : 'refs/heads/') + branchOrTag



    def gitConf = [ $class: 'GitSCM',
                    userRemoteConfigs: [
                        [ url: url,
                          credentialsId: credentialsId
                        ]
                    ],
                    branches: [[ name: branchOrTag ]],
                    extensions: []
                  ]

    if (shallowClone) {
        // this reduces the size of the clone:
        gitConf.extensions <<
                [$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '',
                 shallow: true, timeout: 10]
    }

    //Add submodules support to git checkout
    if (submodules) {
        gitConf.extensions <<
                [$class: 'SubmoduleOption',
                 disableSubmodules: false,
                 parentCredentials: true,
                 recursiveSubmodules: true,
                 reference: '',
                 trackingSubmodules: false]
    }

    /* DEVO-200: Add ability to checkout as local branch, if false Jenkins will use default checkout which results in a detached HEAD.
       In detached HEAD commands like `git rev-parse --abbrev-ref HEAD` or `git symbolic-ref --short HEAD`
       will always return `HEAD` instead of real branch name.
     */

    if (localBranch) {
        gitConf.extensions << [$class: 'LocalBranch', localBranch: branchOrTag]
    }

    checkout scm: gitConf, poll: false
}