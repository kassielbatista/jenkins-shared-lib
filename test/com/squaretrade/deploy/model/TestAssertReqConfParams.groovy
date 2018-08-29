package com.squaretrade.deploy.model

/**
 * test the vars/assertRequiredConfParams.groovy shared library
 */
class TestAssertReqConfParams extends GroovyShellTestCase {

    Script script

    @Override
    protected void setUp() {
        super.setUp()
        GroovyCodeSource gcs = new GroovyCodeSource(new File('vars/assertRequiredConfParams.groovy'))
        script = shell.parse(gcs)
    }

    def reqParams = [ 'gitRepo', 'veracode.appName' ].asImmutable()
    def reqParams2 = ['gitRepo', 'veracodeApps', 'veracodeApps[0].appName']


    /**
     * Test a configuration that is missing required parameters
     */
    void testNegative2() {

        def conf = [
                gitRepo: 'Billing/tax-engine',
                cron: '@midnight',
                disableEmail: true,
                veracode: [
                        appName: 'tax-engine',
                        excludesPattern: 'build/libs/*sources.jar,build/libs/*javadoc.jar'
                ]
        ]

        String expected = 'veracodeApps required parameter is not set'

        try {
            script.invokeMethod('call', [conf, reqParams2])
            fail("Expected: ${expected}")
        }
        catch (IllegalArgumentException t) {
            assertEquals(expected, t.message)
        }

    }

    /**
     * Test a configuration has all required parameters
     */
    void testPositive() {

        def conf = [
                gitRepo: 'Engineering/cool',
                veracode: [
                        appName: 'app',
                        credentialsId: 'xxxx'
                ],
                aFlag: true,
                anInt: 1
        ]

        script.invokeMethod('call', [conf, reqParams])
    }


    /**
     * Test a configuration that is missing required parameters
     */
    void testNegative() {

        def conf = [
            gitRepo: 'repo',
            veracode: [
                blah: 'blahblah',
                someInt: 4
            ]
        ]

        String expected = 'veracode.appName required parameter is not set'

        try {
            script.invokeMethod('call', [conf, reqParams])
            fail("Expected: ${expected}")
        }
        catch (IllegalArgumentException t) {
            assertEquals(expected, t.message)
        }

    }

    /**
     * test a configuration that is 3 levels deep
     */
    void testDeepNest() {

        def conf = [
                gitRepo: 'repo',
                veracode: [
                        appName: 'myapp',
                        blah: 'blahblah',
                        someInt: 4,
                        level3: [
                             requiredBoolParam: false
                        ]

                ]
        ]

        def reqParams2 = reqParams.collect() << 'veracode.level3.requiredBoolParam'
        script.invokeMethod('call', [conf, reqParams2])

        def missingParam = 'veracode.level3.missing'
        reqParams2 << missingParam
        String expected = "${missingParam} required parameter is not set"
        try {
            script.invokeMethod('call', [conf, reqParams2])
            fail("Expected: ${expected}")
        }
        catch (IllegalArgumentException t) {
            assertEquals(expected, t.message)
        }
    }


    /**
     * test a configuration that has an open-ended array (array reference)
     */
    void testArrayReference() {


        def conf = [
            veracode: [
                [
                    appName: 'sourceroot-app',
                    includesPattern: 'jboss/apps/app.ear',

                ],
                [
                    appName: 'sourceroot-batch',
                    includesPattern: 'jboss/apps/batch.ear',
                ],

            ]
        ]
        def reqParams = ['veracode[0].appName']
        script.invokeMethod('call', [conf, reqParams])
    }

    void testAssertJobRequiredParams() {
        def params = [
                GIT_REPO: '',
                TAG_TYPE: 'build'
        ]

        def reqParams = [
                'GIT_REPO'
        ]

        String expected = "GIT_REPO required parameter is not set"

        try {
            script.invokeMethod('jobRequiredParams', [params, reqParams])
            fail("Expected: ${expected}")
        }
        catch (IllegalArgumentException err) {
            assertEquals(expected, err.message)
        }
    }


}
