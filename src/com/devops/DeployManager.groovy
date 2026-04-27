package com.devops

class DeployManager implements Serializable {

    def script

    DeployManager(script) {
        this.script = script
    }

    // ✅ Validation
    def validate(env) {
        script.echo "Validating environment: ${env}"

        if (!['dev', 'staging', 'prod'].contains(env)) {
            script.error "Invalid ENV: ${env}"
        }
    }

    // ✅ Main Deploy Method
    def deploy(env) {

        if (env == 'dev') {
            deployDev()
        }

        if (env == 'staging') {
            approveStaging()
            deployStaging()
        }

        if (env == 'prod') {
            deployProd()
        }
    }

    // ✅ DEV (Auto)
    def deployDev() {
        script.echo "🚀 Deploying to DEV..."

        script.sh '''
        docker-compose up -d
        '''
    }

    // ✅ Manual Approval
    def approveStaging() {
        script.echo "⏳ Waiting for approval for STAGING..."
        script.input message: "Deploy to STAGING?", ok: "Approve"
    }

    // ✅ STAGING
    def deployStaging() {
        script.echo "🚀 Deploying to STAGING..."

        script.sh '''
        docker-compose up -d
        '''
    }

    // ✅ PROD (Blue-Green)
    def deployProd() {

        def active = getActiveEnv()
        def newEnv = active == "blue" ? "green" : "blue"

        script.echo "🔵 Active: ${active}"
        script.echo "🟢 Deploying NEW: ${newEnv}"

        // Deploy new version
        script.sh "docker-compose up -d empms-employee-${newEnv}"

        script.echo "⏳ Waiting for ${newEnv} to be ready..."
        script.sleep 30

        // Health check
        healthCheck(newEnv)

        // Switch traffic
        switchTraffic(newEnv)

        script.echo "✅ Production switched to ${newEnv}"
    }

    // =========================
    // ✅ Detect Active
    // =========================
    def getActiveEnv() {

        def blueRunning = script.sh(
            script: "docker ps --format '{{.Names}}' | grep empms-employee-blue || true",
            returnStdout: true
        ).trim()

        if (blueRunning) {
            return "blue"
        } else {
            return "green"
        }
    }

    // =========================
    // ✅ Health Check (FIXED)
    // =========================
    def healthCheck(env) {

        script.echo "🔍 Checking health of ${env}..."

        script.retry(5) {
            script.sleep 5
            script.sh """
            docker exec empms-employee-${env} wget -qO- http://localhost:8083/employee/healthz
            """
        }

        script.echo "✅ Health check passed for ${env}"
    }

    // =========================
    // ✅ Switch Traffic
    // =========================
    def switchTraffic(env) {

        script.echo "🔀 Switching traffic to ${env}..."

        if (env == "green") {
            script.sh "docker stop empms-employee-blue || true"
        } else {
            script.sh "docker stop empms-employee-green || true"
        }
    }

    // =========================
    // ✅ Rollback
    // =========================
    def rollback(env) {

        if (env == 'prod') {

            script.echo "⚠️ Rolling back PRODUCTION..."

            script.sh """
            docker start empms-employee-blue || true
            docker stop empms-employee-green || true
            """

            script.echo "✅ Rollback completed"
        } else {
            script.echo "Rollback not required for ${env}"
        }
    }
}
