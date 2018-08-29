/**
 * assertRequiredConfParams.groovy
 *
 * Validates that the given configuration has all of the required parameters
 *
 * @param conf configuration <code>Map</code>
 * @param requiredParams a list of required parameters.  Supports nested map keys like <code>veracode.appName</code>
 * @throws IllegalArgumentException is a required parameter is missing
 */
@SuppressWarnings("GrMethodMayBeStatic")
def call(Map conf, def requiredParams) {

    requiredParams.each() { p ->
        // Check to see if parameters exist in configuration 'conf.value'
        if (Eval.x(conf, "x.${p} == null")) {
            throw new IllegalArgumentException("${p} required parameter is not set")
        }
    }
}
/**
 * Alternative method to be used when pipelines need to check required job params instead of config
 * @param params
 * @param requiredParams
 * @return
 */
@SuppressWarnings("GrMethodMayBeStatic")
def jobRequiredParams(Map params, def requiredParams) {

    requiredParams.each() {value ->
        if (Eval.x(params, "x.${value} == ''")) {
            throw new IllegalArgumentException("${value} required parameter is not set")
        }
    }
}