#!/usr/bin/env groovy
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

/**
 * cleanupNodeWS: cleans up the workspace of the given node
 * <p></p>
 * NOTE That running on a specific node is currently disabled because IRL, we get this too many times:
 * <pre>Waiting for next available executor on slavebee-1.production.squaretrade.com</pre>
 * <p></p>
 * this is due to a bug: <a href=http://jira.squaretrade.com/browse/DEVO-108>DEVO-108</a>,
 * so we are going to live with occasional error:
 * <pre>Required context class hudson.FilePath is missing error.</pre>
 *
 * @param node_label the label of the node
 *
 */
def call(String node_label) {

//    if (node_label?.trim()) {
        // clean up the workspace (assumes pipeline runs on one slave/agent).
        echo 'Cleaning up workspace...'

        try {
            timeout(time: 2, unit: 'MINUTES') {

                // if 'agent any' is used at the top level of the pipeline, then doing a deleteDir() in the post section
                // results in a 'Required context class hudson.FilePath is missing error.' So we have to explicitly keep
                // track of which node we're running on and then do the deleteDir() in an explicit node closure.
                //
                // https://issues.jenkins-ci.org/browse/JENKINS-43578
                // https://stackoverflow.com/questions/44531003/how-to-use-post-steps-with-jenkins-pipeline-on-multiple-agents

                // Unfortunately, the node closure switches the current workspace to '${WORKSPACE}@2'
                // so we have to save the current workspace and switch to it explicitly
//                def currentWorkspaceDir = pwd()
//
//                node(node_label) {
//                    dir(currentWorkspaceDir) {
                        deleteDir()
//                    }
//                }
            }
        }
        catch (FlowInterruptedException err) {
            // DEVO-154: ignore FlowInterruptedException to avoid failing the build as cleanup is a nice-to-have.
            echo 'Skipped workspace cleanup. Reason: timeout.'
        }
        catch (Exception e) {
            echo "Workspace cleanup aborted due to unexpected error:\n${e}"
        }
//    }
//    else {
//        echo 'Skipped workspace cleanup. Reason: no node label defined.'
//    }

}
