pipeline {
    agent any
    tools {
        jdk 'jdk-21'
        maven 'maven-3.9.9'
    }
    stages {
        stage('Checkout') {
            steps {
                git branch: 'test', url: 'https://github.com/CatalinaDM/Ejercicio5Cliente.git'
            }
        }
        stage('Version') {
            steps { sh 'java -version && mvn -version' }
        }
        stage('Build & Test') {
            steps { sh 'mvn -B clean verify' }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }
        stage('Semgrep - Analisis de codigo') {
            steps {
                sh '''
                    set -e
                    rm -rf semgrep-input semgrep-reports
                    mkdir -p semgrep-input semgrep-reports

                    find src/main/java src/test/java -type f -name '*.java' -exec cp --parents {} semgrep-input/ \\;
                    find src/main/resources/templates -type f -name '*.html' -exec cp --parents {} semgrep-input/ \\;
                    find src/main/resources/static/js -type f -name '*.js' ! -name 'bootstrap*' ! -name 'jquery*' ! -name 'popper*' -exec cp --parents {} semgrep-input/ \\;

                    docker run --rm \
                        -v jenkins_home:/var/jenkins_home \
                        -w "$WORKSPACE/semgrep-input" \
                        semgrep/semgrep:latest \
                        semgrep scan --config auto --json --no-git-ignore . \
                        > semgrep-reports/semgrep-report.json || true

                    echo "=========================================" > semgrep-reports/resumen.txt
                    echo "RESUMEN DE HALLAZGOS - FRONTEND" >> semgrep-reports/resumen.txt
                    echo "=========================================" >> semgrep-reports/resumen.txt
                    
                    TOTAL=$(grep -o '"check_id"' semgrep-reports/semgrep-report.json | wc -l)
                    echo "Total de hallazgos: $TOTAL" >> semgrep-reports/resumen.txt
                    echo "" >> semgrep-reports/resumen.txt
                    
                    if [ "$TOTAL" -gt 0 ]; then
                        echo "Detalle (Regla y Archivo):" >> semgrep-reports/resumen.txt
                        grep -o '"check_id": *"[^"]*", *"path": *"[^"]*"' semgrep-reports/semgrep-report.json \
                          | awk -F'"' '{print " - Regla: " $4 " | Archivo: " $8}' >> semgrep-reports/resumen.txt
                    else
                        echo "No se encontraron vulnerabilidades en el codigo." >> semgrep-reports/resumen.txt
                    fi
                      
                    cat semgrep-reports/resumen.txt
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'semgrep-reports/**', allowEmptyArchive: true
                }
            }
        }
        stage('OWASP Dependency Check') {
            steps {
                sh '''
                    echo "========================================="
                    echo "ESCANEO DE VULNERABILIDADES (OWASP)"
                    echo "========================================="
                    mvn -B org.owasp:dependency-check-maven:check \
                        -DskipTests \
                        -DfailBuildOnCVSS=11 \
                        -DexcludePaths=target \
                            -Dformats=HTML,XML,JSON \
                            -DossindexAnalyzerEnabled=false
                '''
            }
            post {
                always {
                    dependencyCheckPublisher pattern: 'target/dependency-check-report.xml'
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
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
