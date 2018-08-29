package com.squaretrade.deploy.model

import org.junit.Assert
import sun.misc.IOUtils

class TestStageEnv extends GroovyShellTestCase {

    Script stageEnv
    def TEST_VERACODE_CONF = [ veracodeApps: [
                                                [
                                                    appName: 'sourceroot-app',
                                                    uploadFrom: 'Engineering/DistributeRoot',
                                                    includesPattern: 'jboss/apps/app.ear',
                                                ]
                                            ]
                             ]

    @Override
    protected void setUp() {
        super.setUp()
        GroovyCodeSource gcs = new GroovyCodeSource(new File('vars/stageEnv.groovy'))
        stageEnv = shell.parse(gcs)
    }

    void testCall() {
        def params = ['operations', [:]]
        String result = stageEnv.invokeMethod('call', params)
        assertFalse(result.isEmpty())
        println "\n$result"
    }

    /**
     * Haven't figured out how to unit test this with libraryResource yet.
     */
      void testEnvironmentCall() {
        stageEnv.metaClass.libraryResource { path ->
            return 'Working'
        }

        String result = stageEnv.invokeMethod('call', 'environments')

        assertFalse(result.isEmpty())
        assertEquals('Working', result)

        println result
    }

    void testGetDwhscriptsEnvironmentsCall() {
        stageEnv.metaClass.libraryResource { path ->
            return 'Yep'
        }

        String result = stageEnv.invokeMethod('call', 'getDwhscriptsEnvironments')

        assertFalse(result.isEmpty())
        assertEquals('Yep', result)
    }

    /**
     * This test validates that the preproductionEnvironments.txt
     * resource lookup and basic filtering of some
     * excludedAutomationPreProductionEnvironments.txt are working.
     * While asserts are not done for the full environment list,
     * spot checking will make the test less likely to break on any
     * change to these resource files.
     */
    void testGetAutomationEnvironmentCall() {
        stageEnv.metaClass.libraryResource { String path->
            return stageEnv.class.getResourceAsStream(path).text
        }

        def expected = [
            'NONE',
            'appsteam-cluster',
            'mintteam1',
            'mintteam2',
            'platformteam1',
            'platformteam2',
            'stage4',
        ]

        def notExpected = [
            'dev',
            'dev1',
            'integration2',
        ]

        String result = stageEnv.invokeMethod('call', 'getAutomationEnvironments')
        Assert.assertNotNull("stageEnv getAutomationEnvironments return a non-null result", result)

        String[] automationEnvironments = result.split("\n")
        expected.each {
            Assert.assertTrue("environment list should contain ${it}", automationEnvironments.contains(it))
        }

        notExpected.each {
            Assert.assertFalse("environment list should not contain ${it}", automationEnvironments.contains(it))
        }
    }

    void testDefaultOperations() {
        def expected = [
            'NONE',
            'build',
            'build+test',
            'build+test+update-db',
            'build+test+update-db+deploy',
            'build+test+update-db+deploy+rollingRestart',
            'status',
            'rollingRestart',
            'start',
            'stop',
            'update-db'
        ]
        assertOperations(expected, [:])
    }

    void testDwhscriptsOperations() {
        def expected = ['NONE', 'policy-scan+deploy+tag', 'deploy+tag', 'deploy', 'policy-scan']

        assertEquals(expected.join('\n'), stageEnv.invokeMethod('call', 'dwhscriptsOperations'))
    }

    void testDisableDbUpdates() {

        def expected = [
            'NONE',
            'build',
            'build+test',
            'build+test+deploy',
            'build+test+deploy+rollingRestart',
            'status',
            'rollingRestart',
            'start',
            'stop'
        ]
        assertOperations(expected,  [disableDbUpdates: true])
    }

    void testDisableTests() {
        def expected = [
            'NONE',
            'build',
            'build+update-db',
            'build+update-db+deploy',
            'build+update-db+deploy+rollingRestart',
            'status',
            'rollingRestart',
            'start',
            'stop',
            'update-db'
        ]
        assertOperations(expected, [disableTests: true])
    }

    void testDisableTestsAndDbUpdates() {

        def expected = [
            'NONE',
            'build',
            'build+deploy',
            'build+deploy+rollingRestart',
            'status',
            'rollingRestart',
            'start',
            'stop',
        ]
        assertOperations(expected, [disableTests: true, disableDbUpdates: true])
    }


    void testIsMavenProject() {

        def expected = [
            'NONE',
            'build',
            'build+deploy',
            ''
        ]
        assertOperations(expected, [projectType: 'maven', disableTests: true, disableDbUpdates: true])
    }

    void testCommands() {

        def expected = [
            'status', 'rollingRestart', 'start', 'stop', 'update-db'
        ]

        def conf = [
                projectType: 'gradle'
        ]

        assertCommands(expected, conf)
    }

