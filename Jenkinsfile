pipeline {
    agent any

    tools {
        jdk 'jdk-21'
        maven 'maven-3.9.9'
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'test',
                    url: 'https://github.com/CatalinaDM/Ejercicio5Cliente.git'
            }
        }

        stage('Version') {
            steps {
                sh 'java -version && mvn -version'
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

     stage('Semgrep - Analisis de codigo') {
    steps {
        sh '''
            set -e

            echo "Preparando archivos..."
            rm -rf semgrep-input
            mkdir -p semgrep-input semgrep-reports

            find src/main/java src/test/java \
                -type f -name '*.java' \
                -exec cp --parents {} semgrep-input/ \\;

            echo "Archivos preparados:"
            find semgrep-input -type f -name '*.java'

            TOTAL=$(find semgrep-input -type f -name '*.java' | wc -l)

            echo "Total de archivos: $TOTAL"

            if [ "$TOTAL" -eq 0 ]; then
                echo "ERROR: No hay archivos Java."
                exit 1
            fi

            echo "Verificando montaje del volumen..."

            docker run --rm \
                -v jenkins_home:/var/jenkins_home \
                -w "$WORKSPACE/semgrep-input" \
                semgrep/semgrep:latest \
                sh -c "find . -type f -name '*.java'"

            echo "Ejecutando Semgrep..."

            docker run --rm \
                -v jenkins_home:/var/jenkins_home \
                -w "$WORKSPACE/semgrep-input" \
                semgrep/semgrep:latest \
                semgrep scan \
                --config auto \
                --json \
                --no-git-ignore \
                . \
                > semgrep-reports/semgrep-report.json

            echo "Validando reporte..."

            test -s semgrep-reports/semgrep-report.json

            grep -Fq '"errors":[]' semgrep-reports/semgrep-report.json

            echo "Reporte generado:"
            ls -lh semgrep-reports/semgrep-report.json
        '''
    }

    post {
        always {
            archiveArtifacts(
                artifacts: 'semgrep-reports/semgrep-report.json',
                allowEmptyArchive: true,
                fingerprint: true
            )
        }
    }
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