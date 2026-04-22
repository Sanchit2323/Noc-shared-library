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
        script.sh "docker-compose build"

        // STEP 2: Ensure BLUE is running
        try {
            script.echo "Ensuring BLUE is running..."
            script.sh "docker-compose up -d ${blue}"
        } catch (Exception e) {
            script.echo "BLUE might already be running"
        }

        // Step 3: Deploy GREEN
        script.echo "Deploying GREEN (new version)..."
        script.sh "docker-compose up -d ${green}"

        // wait for app
        script.echo "Waiting for GREEN to be ready..."
        script.sleep(20)

        try {
            // Step 3: Health check
            script.echo "Running health check on GREEN..."

            script.sh """
            for i in {1..5}; do
              docker exec empms-employee-green curl -f http://localhost:8083/employee/search/all && exit 0
              echo "Retrying health check..."
              sleep 5
            done
            exit 1
            """

            // Step 4: Switch traffic
            script.echo "Switching traffic to GREEN..."
            script.sh "docker stop ${blue}"

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

        try {
            script.echo "Stopping GREEN..."
            script.sh "docker stop ${green}"
        } catch (Exception e) {
            script.echo "⚠️ GREEN was not running"
        }

        // Start BLUE
        try {
            script.echo "Starting BLUE..."
            script.sh "docker start ${blue}"
        } catch (Exception e) {
            script.error("CRITICAL: BLUE container not found!")
        }

        script.echo "Rollback completed"
    }
}
