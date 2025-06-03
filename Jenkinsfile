@Library(['sfci-pipeline-sharedlib@master', 'build-pipeline-shared-cip-lib@master']) _

import net.sfdc.dci.BuildUtils
import net.sfdc.dci.MavenUtils

def envDef = [buildImage: '331455399823.dkr.ecr.us-east-2.amazonaws.com/sfci/cc-cnp/sfci-build-image:1.13.1']

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

        stage("Build and Publish") {
           if (env.BRANCH_NAME == "master") {
               // For master branch, we follow the release process
               mavenVersionsSet([managed: false, auto_increment: false])
               mavenBuild maven_args: '-DskipUTs -DskipTests -DskipITs -Ddockerfile.skip -Dmaven.test.skip=true -P SFCI'
               
               // Publish to Nexus
               mavenStageArtifacts(maven_args: '-DskipUTs -DskipTests -DskipITs -Ddockerfile.skip -Dmaven.test.skip=true -P SFCI')
               mavenPromoteArtifacts()
               
               // Copy JAR to repository root
               sh """
                  cp target/cip-client-dataconnector-${version}.jar ./cip-client-dataconnector-${version}.jar
                  git add cip-client-dataconnector-${version}.jar
                  git commit -m "Release version ${version} JAR"
                  git push origin master
               """
           } else {
               // For feature branches, we deploy snapshots
               updateFeatureBranchVersion()
               mavenBuild maven_args: '-DskipUTs -DskipTests -DskipITs -Ddockerfile.skip -Dmaven.test.skip=true -P SFCI'
               mavenDeploySnapshots(maven_args: '-DskipUTs -DskipTests -DskipITs -Ddockerfile.skip -Dmaven.test.skip=true -P SFCI')
          }
        }
    }
}

def updateFeatureBranchVersion() {
    branch = env.BRANCH_NAME.substring(env.BRANCH_NAME.indexOf('/') + 1).trim()
    echo("Branch name: '${branch}'")

    projectModel = readMavenPom file: "pom.xml"
    version = projectModel.getVersion().split("-")[0].trim()
    echo("Version: '${version}'")

    newVersion = "${version}-${branch}-SNAPSHOT"
    echo("New Version is '${newVersion}'")

    mavenBuild maven_goals: "versions:set -DnewVersion='${newVersion}' versions:commit"
    echo "Renamed private build version from '${version}' to '${newVersion}'"
} 