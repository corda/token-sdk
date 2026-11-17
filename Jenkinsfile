#!groovy

/**
 * Kill already started job.
 * Assume new commit takes precendence and results from previous
 * unfinished builds are not required.
 * This feature doesn't play well with disableConcurrentBuilds() option
 */
@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
import groovy.transform.Field
killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

def isReleaseTag() {
    return (env.TAG_NAME =~ /^release-.*$/)
}

def isReleaseCandidate() {
    return (isReleaseTag()) && (env.TAG_NAME =~ /.*-(RC|HC)\d+(-.*)?/)
}

def isReleaseBranch() {
    return (env.BRANCH_NAME =~ /^release\/.*$/)
}

def isRelease = isReleaseTag() || isReleaseCandidate()
String publishOptions = (isReleaseBranch() || isRelease) ? "-s --info" : "--no-daemon -s -PversionFromGit"

pipeline {
    agent { label 'standard' }

    parameters {
        booleanParam name: 'DO_PUBLISH', defaultValue: (isRelease || isReleaseBranch()), description: 'Publish artifacts to Artifactory?'
        booleanParam name: 'RUN_FREIGHTER_TESTS', defaultValue: false, description: 'Publish Kotlin version to artifactory'
    }

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
    }

    triggers {
        cron (isReleaseBranch() ? 'H 0 * * 1,4' : '')
    }

    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        LOOPBACK_ADDRESS = "172.17.0.1"
        DOCKER_CREDENTIALS = credentials('docker-for-oracle-login')
        SNYK_TOKEN = credentials('c4-ent-snyk-api-token-secret')
        JAVA_HOME="/usr/lib/jvm/java-17-amazon-corretto"
    }

    stages {
        stage('Build') {
            steps {
                sh './gradlew -Pcompilation.warningsAsErrors=true assemble --parallel'
            }
        }

        stage('Snyk Security') {
            when {
                expression { isReleaseTag() || isReleaseBranch() }
            }
            steps {
                script {
                    // Invoke Snyk for each Gradle sub project we wish to scan
                    def modulesToScan = ['contracts', 'workflows']
                    modulesToScan.each { module ->
                        snykSecurityScan(env.SNYK_TOKEN, "--sub-project=$module --configuration-matching='^runtimeClasspath\$' --prune-repeated-subdependencies --debug --remote-repo-url='${env.GIT_URL}' --target-reference='${env.BRANCH_NAME}' --project-tags=Branch='${env.BRANCH_NAME.replaceAll("[^0-9|a-z|A-Z]+","_")}'", false, true)
                    }
                }
            }
        }

        stage('Unit Tests') {
            steps {
                sh "./gradlew test -Si"
            }
        }

        stage('Integration Tests') {
            steps {
                sh "./gradlew integrationTest -Si"
            }
        }

        stage('Freighter Tests') {
           when {
                expression { params.RUN_FREIGHTER_TESTS}
            }
            steps {
                timeout(60) {
                    sh './gradlew freighterTest -Si --no-daemon'
                }
            }
        }

        stage('Publish to Artifactory') {
            when {
                expression { params.DO_PUBLISH }
                beforeAgent true
            }
            steps {
                rtServer(
                        id: 'R3-Artifactory',
                        url: 'https://software.r3.com/artifactory',
                        credentialsId: 'artifactory-credentials'
                )
                rtGradleDeployer(
                        id: 'deployer',
                        serverId: 'R3-Artifactory',
                        repo: isRelease ? 'corda-lib' : 'corda-lib-dev'
                )
                rtGradleRun(
                        usesPlugin: true,
                        useWrapper: true,
                        switches: publishOptions,
                        tasks: 'artifactoryPublish',
                        deployerId: 'deployer',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
                rtPublishBuildInfo(
                        serverId: 'R3-Artifactory',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
            }
        }

        // Temporary: publish release-1.3.5 to the second Artifactory instance (software2) as well. Tag-only commit.
        stage('Publish to Artifactory (software2)') {
            when {
                expression { params.DO_PUBLISH && env.TAG_NAME == 'release-1.3.5' }
                beforeAgent true
            }
            steps {
                rtServer(
                        id: 'R3-Artifactory-2',
                        url: 'https://software2.r3.com/artifactory',
                        credentialsId: 'artifactory-credentials-2'
                )
                rtGradleDeployer(
                        id: 'deployer-2',
                        serverId: 'R3-Artifactory-2',
                        repo: 'corda-lib'
                )
                script {
                    try {
                        // Point the build (publishing and dependency resolution) at software2, which has the same
                        // repo structure. Match on the host only.
                        sh 'sed -i "s#//software\\.r3\\.com/artifactory#//software2.r3.com/artifactory#" gradle.properties'
                        withCredentials([
                                usernamePassword(credentialsId: 'artifactory-credentials-2',
                                                 usernameVariable: 'CORDA_ARTIFACTORY_USERNAME',
                                                 passwordVariable: 'CORDA_ARTIFACTORY_PASSWORD')]) {
                            rtGradleRun(
                                    usesPlugin: true,
                                    useWrapper: true,
                                    switches: publishOptions,
                                    tasks: 'artifactoryPublish',
                                    deployerId: 'deployer-2',
                                    buildName: env.ARTIFACTORY_BUILD_NAME
                            )
                        }
                    } finally {
                        // restore the tag's file so later steps use software.r3.com again
                        sh 'git checkout -- gradle.properties'
                    }
                }
                rtPublishBuildInfo(
                        serverId: 'R3-Artifactory-2',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
            }
        }

    }

    post {
        always {
            junit '**/build/test-results/**/*.xml'
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}

