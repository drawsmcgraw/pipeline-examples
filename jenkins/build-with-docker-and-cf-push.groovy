// Simple example of fetching code, building it using a Docker container,
// and pushing it to production on Cloud Foundry.

pipeline {

    // Environment variables always go up top. That way, you know where to find them :)
    environment {

       // This function call to credentials() will create two extra
       // variables: PCF_CREDS_USR and PCF_CREDS_PSW.
       // ref: https://jenkins.io/doc/book/pipeline/jenkinsfile/#handling-credentials
       PCF_CREDS =    credentials('dev_pcf')

       PCF_ENDPOINT = 'api.sys.pcf.lab.homelab.net'
       PCF_ORG =      'demo'
       PCF_SPACE =    'demo'
    }

    // Don't care who runs it
    agent any

    stages {

        // Note: Requires that git be installed on the Jenkins machine/slave.
        stage('Fetch') {
            steps {
                git 'https://gitlab.com/drawsmcgraw/hello-ci.git'
            }
        }

        // Build inside a container since builds can be messy.
        stage('Build') {
            agent {
                docker {
                    image 'maven:3.6.1-jdk-8'
                }
            }
            steps {
                sh 'mvn package'
            }
        }

        stage('Deploy') {
            steps {

                // Multiline shell steps are supported (and useful for readability)
                sh """
                curl --location "https://cli.run.pivotal.io/stable?release=linux64-binary&source=github" | tar zx 
                ./cf login --skip-ssl-validation -u $PCF_CREDS_USR -p $PCF_CREDS_PSW -a $PCF_ENDPOINT -o $PCF_ORG -s $PCF_SPACE
                ./cf push
                """
            }
        }
    }
}
