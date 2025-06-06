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
            if (env.BRANCH_NAME == "master") {
                // Internal Salesforce registry publishing
                mavenVersionsSet([managed: false, auto_increment: false])
                mavenBuild maven_args: '-DskipUTs -DskipTests -DskipITs'
                mavenStageArtifacts()
                mavenPromoteArtifacts()

                // Push to public GitHub repository
                publishToPublicGitHub(version)
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

def publishToPublicGitHub(version) {
    withCredentials([usernamePassword(credentialsId: 'github-credentials', usernameVariable: 'GITHUB_USERNAME', passwordVariable: 'GITHUB_TOKEN')]) {
        sh """
            # TODO: Update it once we have a team account for the public repo
            git config --global user.email "wang.wang@salesforce.com"
            git config --global user.name "\${GITHUB_USERNAME}"
            
            # Add remote with credentials
            git remote add public https://\${GITHUB_USERNAME}:\${GITHUB_TOKEN}@github.com/forcedotcom/salesforce-commerce-intelligence-jdbc.git
            
            # Commit and push the JARs 
            cp ./target/cip-client-dataconnector-${version}.jar .
            git add ./cip-client-dataconnector-${version}.jar ./src/**/* ./pom.xml ./README.md
            git commit -m "Upload src content and JAR files to version ${version}"

            # Create a tag for the release
            git tag -a v${version} -m "Release v${version}"
            
            # push the tag to the public repo
            git push public main
            git push public v${version}
        """
    }
}
