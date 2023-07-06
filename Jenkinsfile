@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
import groovy.transform.Field

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

pipeline {
    agent {
        dockerfile {
            filename '.ci/Dockerfile'
            additionalBuildArgs "--build-arg USER=stresstester"
            args '-v /var/run/docker.sock:/var/run/docker.sock --group-add 999'
        }
    }

    options { timestamps() }

    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        LOOPBACK_ADDRESS = "172.17.0.1"
        DOCKER_CREDENTIALS = credentials('docker-for-oracle-login')
        SNYK_TOKEN = credentials('c4-ent-snyk-api-token-secret')
    }

    parameters {
        booleanParam name: 'RUN_FREIGHTER_TESTS', defaultValue: false, description: 'Publish Kotlin version to artifactory'
    }

    stages {
        stage("Prep") {
            steps {
                sh '''
                    docker login --username ${DOCKER_CREDENTIALS_USR} --password ${DOCKER_CREDENTIALS_PSW}
                   '''
            }
        }

        stage('Build') {
            steps {
                sh './gradlew assemble --parallel'
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
        stage('Unit / Integration Tests') {
            steps {
                timeout(30) {
                    sh "./gradlew test integrationTest -Si --no-daemon --parallel"
                }
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
                expression { isReleaseTag() }
            }
            steps {
                sh './gradlew artifactoryPublish -Si'
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

def isReleaseTag() {
    return (env.TAG_NAME =~ /^release-.*$/)
}

def isReleaseCandidate() {
    return (isReleaseTag()) && (env.TAG_NAME =~ /.*-(RC|HC)\d+(-.*)?/)
}

def isReleaseBranch() {
    return (env.BRANCH_NAME =~ /^release\/.*$/) || (env.BRANCH_NAME =~ /^1.2$/)
}
