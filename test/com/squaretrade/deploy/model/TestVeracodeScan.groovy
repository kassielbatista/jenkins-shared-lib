package com.squaretrade.deploy.model

import groovy.mock.interceptor.MockFor

/**
 *
 */
class TestVeracodeScan extends GroovyShellTestCase {

    Script script
    Map conf, conf2, conf3, conf4
    String TEST_VID = 'testId'
    String TEST_VKEY = 'testKey'
    String TEST_BUILD_NUMBER = '4'
    String TEST_GIT_BRANCH = 'master'

    /**
     *
     */
    @Override
    protected void setUp() {
        super.setUp()
        GroovyCodeSource gcs = new GroovyCodeSource(new File('vars/veracodeScan.groovy'))
        script = shell.parse(gcs)

        conf= [
                gitRepo: 'Engineering/SourceRoot',
                cron: '@midnight',
                disableEmail: true,
                gradleTask: 'install',
                isPromote: true,
                veracodeMethodType: 'PARALLEL',
                veracodeApps: [
                        [
                                appName: 'sourceroot-app',
                                uploadFrom: 'Engineering/DistributeRoot',
                                includesPattern: 'jboss/apps/app.ear'
                        ],
                        [
                                appName: 'sourceroot-batch',
                                uploadFrom: 'Engineering/DistributeRoot',
                                includesPattern: 'jboss/apps/batch.ear'
                        ]
                ]
        ]

        conf2= [
                gitRepo: 'Billing/tax-engine',
                cron: '@midnight',
                disableEmail: true,
                veracodeMethodType: 'SERIAL',
                veracodeApps: [
                        [
                                appName: 'tax-engine',
                                excludesPattern: 'build/libs/*sources.jar,build/libs/*javadoc.jar'

                        ]
                ]
        ]

        conf3= [
                gitRepo: 'Engineering/ha-services',
                cron: '@midnight',
                disableEmail: true,
                veracodeMethodType: 'PARALLEL',
                veracodeApps: [
                        [
                                appName: 'bulk-submitter',
                                uploadFrom: 'Engineering/ha-services/bulk-submitter'
                        ],
                        [
                                appName: 'payment-gateway',
                                uploadFrom: 'Engineering/ha-services/payment-gateway'
                        ]
                ]
        ]

        conf4= [
                gitRepo: 'Engineering/ha-services',
                cron: '@midnight',
                disableEmail: true,
                veracodeMethodType: 'SERIAL',
                veracodeApps: [
                        [
                                appName: 'bulk-submitter',
                                uploadFrom: 'Engineering/ha-services/bulk-submitter',
                                sandboxName: 'Cloudbees Sandbox',
                                app_id: '4',
                                sandbox_id: '5',
                        ]
                ]
        ]

        mockSteps()
    }

    /**
     *
     */
    private void mockSteps() {

        script.metaClass.usernamePassword{ vParams->
            assertEquals('bad credentialsId', vParams.credentialsId, '5021cb1f-98a3-4d5b-af99-d6adf29a8d66')
            //println vParams
        }

        script.metaClass.withCredentials{ ArrayList al, Closure closure->
            closure.setProperty('veracode_id', TEST_VID)
            closure.setProperty('veracode_key', TEST_VKEY)
            closure()
        }

        script.metaClass.echo{ String msg->
            //assertTrue(msg.startsWith('Veracode plugin parameters'))
            println "echo: ${msg}"
        }

    }


    /**
     *
     */
    void testSandboxScan() {

        String testScanName = "${conf.gitRepo}-${TEST_GIT_BRANCH}-build-${TEST_BUILD_NUMBER}-POLICY"

        def vApi = mockSandbox(testScanName, false)

        vApi.use {
            script.invokeMethod('call', [conf.veracodeApps[0] as Map, testScanName, VeracodeScanTypeEnum.SANDBOX, false, Boolean.TRUE, conf.isPromote])
        }
    }

    /**
     *
     */
    void testPolicyScan() {

        String testScanName = "${conf.gitRepo}-${TEST_GIT_BRANCH}-build-${TEST_BUILD_NUMBER}"

		def vApi = mockPolicy(testScanName, false)
	
		vApi.use {
			script.invokeMethod('call', [conf.veracodeApps[0], testScanName, VeracodeScanTypeEnum.POLICY, false, true, conf.isPromote])
		}
    }

