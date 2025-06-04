/*
 * Salesforce CI Pipeline Shared Library - contains reusable classes and functions for use in Jenkinsfile
 */
@Library(['sfci-pipeline-sharedlib@master', 'build-pipeline-shared-cip-lib@master']) _

/*
 * Import if using classes from above shared library
 */
import net.sfdc.dci.BuildUtils
import net.sfdc.dci.MavenUtils
import net.sfdc.dci.CodeCoverageUtils

def envDef = [buildImage: '871501607754.dkr.ecr.us-west-2.amazonaws.com/sfci/cc-commerce-intelligence-platform/cip-build-image:1.0.1']
def serviceName = "client-dataconnector"
def serverImagetag = "cip-dataconnector-client"
def publicGitHubRepo = "https://github.com/forcedotcom/salesforce-commerce-intelligence-jdbc.git"

env.RELEASE_BRANCHES = ['master']

executePipeline(envDef) {
    def branchName = ''
    def commitSha = ''
    def version = ''
    def proxy_settings = "-DproxySet=true -Dhttp.proxyHost=${env.publicProxy} -Dhttp.proxyPort=8080"

    ansiColor('xterm') {
        stage("Prepare") {
            branchName = BuildUtils.getCurrentBranch(this)
            checkout scm
            commitSha = BuildUtils.getLatestCommitSha(this)
            echo "Building commit ${commitSha} on build ${env.BUILD_ID} for branch ${branchName}"
            mavenInit()
        }

        version = MavenUtils.getDefaultVersion(this)

        stage("Build and Test") {
            mavenBuild maven_goals: '-U -C $proxy_settings clean install'
        }

        stage("Analyze") {
            parallel analyse: {
                mavenBuild maven_goals: "pmd:pmd com.github.spotbugs:spotbugs-maven-plugin:spotbugs"
                recordIssues aggregatingResults: true, tools: [pmdParser(pattern: '**/pmd.xml'), spotBugs(pattern: '**/spotbugsXml.xml', useRankAsPriority: true), java(), mavenConsole(), javaDoc()]
            }
        }

        stage('Publishing Code Coverage'){
            CodeCoverageUtils.publishAndValidateCoverageReport(this, [run_sonar_analysis: true, tool_name:'jacoco', inclusion_patterns:'com/salesforce/commerce/intelligence/jdbc/client/**', code_coverage_tool_version:'0.8.12'])
        }

        stage("Publish") {
            // TODO: need to change back to master branch
            if (env.BRANCH_NAME != "master") {
                // Internal Salesforce registry publishing
                // mavenVersionsSet([managed: false, auto_increment: false])
                // mavenBuild maven_args: '-DskipUTs -DskipTests -DskipITs'
                // mavenStageArtifacts()
                // mavenPromoteArtifacts()

                // Public Maven repository publishing
                // withCredentials([usernamePassword(credentialsId: 'ossrh-credentials', usernameVariable: 'OSSRH_USERNAME', passwordVariable: 'OSSRH_PASSWORD')]) {
                //     mavenBuild maven_goals: "deploy -DskipUTs -DskipTests -DskipITs -Dossrh.username=${OSSRH_USERNAME} -Dossrh.password=${OSSRH_PASSWORD}"
                // }

                // Push to public GitHub repository
                withCredentials([usernamePassword(credentialsId: 'github-credentials', usernameVariable: 'GITHUB_USERNAME', passwordVariable: 'GITHUB_TOKEN')]) {
                    sh """
                        git config --global user.email "wang.wang@salesforce.com"
                        git config --global user.name "cw112233"
                        
                        # Remove existing remote if it exists
                        git remote remove public || true
                        
                        # Add remote with credentials
                        git remote add public https://${GITHUB_USERNAME}:${GITHUB_TOKEN}@github.com/forcedotcom/salesforce-commerce-intelligence-jdbc.git
                        
                        # Push current branch to public repo with credentials
                        git checkout -b cw/add-jenkins-for-publish
                        git push -f public cw/add-jenkins-for-publish
                        
                        # Check if version exists and bump if needed
                        if gh release view v${version} --repo forcedotcom/salesforce-commerce-intelligence-jdbc 2>/dev/null; then
                            # Extract version numbers
                            MAJOR=\$(echo ${version} | cut -d. -f1)
                            MINOR=\$(echo ${version} | cut -d. -f2)
                            PATCH=\$(echo ${version} | cut -d. -f3 | cut -d- -f1)
                            # Bump patch version
                            NEW_VERSION="\${MAJOR}.\${MINOR}.\$((PATCH + 1))-SNAPSHOT"
                            echo "Version ${version} exists, bumping to \${NEW_VERSION}"
                            version="\${NEW_VERSION}"
                        fi
                        
                        # Get the commit history for release notes
                        RELEASE_NOTES=\$(git log --pretty=format:"%h - %s (%an)" --no-merges -n 10)
                        
                        # Create release with custom notes from our source repository
                        gh release create v${version} \
                            --repo forcedotcom/salesforce-commerce-intelligence-jdbc \
                            --title "Release v${version}" \
                            --notes "\${RELEASE_NOTES}"
                        
                        # Then upload the JAR files to the release
                        gh release upload v${version} target/cip-client-dataconnector-${version}.jar --repo forcedotcom/salesforce-commerce-intelligence-jdbc
                        gh release upload v${version} target/original-cip-client-dataconnector-${version}.jar --repo forcedotcom/salesforce-commerce-intelligence-jdbc
                    """
                }
            } else {
                //for non master branches, we just deploy a snapshot
                cip_utils.updateFeatureBranchVersion()
                mavenDeploySnapshots maven_args: '-DskipUTs -DskipTests -DskipITs'
            }
        }

        stage('GUS Compliance'){
            git2gus()
        }
    }
}
