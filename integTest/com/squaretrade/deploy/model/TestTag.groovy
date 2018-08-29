package com.squaretrade.deploy.model

import com.squaretrade.deploy.mock.MockUtils

class TestTag extends GroovyShellTestCase{

    GroovyObject objInstance
    String previousCheckedBranch
    String TEST_FILE_DIR = './test/test_files/'
    String TEST_FILE_NAME = 'testFile.txt'
    String TEST_BRANCH = 'testing/branch'

    @Override
    protected void setUp () {
        super.setUp()
        GroovyCodeSource gcs = new GroovyCodeSource(new File('vars/stageEnv.groovy'))
        objInstance = shell.parse(gcs)

        MockUtils.mockEcho(objInstance)
        MockUtils.mockEmailExt(objInstance)
        MockUtils.mockSh2(objInstance)

        MockUtils.mockProperties(
                objInstance,
                [
                        env: [
                                JOB_NAME: 'sandbox/DEVO-200'
                        ],
                        params: [
                                GIT_REPO: 'devops/jenkins-pipeline-shared-lib'
                        ]
                ]
        )

        createTestBranch()
    }

    @Override
    protected void tearDown() {
        deleteTestBranch()

        MockUtils.runShCommand("rm -rf ${TEST_FILE_DIR}")
    }

    /**
     * Returns the Tag instance with the given Tag Type.
     *
     * @param tagType
     * @return
     */
    private Tag getTagInstance(String tagType, String branchName) {
        Tag instance = new Tag(tagType, 'kbatista@squaretrade.com', objInstance)

        instance.metaClass.getCurrentBranch() {
            return branchName
        }

        return instance
    }

    /**
     * Delete a given tag
     *
     * @param command
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    private void deleteTag(String tagToDelete) {
        // Delete local tag
        MockUtils.runShCommand("git tag -d ${tagToDelete}")

        // Delete remote tag
        MockUtils.runShCommand("git push --delete origin ${tagToDelete}")
    }

    /**
     * Creates a test branch
     */
    private void createTestBranch() {
        // Gets current checked out branch before creates the testing branch
        previousCheckedBranch = MockUtils.runShCommand2("git rev-parse --abbrev-ref HEAD", true)

        // Create and checkout to a brand new local branch
        MockUtils.runShCommand("git checkout -b ${TEST_BRANCH}")
        MockUtils.runShCommand("git push origin ${TEST_BRANCH}")
    }

    /**
     * Deletes the test branch
     */
    private void deleteTestBranch() {
        // Checkout to previous branch
        MockUtils.runShCommand("git checkout ${previousCheckedBranch}")

        // Delete test branch (local and remote)
        MockUtils.runShCommand("git branch -D ${TEST_BRANCH}")
        MockUtils.runShCommand("git push origin --delete ${TEST_BRANCH}")
    }

    private void generateCommit() {
        MockUtils.runShCommand("mkdir ${TEST_FILE_DIR}")

        File testFile = new File(TEST_FILE_DIR + TEST_FILE_NAME)

        if (testFile.exists()) {
            testFile.write("New line")
        } else {
            MockUtils.runShCommand("touch ${TEST_FILE_DIR + TEST_FILE_NAME}")
        }

        MockUtils.runShCommand("git add ${TEST_FILE_DIR + TEST_FILE_NAME}")
        MockUtils.runShCommand("git commit -m \"adding text to test file\"")
        MockUtils.runShCommand("git push origin ${TEST_BRANCH}")
    }

    /**
     * Returns the pushed tag
     *
     * @return
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    private String getPushedTag() {
        String result = MockUtils.runShCommand2("git describe --abbrev=0", true)

        return result
    }

    void testBuildTagCreation() {
        Tag tagInstance = getTagInstance('build', TEST_BRANCH)
        tagInstance.buildProperties()

        String expectedTag = "build-${TEST_BRANCH}-jenkins-pipeline-shared-lib-01"

        tagInstance.handleTagging()

        assertEquals(expectedTag, getPushedTag())

        deleteTag(expectedTag)
    }

    void testReleaseCandidateTagCreation() {
        /*
         * To create a Release Candidate (rc) tag, the repo/branch a 'build' tag must exist before.
         */
        Tag buildTagInstance = getTagInstance('build', TEST_BRANCH)
        buildTagInstance.buildProperties()
        buildTagInstance.handleTagging()
        String buildTagResult = buildTagInstance.latestTag

        generateCommit()

        Tag rcTagInstance = getTagInstance('rc', TEST_BRANCH)
        rcTagInstance.buildProperties()

        String rcExpectedTag = "rc-${TEST_BRANCH}-jenkins-pipeline-shared-lib-01"

        rcTagInstance.handleTagging()

        assertEquals(rcExpectedTag, getPushedTag())

        /*
         * Delete all created tags
         */
        deleteTag(buildTagResult)
        deleteTag(rcExpectedTag)
    }

    void testReleaseTagCreation() {
        /*
         * To create a Release Candidate (rc) tag, the repo/branch a 'build' tag must exist before.
         */
        Tag buildTagInstance = getTagInstance('build', TEST_BRANCH)
        buildTagInstance.buildProperties()
        buildTagInstance.handleTagging()
        String buildTagResult = buildTagInstance.latestTag

        generateCommit()

        Tag rcTagInstance = getTagInstance('rc', TEST_BRANCH)
        rcTagInstance.buildProperties()
        rcTagInstance.handleTagging()
        String rcTagResult = rcTagInstance.latestTag

        generateCommit()

        Tag releaseTagInstance = getTagInstance('release', TEST_BRANCH)
        releaseTagInstance.buildProperties()

        String releaseExpectedTag = "release-${TEST_BRANCH}-jenkins-pipeline-shared-lib-01"

        releaseTagInstance.handleTagging()

        assertEquals(releaseExpectedTag, getPushedTag())

        /*
         * Delete all created tags
         */
        deleteTag(buildTagResult)
        deleteTag(rcTagResult)
        deleteTag(releaseExpectedTag)
    }
}