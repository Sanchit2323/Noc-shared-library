package com.devops

class DeployManager implements Serializable {

    def script

    DeployManager(script) {
        this.script = script
    }

    def validate(String env) {
        script.echo "Validating ${env}"

        if(env != "dev" && env != "staging" && env != "prod") {
            script.error "Invalid environment"
        }
    }

    def deploy(String env) {
        script.echo "Deploying to ${env}"
        script.sh "echo Deploy success"
    }

    def rollback(String env) {
        script.echo "Rollback in ${env}"
        script.sh "echo Rollback done"
    }
}
