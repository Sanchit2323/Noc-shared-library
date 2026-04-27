package com.devops

class DeployManager implements Serializable {

    def script

    DeployManager(script) {
        this.script = script
    }

    // =========================
    // ✅ Validation
    // =========================
    def validate(env) {
        script.echo "Validating environment: ${env}"

        if (!['dev', 'staging', 'prod'].contains(env)) {
            script.error "Invalid ENV: ${env}"
        }
    }

    // =========================
    // ✅ Entry Point
    // =========================
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

    // =========================
    // ✅ DEV
    // =========================
    def deployDev() {
        script.echo "🚀 Deploying to DEV..."
        script.sh 'docker-compose up -d'
    }

    // =========================
    // ✅ STAGING
    // =========================
    def approveStaging() {
        script.input message: "Deploy to STAGING?", ok: "Approve"
    }

    def deployStaging() {
        script.echo "🚀 Deploying to STAGING..."
        script.sh 'docker-compose up -d'
    }

    // =========================
    // ✅ PROD (Blue-Green)
    // =========================
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
    // ✅ Detect Active Env
    // =========================
    def getActiveEnv() {

        def blueRunning = script.sh(
            script: "docker ps --format '{{.Names}}' | grep -w empms-employee-blue || echo ''",
            returnStdout: true
        ).trim()

        if (blueRunning) {
            return "blue"
        } else {
            return "green"
        }
    }

    // =========================
    // ✅ Health Check
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
    // ✅ Stop Container Safely
    // =========================
    def stopContainer(name) {

        def exists = script.sh(
            script: "docker ps -a --format '{{.Names}}' | grep -w ${name}",
            returnStdout: true
        ).trim()

        if (exists) {
            script.echo "Stopping ${name}..."
            script.sh "docker stop ${name}"
        } else {
            script.echo "${name} not found. Skipping..."
        }
    }

    // =========================
    // ✅ Switch Traffic
    // =========================
    def switchTraffic(env) {

        script.echo "🔀 Switching traffic to ${env}..."

        if (env == "green") {
            stopContainer("empms-employee-blue")
        } else {
            stopContainer("empms-employee-green")
        }
    }

    // =========================
    // ✅ Rollback (STRICT)
    // =========================
    def rollback(env) {

        if (env == 'prod') {

            script.echo "⚠️ Rolling back PRODUCTION..."

            def blueExists = script.sh(
                script: "docker ps -a --format '{{.Names}}' | grep -w empms-employee-blue",
                returnStdout: true
            ).trim()

            if (!blueExists) {
                script.error "❌ BLUE container not found. Cannot rollback!"
            }

            script.echo "Starting BLUE..."
            script.sh "docker start empms-employee-blue"

            def greenRunning = script.sh(
                script: "docker ps --format '{{.Names}}' | grep -w empms-employee-green",
                returnStdout: true
            ).trim()

            if (greenRunning) {
                script.echo "Stopping GREEN..."
                script.sh "docker stop empms-employee-green"
            }

            script.echo "✅ Rollback completed"
        }
    }
}
