package com.squaretrade.deploy.model

/**
 *
 */
class TestGetNode extends GroovyShellTestCase {

    Script script

    @Override
    protected void setUp() {
        super.setUp()
        GroovyCodeSource gcs = new GroovyCodeSource(new File('vars/getNode.groovy'))
        script = shell.parse(gcs)
    }


    void test() {

        def envs = [
                master: [
                        NODE_LABELS: 'master'
                ],
                slavebee1: [
                        NODE_LABELS: 'slavebee slavebee-1.production.squaretrade.com slavebee1'
                ],
                slavebee2: [
                        NODE_LABELS: 'slavebee slavebee-2.production.squaretrade.com slavebee2'
                ],
                slavebee3: [
                        NODE_LABELS: 'slavebee slavebee-3.production.squaretrade.com slavebee3'
                ]
        ]

        envs.each {node_name,v ->
            String node = script.invokeMethod('call', v)
            assertEquals(node_name, node)
        }



    }


}
