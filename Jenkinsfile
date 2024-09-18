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
            CodeCoverageUtils.publishAndValidateCoverageReport(this, [run_sonar_analysis: true, tool_name:'jacoco', inclusion_patterns:'org/cip/auth/**', code_coverage_tool_version:'0.8.12'])
        }

        stage("Publish") {
            //deploy the compiled artifact to Nexus
            if (env.BRANCH_NAME == "master") {
                //follow release process described here: https://confluence.internal.salesforce.com/display/public/ZEN/Publishing+to+Nexus+on+SFCI
                mavenVersionsSet([managed: false, auto_increment: false])
                mavenBuild maven_args: '-DskipUTs -DskipTests -DskipITs'
                mavenStageArtifacts()
                mavenPromoteArtifacts()
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