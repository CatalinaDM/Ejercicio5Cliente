pipeline {
    agent any
    tools {
        jdk 'jdk-21'          // Manage Jenkins > Tools > JDK installations
        maven 'maven-3.9.9'   // Manage Jenkins > Tools > Maven installations
    }
    stages {
        stage('Version') {
            steps { sh 'java -version && mvn -version' }
        }
        stage('Build & Test') {
            steps { sh 'mvn -B clean verify' }
            post { always { junit 'target/surefire-reports/*.xml' } }
        }
        stage('SonarQube') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh 'mvn -B sonar:sonar -Dsonar.projectKey=Ejercicio5Cliente'
                }
            }
        }
    }
}
