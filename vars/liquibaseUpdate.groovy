#!/usr/bin/env groovy

/**
 * checkoutGitBranchOrTag: a custom step to invoke <code>gradlew liquibaseUpdate</code>
 *
 *
 * @param conf.dbCreateCredentialsId Jenkins Credential ID for the <code>gradlew liquibaseUpdate<code> create password
 * @param conf.dbMainCredentialsId  Jenkins Credential ID for the microservice <code>gradlew liquibaseUpdate<code> main password
 * @param conf.dbInitCredentialsId Jenkins Credential ID for the <code>gradlew liquibaseUpdate<code> init password
 * @param dbDefaultCredentialsId Jenkins Credential ID to use if any of above is not supplied.
 * @return
 */
def call(Map conf, String dbDefaultCredentialsId, String env) {

    // get db credentials
    dbMainCredId = conf.containsKey('dbMainCredentialsId') ? conf.get('dbMainCredentialsId') : dbDefaultCredentialsId
    withCredentials([string(credentialsId: dbMainCredId, variable: 'DB_MAIN_PWD')]) {
        dbCreateCredId = conf.containsKey('dbCreateCredentialsId') ? conf.get('dbCreateCredentialsId') : dbDefaultCredentialsId
        withCredentials([string(credentialsId: dbCreateCredId, variable: 'DB_CREATE_PWD')]) {
            dbInitCredId = conf.containsKey('dbInitCredentialsId') ? conf.get('dbInitCredentialsId') : dbDefaultCredentialsId
            withCredentials([string(credentialsId: dbInitCredId, variable: 'DB_INIT_PWD')]) {

                def dbInitProp = ''
                def dbCreateProp = ''
                def dbMainProp = ''

                // FIXME: move this logic into its own global var
                if (env.startsWith('stage') || env.startsWith('load') || env == 'hotfix') {
                    echo '==> Using Credential IDs:'
                    echo "   Init: ${dbInitCredId}"
                    echo "   Create: ${dbCreateCredId}"
                    echo "   Main: ${dbMainCredId}"

                    // See https://github.squaretrade.com/Engineering/st-liquibase-gradle-plugin/blob/master/src/main/groovy/com/squaretrade/gradle/liquibase/StLiquibaseConfigureActivitiesTask.groovy#L146
                    // for documentation on passing db passwords as Gradle project properties via the command-line
                    dbInitProp = "-PdbInitPassword='${DB_INIT_PWD}'"
                    dbCreateProp = "-PdbCreatePassword='${DB_CREATE_PWD}'"
                    dbMainProp = "-PdbMainPassword='${DB_MAIN_PWD}'"
                }
                sh "./gradlew ${dbInitProp} ${dbCreateProp} ${dbMainProp} update"
            }
        }
    }

}
