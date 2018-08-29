package com.squaretrade.deploy.model

import org.junit.BeforeClass
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse

class TestVeracodeApi2 {


    static String APP_ID = '699dafb3250104d56d38d4094982994d'
    static String APP_KEY = '9f8f14fdbb46a1c78236b4887350b84127c8980bf6f9cba16b29b020d158ead250c3e6b78a47a0d22b92cdfce89572e1326216b5f4147fef87b01456faef220e'

    static VeracodeApi api

    @BeforeClass
    static void setup() {
        api = new VeracodeApi(APP_ID, APP_KEY)
        api.isDebug = true
    }

    @Test
    void test() {
        String appId = api.getAppId('tax-engine')
        assertEquals('412399', appId)

        String sandboxId = api.getSandboxId(appId, 'Cloudbees-QA')
        assertEquals('621029', sandboxId)


        String buildStatus = api.getBuildStatus(api.getLastBuildInfo(appId, sandboxId))
        println buildStatus
        assertFalse(buildStatus.isEmpty())


        boolean canUpload = api.canUploadNewBuild(buildStatus)
        println "canUpload: ${canUpload}"


        if (buildStatus == 'Incomplete') {
//            String result = api.deleteLastBuild(appId, sandboxId)
//            println result
//            assertFalse(result.isEmpty())
        }
    }

}
