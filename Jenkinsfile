@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

boolean isReleaseBranch = (env.BRANCH_NAME =~ /^release\/.*/)
boolean isReleaseTag = (env.TAG_NAME =~ /^release-.*$/)

pipeline {
    agent {
        docker {
            // Our custom docker image
            image 'build-zulu-openjdk:8'
            label 'docker'
            registryUrl 'https://engineering-docker.software.r3.com/'
            registryCredentialsId 'artifactory-credentials'
            // Used to mount storage from the host as a volume to persist the cache between builds
            args '-v /tmp:/host_tmp -v /var/run/docker.sock:/var/run/docker.sock --group-add 999'
            alwaysPull true
        }
    }
    options { timestamps() }

    environment {
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        LOOPBACK_ADDRESS = "172.17.0.1"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        DOCKER_CREDENTIALS = credentials('docker-for-oracle-login')
        GRADLE_USER_HOME = "/host_tmp/gradle"
        SNYK_TOKEN  = credentials("c4-sdk-snyk")
    }

    parameters {
        booleanParam(name: 'RUN_FREIGHTER_TESTS', defaultValue: false, description: 'Publish Kotlin version to artifactory')
    }

    stages {

        stage("Auth Docker for Oracle Images") {
            steps {
                sh '''
                    docker login --username ${DOCKER_CREDENTIALS_USR} --password ${DOCKER_CREDENTIALS_PSW}
                   '''
            }
        }

        stage('Snyk Security') {
            // when {
            //     expression { isReleaseTag || isReleaseBranch }
            // }
            steps {
                script {
                    // Invoke Snyk for each Gradle sub project we wish to scan
                    def modulesToScan = ['contracts', 'workflows']
                    modulesToScan.each { module ->
                        snykSecurityScan("${env.SNYK_TOKEN}", "--sub-project=$module --configuration-matching='^runtimeClasspath\$' --prune-repeated-subdependencies --debug --target-reference='${env.BRANCH_NAME}' --project-tags=Branch='${env.BRANCH_NAME.replaceAll("[^0-9|a-z|A-Z]+","_")}'")
                    }
                }
            }
        }

        stage('Unit Tests') {
            steps {
                timeout(30) {
                    sh "./gradlew clean test --info --info --stacktrace --no-daemon"
                }
            }
        }

        stage('Integration Tests') {
            steps {
                timeout(30) {
                    sh "./gradlew integrationTest --info --stacktrace --no-daemon"
                }
            }
        }

        stage('Freighter Tests') {
           when {
                expression { params.DO_TEST }
            }
            steps {
                timeout(60) {
                    sh '''
                        export ARTIFACTORY_USERNAME=\"\${ARTIFACTORY_CREDENTIALS_USR}\"
                        export ARTIFACTORY_PASSWORD=\"\${ARTIFACTORY_CREDENTIALS_PSW}\"
                        ./gradlew freighterTest --info --stacktrace --no-daemon
                        '''
                }
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