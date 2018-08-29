package com.squaretrade.deploy.model

import com.squaretrade.deploy.mock.MockUtils
import org.junit.Before
import org.junit.Test

/**
 * NOTE: on macOS you must to go System Preferences-->Sharing and enable "Remote Login" for ssh and scp to work.
 */
class TestDataWarehouse {

    static String BACKUP_SCRIPT_NAME = 'dwhscripts_backup.sh'
    static String BACKUP_SCRIPT_SRC_DIR = 'resources/com/squaretrade/backup'
    static String DWHSCRIPTS_SRC_DIR_NAME = 'dwhscripts'
    static String DWHSCRIPTS_DEPLOY_TO_DIR_NAME = 'jenkins-dwhscripts'

    def dw
    File testDeployFromDir
    File testDeployToDir
    File dwhscriptsDeployDir
    File dwhscriptsDeployOldDir

    @Before
    void setup() {

        dw = new DataWarehouse()
        setupDeployFromDir()
        setupDeployToDir()
        // mock
        MockUtils.mockSh(dw, testDeployFromDir.toString())
        dw.sh 'pwd'
    }

    void setupDeployToDir() {
        testDeployToDir = File.createTempDir("deployTo-", "-tmpdir")
        //testDeployToDir.deleteOnExit()
        assert testDeployToDir.exists()
        println "testDeployToDir: ${testDeployToDir}"

        // make dhwscripts backup and deployment directories
        dwhscriptsDeployDir = new File("${testDeployToDir}/${DWHSCRIPTS_DEPLOY_TO_DIR_NAME}")
        dwhscriptsDeployOldDir = new File(dwhscriptsDeployDir.toString() + '-old')

        assert !dwhscriptsDeployDir.exists()
        assert !dwhscriptsDeployOldDir.exists()
    }

    void setupDeployFromDir() {
        testDeployFromDir = File.createTempDir("deployFrom-", "-tmpdir")
        testDeployFromDir.deleteOnExit()
        assert testDeployFromDir.exists()
        println "testDeployFromDir: ${testDeployFromDir}"

        // copy dwhscripts backup script to src deployment dir
        MockUtils.runShCommand("cp ${BACKUP_SCRIPT_SRC_DIR}/${BACKUP_SCRIPT_NAME} ${testDeployFromDir}")

        // copy dwhscripts dir to src deployment dir
        String workRoot = System.getenv('WORK_ROOT')
        MockUtils.runShCommand("cp -r ${workRoot}/datawarehouse/${DWHSCRIPTS_SRC_DIR_NAME} ${testDeployFromDir}")
    }


    @Test
    void testDeploy() {
        dw.deploy('localhost', testDeployToDir.toString(), BACKUP_SCRIPT_NAME, DWHSCRIPTS_SRC_DIR_NAME, DWHSCRIPTS_DEPLOY_TO_DIR_NAME, dw)

        assert new File("${testDeployToDir}/${BACKUP_SCRIPT_NAME}").exists()
        assert dwhscriptsDeployDir.exists()
        assert dwhscriptsDeployOldDir.exists()

        // TODO: assert that two directories are equivalent.

    }

}
