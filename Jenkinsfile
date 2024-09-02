pipeline {
    agent any
    tools {
        maven "maven"
    }
    stages {
        stage('Install Dependencies') {
            steps {
                script {
                    sh 'mvn clean'
                    sh 'mvn install'
                }
            }
        }

        stage('Docker Cleanup') {
            steps {
                script {
                    sh 'docker image prune -a -f'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    sh 'docker build -t fa-check-image .'
                }
            }
        }

        stage('Build Docker-Compose File') {
            steps {
                script {
                    sh 'docker-compose -f docker-compose.yml up -d'
                }
            }
        }
    }
    post {
        success {
            emailext(
                to: "sruthi.d@basispay.in",
                subject: "Build Successful: ${JOB_NAME} #${BUILD_NUMBER}",
                body: """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            color: #333;
                        }
                        .header {
                            background-color: #28a745;
                            color: #fff;
                            padding: 10px;
                            text-align: center;
                        }
                        .content {
                            padding: 20px;
                        }
                        .footer {
                            background-color: #f1f1f1;
                            padding: 10px;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        Jenkins Build Notification - Success
                    </div>
                    <div class="content">
                        <h2>Build Successful</h2>
                        <h3>Deployed Succcessful</h3>
                        <p><strong>Project:</strong> ${JOB_NAME}</p>
                        <p><strong>Build Number:</strong> ${BUILD_NUMBER}</p>
                        <p><strong>Build URL:</strong> <a href="${BUILD_URL}">${BUILD_URL}</a></p>
                    </div>
                    <div class="footer">
                        <p><strong>Thanks & Regards,</p>
                        <p><strong>Sruthi D.</p>
                        <p><strong>DevOps Team</p>
                    </div>
                </body>
                </html>
                """,
                mimeType: 'text/html'
            )
        }
        failure {
            emailext(
                to: "sruthi.d@basispay.in",
                subject: "Build Failed: ${JOB_NAME} #${BUILD_NUMBER}",
                body: """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            color: #333;
                        }
                        .header {
                            background-color: #dc3545;
                            color: #fff;
                            padding: 10px;
                            text-align: center;
                        }
                        .content {
                            padding: 20px;
                        }
                        .footer {
                            background-color: #f1f1f1;
                            padding: 10px;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        Jenkins Build Notification - Failure
                    </div>
                    <div class="content">
                        <h2>Build Failed</h2>
                        <p><strong>Project:</strong> ${JOB_NAME}</p>
                        <p><strong>Build Number:</strong> ${BUILD_NUMBER}</p>
                        <p><strong>Build URL:</strong> <a href="${BUILD_URL}">${BUILD_URL}</a></p>
                        <p><strong>Console Output:</strong></p>
                    </div>
                    <div class="footer">
                        <p><strong>Thanks & Regards,</p>
                        <p><strong>Sruthi D.</p>
                        <p><strong>DevOps Team</p>
                    </div>
                </body>
                </html>
                """,
                mimeType: 'text/html'
            )
        }
    }
}
