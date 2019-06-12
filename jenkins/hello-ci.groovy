pipeline {
   agent any

   environment {
      PCF_CREDS = credentials('dev_pcf')
      PCF_ENDPOINT = 'api.sys.pcf.lab.home.net'
   }

   stages {
   
       // ref: https://jenkins.io/doc/book/pipeline/jenkinsfile/#usernames-and-passwords
       stage('Preparation') { 
          steps {
             git 'https://gitlab.com/drawsmcgraw/hello-ci'
          }
       }

       stage('Build') {
          steps {
             sh './mvnw package'
          }
       }

       stage('Deploy') {
          steps{
             sh 'curl --location "https://cli.run.pivotal.io/stable?release=linux64-binary&source=github" | tar zx'
             sh "./cf login --skip-ssl-validation -u $PCF_CREDS_USR -p $PCF_CREDS_PSW -a $PCF_ENDPOINT -o demo-org -s demo-space"
             sh './cf push'
          }
       }
   }
}