    /**
     * test calling veracodeScan.steps with different configurations
     */
    void testSteps() {

        script.metaClass.echo{ String msg->
            println "\necho: ${msg}"
        }

        script.metaClass.dir{ dir, closure ->
            println "\n\nin dir: ${dir}, executing...."
            closure()
        }

        script.metaClass.parallel{
            it.each{ k, closure ->
                println "\n\nExecuting parallel step: ${k}"
                closure()
            }
        }


        [conf, conf2, conf3].each() { Map c ->

            String expectedScanName = "${c.gitRepo}-${TEST_GIT_BRANCH}-build-${TEST_BUILD_NUMBER}-SANDBOX"

            def vApi = mockSandbox(expectedScanName, c == conf2)

            vApi.use {
                println "\n==================================================================\n"
                script.invokeMethod('steps', [c, TEST_GIT_BRANCH, TEST_BUILD_NUMBER, VeracodeScanTypeEnum.SANDBOX.name(), c.veracodeMethodType, true])
            }
        }

    }

    private void assertCommon(Map vParams, String expectedScanName, boolean hasExcludes) {
        println "veracode: ${vParams}"
        assertEquals('bad scanName', expectedScanName, vParams.get('scanName'))
        assertEquals('unexpected uploadExcludesPattern', hasExcludes, vParams.uploadExcludesPattern != null)
        assertEquals('bad vid', TEST_VID, vParams.get('vid'))
        assertEquals('bad vkey', TEST_VKEY, vParams.get('vkey'))
        assertEquals('bad useIDkey', 'true', ((String) vParams.get('useIDkey')).trim())
    }

    def mockSandbox(String expectedScanName, boolean hasExcludes) {
        script.metaClass.currentBuild = [result: 'SUCCESS']

        script.metaClass.error { String msg->
            println "\nerror: ${msg}"
        }

        def vApi = new MockFor(VeracodeApi)

        vApi.ignore.deleteLastBuildIfRequiredForFileUpload { appName, sandboxName ->
            println "\ndeleteLastBuildIfRequiredForFileUpload called with appName=${appName}, sandboxName=${sandboxName}"
        }
		
		vApi.ignore.isScanHealthy { appName, sandboxName, scanName ->
			println "\nisScanHealthy called with appName=${appName}, sandboxName=${sandboxName}, scanName=${scanName}; returning true"
			return true
		}
		
		vApi.ignore.isCurrentBuild { appName, sandboxName, scanName ->
			println "\nisCurrentBuild called with appName=${appName}, sandboxName=${sandboxName}, scanName=${scanName}; returning true"
			return true
		}
		
        script.metaClass.veracode { Map vParams ->
            assertCommon(vParams, expectedScanName, hasExcludes)

            assertEquals('bad sandboxName', 'Cloudbees Sandbox', vParams.get('sandboxName'))
            assertEquals('bad createSandbox', 'true', ((String) vParams.get('createSandbox')).trim())
        }

        return vApi
    }

	def mockPolicy(String expectedScanName, boolean hasExcludes) {
		
		script.metaClass.currentBuild = [result: 'SUCCESS']

		script.metaClass.error { String msg->
			println "error: ${msg}"
		}

		def vApi = new MockFor(VeracodeApi)
		
		vApi.ignore.isScanHealthy { appName, sandboxName, scanName ->
			println "\nisScanHealthy called with appName=${appName}, sandboxName=${sandboxName}, scanName=${scanName}; returning true"
			return true
		}
		
		vApi.ignore.isCurrentBuild { appName, sandboxName, scanName ->
			println "\nisCurrentBuild called with appName=${appName}, sandboxName=${sandboxName}, scanName=${scanName}; returning true"
			return true
		}
		
		script.metaClass.veracode{ Map vParams ->
			assertCommon(vParams, expectedScanName, false)
			assertNull('unexpected sandboxName', vParams.get('sandboxName'))
			assertNull('unexpected createSandbox', vParams.get('createSandbox'))
		}

		return vApi
	}


}
