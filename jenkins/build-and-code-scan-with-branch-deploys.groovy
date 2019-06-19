// Test, scan, build, and deploy an application.
// Feature branches get their own namespace so everyone can
// deploy their version of code without interrupting dev/test/prod.
pipeline {

    // Environment variables always go up top
    environment {
       PCF_CREDS =    credentials('dev_pcf')
       PCF_ENDPOINT = 'api.sys.pcf.lab.homelab.net'
       PCF_ORG =      'demo'
       PCF_SPACE =    'demo'
       SONAR_URL =    'http://192.168.122.238:9000'
       SONAR_TOKEN =  credentials('hello_ci_sonarqube_token')
       APP_NAME =     'hello-ci'

       // To support manual deploys, default to the 'master' branch if gitlabSourceBranch is null.
       BRANCH =       "${env.gitlabSourceBranch != null ? env.gitlabSourceBranch : 'master'}" // via the Gitlab plugin

       // Append the branch name to the end of the CF app name. 
       // Do not append a suffix for the master branch.
       PUSH_APP_NAME= "${env.BRANCH == 'master' ? env.APP_NAME : env.APP_NAME + '-' + env.gitlabSourceBranch}"
    }

    // Don't care who runs it
    agent any


    stages {

        // Note: Requires that git be installed on the Jenkins machine/slave.
        stage('Fetch') {
            agent { 
                docker { 
                    image 'alpine' 
                } 
            }
            steps {
                sh 'printenv'
                git url: 'http://192.168.122.241/root/hello-ci.git', branch: "$BRANCH"
                stash name: 'REPO_CONTENTS', includes: '*'
            }
        }

        // Build/Test inside a container since builds can be messy.
        // Create a volume, mapping the `.m2` directory so we don't have
        // to download dependencies every time we build.
        stage('Test') {
            agent {
                docker {
                    image 'maven:3.6.1-jdk-8'
                    args  '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                sh 'mvn test'
                stash name: 'ARTIFACT', includes: 'target/'
            }
        }

        stage('Build') {
            agent {
                docker {
                    image 'maven:3.6.1-jdk-8'
                    args  '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                sh 'mvn package'
                stash name: 'ARTIFACT', includes: 'target/'
            }
        }

        stage('Security Scan') {
            agent {
                docker {
                    image 'maven:3.6.1-jdk-8'
                    args  '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                sh "mvn sonar:sonar -Dsonar.projectKey=tutorial -Dsonar.host.url=$SONAR_URL -Dsonar.login=$SONAR_TOKEN"
            }
        }

        stage('Deploy') {
            steps {
                unstash 'ARTIFACT'
                unstash 'REPO_CONTENTS'
                sh """
                curl --location "https://cli.run.pivotal.io/stable?release=linux64-binary&source=github" | tar zx 
                ./cf login --skip-ssl-validation -u $PCF_CREDS_USR -p $PCF_CREDS_PSW -a $PCF_ENDPOINT -o $PCF_ORG -s $PCF_SPACE
                ./cf push $PUSH_APP_NAME
                """
            }
        }
    }
}
