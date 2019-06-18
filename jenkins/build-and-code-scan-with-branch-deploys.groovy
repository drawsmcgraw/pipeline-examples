// Pull code, test it, use SonarQube to scan it, build it, and deploy it.
// Name the app for the branch that the code came from. This means that if 
// someone pushes a feature branch, they get to see their code deployed
// in a separate environment.
pipeline {

    environment {
       PCF_CREDS =    credentials('dev_pcf')
       PCF_ENDPOINT = 'api.sys.pcf.lab.homelab.net'
       PCF_ORG =      'demo'
       PCF_SPACE =    'demo'
       SONAR_URL =    'http://sonar-server:9000'
       SONAR_TOKEN =  credentials('hello_ci_sonarqube_token')
       BRANCH =       "$gitlabSourceBranch" // via the Gitlab plugin
       APP_NAME =     'hello-ci'
    }

    // Don't care who runs it
    agent any


    stages {

        // Note: Requires that git be installed on the Jenkins machine/slave.
        stage('Fetch') {
            steps {
                git url: 'http://gitlab-repo.com/root/hello-ci.git', branch: "$BRANCH"
            }
        }

        // Build/Test inside a container since builds can be messy.
        // Use the 'stash' step so our build artifact is available for the Deploy stage.
        stage('Test') {
            agent {
                docker {
                    image 'maven:3.6.1-jdk-8'
                    args  '-v $HOME/.m2:/root/.m2'
                }
            }
            steps {
                sh 'mvn test'
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
                sh "mvn sonar:sonar   -Dsonar.projectKey=tutorial   -Dsonar.host.url=$SONAR_URL   -Dsonar.login=$SONAR_TOKEN"
            }
        }

        stage('Deploy') {
            steps {
                unstash 'ARTIFACT'
                sh """
                curl --location "https://cli.run.pivotal.io/stable?release=linux64-binary&source=github" | tar zx 
                ./cf login --skip-ssl-validation -u $PCF_CREDS_USR -p $PCF_CREDS_PSW -a $PCF_ENDPOINT -o $PCF_ORG -s $PCF_SPACE
                ./cf push $APP_NAME-$BRANCH
                """
            }
        }
    }
}
