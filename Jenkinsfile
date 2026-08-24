// Continuous integration for the request management service.
// Kept in the repository so that the pipeline evolves together with the code it builds.
pipeline {

    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 20, unit: 'MINUTES')
    }

    tools {
        jdk 'jdk-21'
    }

    stages {

        stage('Build and test') {
            steps {
                // verify runs the unit, slice, integration and BDD tests, then the coverage gate.
                sh './mvnw -B -ntp clean verify'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                    recordCoverage(tools: [[parser: 'JACOCO', pattern: 'target/site/jacoco/jacoco.xml']])
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B -ntp package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
    }

    post {
        failure {
            echo 'The build failed; the coverage gate and the architecture tests are part of it.'
        }
    }
}