    void testDefaultDbCredID() {
        String result = stageEnv.invokeMethod('defaultDbCredentialsId', null)
        assertNotNull(result)
        assertFalse(result.isEmpty())
    }



    void testIsEnvironmentParamSet() {

        String environmentParam = 'NONE'
        String operationParam = 'build'
        assertIsEnvironmentParamSet(true, environmentParam, operationParam)

        operationParam = 'build+test'
        assertIsEnvironmentParamSet(true, environmentParam, operationParam)

        operationParam = 'build+test+update-db'
        assertIsEnvironmentParamSet(false, environmentParam, operationParam)

        environmentParam = 'dev1'
        assertIsEnvironmentParamSet(true, environmentParam, operationParam)

    }

    void testCanVeracodeScan() {
        Map conf = [
                gitRepo: 'Engineering/SourceRoot',
        ]

        conf << TEST_VERACODE_CONF

        assertTrue(stageEnv.invokeMethod('canVeracodeScan', conf))
        conf.veracodeApps[0].remove('appName')
        assertFalse(stageEnv.invokeMethod('canVeracodeScan', conf))
        conf.remove('veracodeApps')
        assertFalse(stageEnv.invokeMethod('canVeracodeScan', conf))

    }

    void testGetPythonFilesToSearchWithDefaultValues() {
        String expected = "-name '*.py' -o -name '*.html' -o -name '*.js' -o -name '*.css'"

        assertEquals(expected, stageEnv.invokeMethod('call', 'getPythonFilesToSearch'))
    }

    void testGetPythonFilesToSearchWithCustomExtensions() {
        List<String> filesToSearch = ['.py', '.html']

        String expected = "-name '*.py' -o -name '*.html'"

        assertEquals(expected, stageEnv.invokeMethod('getPythonFilesToSearch', filesToSearch))
    }

    void testGetJobBaseName() {
        assertEquals('testJob', stageEnv.invokeMethod('getJobBaseName', 'folder/testJob'))
    }

    void testValidateAutomaticPolicyScanSuccess() {
        Map params = [
                OPERATION: 'build+test+update-db+deploy',
                ENVIRONMENT: 'stage2',
                GIT_BRANCH_OR_TAG: 'rc-someBranch',
                GIT_IS_TAG: true,
                VERACODE_POLICY_SCAN: false
        ]

        assertTrue(stageEnv.isAutomaticPolicyScan(params))
    }

    void testValidateAutomaticPolicyScanInvalidOperation() {
        Map params = [
                OPERATION: 'build+update-db',
                ENVIRONMENT: 'stage2',
                GIT_BRANCH_OR_TAG: 'rc-someBranch',
                GIT_IS_TAG: true,
                VERACODE_POLICY_SCAN: false
        ]

        assertFalse(stageEnv.isAutomaticPolicyScan(params))
    }

    void testValidateAutomaticPolicyScanInvalidEnvironment() {
        Map params = [
                OPERATION: 'build+test+update-db+deploy',
                ENVIRONMENT: 'dev1',
                GIT_BRANCH_OR_TAG: 'rc-someBranch',
                GIT_IS_TAG: true,
                VERACODE_POLICY_SCAN: false
        ]

        assertFalse(stageEnv.isAutomaticPolicyScan(params))
    }

    void testValidateAutomaticPolicyScanInvalidBranchType() {
        Map params = [
                OPERATION: 'build+test+update-db+deploy',
                ENVIRONMENT: 'stage2',
                GIT_BRANCH_OR_TAG: 'someBranch',
                GIT_IS_TAG: true,
                VERACODE_POLICY_SCAN: false
        ]

        assertFalse(stageEnv.isAutomaticPolicyScan(params))
    }

    void testValidateAutomaticPolicyScanGitIsNotTag() {
        Map params = [
                OPERATION: 'build+test+update-db+deploy',
                ENVIRONMENT: 'stage2',
                GIT_BRANCH_OR_TAG: 'rc-someBranch',
                GIT_IS_TAG: false,
                VERACODE_POLICY_SCAN: false
        ]

        assertFalse(stageEnv.isAutomaticPolicyScan(params))
    }

    //============================================================

    void testCanDeployMaven() {
        Map conf = [
                gitRepo: 'Engineering/datawarehouse',
                projectType: 'maven',
                mavenTomcatDeploy: true
        ]

        assertTrue(stageEnv.invokeMethod('mavenTomcatDeploy', conf))
    }

    void assertIsEnvironmentParamSet(boolean expected, Object ...args) {
        assertEquals(expected, stageEnv.invokeMethod('isEnvironmentParamSet', args))
    }

    void assertOperations(expected, args) {
        String result = stageEnv.invokeMethod('operations', args)
        assertEquals(expected.join('\n'), result)
    }

    void assertCommands(expected, args) {
        String result = stageEnv.invokeMethod('commands', args)
        assertEquals(expected.toString(), result.toString())
    }


}
