package com.squaretrade.deploy.model
import static groovy.json.JsonOutput.*

/**
 *
 */
class TestCheckout extends GroovyShellTestCase {

    Script script

    @Override
    protected void setUp() {
        super.setUp()
        GroovyCodeSource gcs = new GroovyCodeSource(new File('vars/checkoutGitBranchOrTag.groovy'))
        script = shell.parse(gcs)
    }


    String GIT_REPO = 'git@github.squaretrade.com:Engineering/SourceRoot.git'
    String GIT_BRANCH = 'master'

    /**
     *
     */
    void testCommon() {

        def conf= [
            url: "${GIT_REPO}",
            branchOrTag: "${GIT_BRANCH}",
            shallowClone: false
        ]

        script.metaClass.checkout{ vParams->
            println prettyPrint(toJson(vParams))
            assertCommon(vParams)
        }

        script.invokeMethod('call', conf)
    }

    private void assertCommon(vParams) {
        assertEquals('GitSCM', vParams.scm.$class)
        assertEquals(GIT_REPO, vParams.scm.userRemoteConfigs[0].url)
        assertEquals('st-github-builds', vParams.scm.userRemoteConfigs[0].credentialsId)
        assertFalse(vParams.poll)
    }

    /**
     *
     */
    void testShallowClone() {

        def conf= [
                url: "${GIT_REPO}",
                branchOrTag: "${GIT_BRANCH}",
                shallowClone: true
        ]

        script.metaClass.checkout{ vParams->
            println prettyPrint(toJson(vParams))
            assertCommon(vParams)

            assertEquals('CloneOption', vParams.scm.extensions[0].$class)
            assertEquals(1, vParams.scm.extensions[0].depth)
            assertTrue(vParams.scm.extensions[0].noTags)
            assertTrue(vParams.scm.extensions[0].shallow)
            assertTrue(vParams.scm.extensions[0].honorRefspec)
        }

        script.invokeMethod('call', conf)
    }

    void testSubmodules() {
        def conf = [
                url: "${GIT_REPO}",
                branchOrTag: "${GIT_BRANCH}",
                submodules: true
        ]

        script.metaClass.checkout{ vParams ->
            println prettyPrint(toJson(vParams))
            assertCommon(vParams)

            assertEquals('SubmoduleOption', vParams.scm.extensions[0].$class)
            assertEquals(false, vParams.scm.extensions[0].disableSubmodules)
            assertEquals(true, vParams.scm.extensions[0].parentCredentials)
            assertEquals(true, vParams.scm.extensions[0].recursiveSubmodules)
            assertEquals('', vParams.scm.extensions[0].reference)
            assertEquals(false, vParams.scm.extensions[0].trackingSubmodules)
        }
    }
}
