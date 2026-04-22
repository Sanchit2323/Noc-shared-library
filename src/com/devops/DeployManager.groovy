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
        script.echo "Starting deployment to ${env} using Blue-Green..."

        def blue = "empms-employee-blue"
        def green = "empms-employee-green"

        // Step 1: Build images
        script.echo "Building Docker images..."
        script.sh "make build-images"

        // Step 2: Deploy GREEN
        script.echo "Deploying GREEN (new version)..."
        script.sh "docker-compose up -d ${green}"

        // wait for container
        script.sleep(10)

        try {
            // Step 3: Health check
            script.echo "Running health check on GREEN..."

            script.sh "curl -f http://localhost:8085/employee/search/all"

            // Step 4: Switch traffic
            script.echo "Switching traffic to GREEN..."
            script.sh "docker stop ${blue} || true"

            script.echo "Deployment SUCCESS ✅"

        } catch (Exception e) {

            script.echo "Deployment FAILED ❌ → Triggering rollback..."
            rollback(env)

            script.error("Deployment failed and rollback executed")
        }
    }

    def rollback(String env) {
        script.echo "Rollback triggered in ${env}"

        def blue = "empms-employee-blue"
        def green = "empms-employee-green"

        script.echo "Stopping GREEN (new version)..."
        script.sh "docker stop ${green} || true"

        script.echo "Starting BLUE (old stable version)..."
        script.sh "docker start ${blue} || true"

        script.echo "Rollback completed"
    }
}
