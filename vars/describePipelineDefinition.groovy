#!/usr/bin/env groovy

/**
 * describePipelineDefinition.groovy
 *
 * Document a shared library pipeline.
 * Appends "Pipeline Definitions" section to the current job description if not present along with the
 * given sharedLibGitUrl
 *
 * @param sharedLibGitUrl git url to append to job description under 'Pipeline Definitions:'
 * @return
 */
def call(String sharedLibGitUrl) {

    String PIPELINE_DEFINITIONS = 'Pipeline Definitions:'
    String LF = '\n'

    def project = currentBuild.rawBuild.project
    if (!project.description.contains(PIPELINE_DEFINITIONS)) {
        if (!project.description.isEmpty()) {
            project.description += "${LF}"
        }
        project.description += PIPELINE_DEFINITIONS
    }

    if (!project.description.contains(sharedLibGitUrl)) {
        echo '==> Appending pipeline definition Git url to job description'
        project.description += LF + sharedLibGitUrl
    }

}
