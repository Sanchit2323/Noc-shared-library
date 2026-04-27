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

        script.echo "🚀 Starting BLUE..."
        script.sh 'docker-compose up -d empms-employee-blue'

        script.echo "🚀 Deploying GREEN..."
        script.sh 'docker-compose up -d empms-employee-green'

        script.echo "⏳ Waiting for GREEN to be ready..."
        script.sleep 30

        script.echo "🔍 Running Health Check..."
        healthCheck()

        script.echo "✅ Production deployment successful!"
    }

    // ✅ Health Check
    def healthCheck() {

        def status = script.sh(
            script: 'curl -s -o /dev/null -w "%{http_code}" http://localhost/employee/healthz',
            returnStdout: true
        ).trim()

        script.echo "Health Check Status: ${status}"

        if (status != "200") {
            script.error "❌ Health check failed!"
        }
    }

    // ✅ Rollback
    def rollback(env) {

        if (env == 'prod') {

            script.echo "⚠️ Rolling back PRODUCTION..."

            script.sh '''
            docker stop empms-employee-green || true
            docker start empms-employee-blue || true
            '''

            script.echo "✅ Rollback completed"
        } else {
            script.echo "Rollback not required for ${env}"
        }
    }
}
