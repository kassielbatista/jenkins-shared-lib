/**
 * List all installed plugins on SquareTrade Cloudbees (ci.squaretrade.com)
 *
 * @param conf
 * @return
 */
def call(){
    def plugins = jenkins.model.Jenkins.instance.getPluginManager().getPlugins()
    plugins.each {println "${it.getShortName()}: ${it.getVersion()}"}
}