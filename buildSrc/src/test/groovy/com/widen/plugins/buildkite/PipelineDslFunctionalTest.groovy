package com.widen.plugins.buildkite

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Comprehensive functional tests for Buildkite Pipeline DSL.
 * Tests each DSL parameter and compares output against expected YAML files.
 */
class PipelineDslFunctionalTest extends Specification {
    @TempDir
    File testProjectDir

    File buildFile
    File settingsFile
    Path expectedOutputDir

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        settingsFile = new File(testProjectDir, 'settings.gradle')
        
        // Expected YAML files are stored in src/test/resources/expected-pipeline-output
        expectedOutputDir = Paths.get('src/test/resources/expected-pipeline-output')
    }

    /**
     * Helper method to run uploadPipeline task and capture output
     */
    private String runUploadPipeline() {
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('uploadPipeline', '--stacktrace')
            .withPluginClasspath()
            .build()

        assert result.task(':uploadPipeline').outcome == TaskOutcome.SUCCESS
        return result.output
    }

    /**
     * Helper method to extract JSON from task output
     */
    private String extractJsonFromOutput(String output) {
        // The JSON is printed after the task execution
        def lines = output.readLines()
        def jsonStartIndex = -1
        
        // Find where the JSON starts (after task messages)
        for (int i = 0; i < lines.size(); i++) {
            if (lines[i].trim().startsWith('{')) {
                jsonStartIndex = i
                break
            }
        }
        
        if (jsonStartIndex == -1) {
            throw new IllegalStateException("Could not find JSON output in:\n$output")
        }
        
        return lines.subList(jsonStartIndex, lines.size()).join('\n')
    }

    /**
     * Helper method to compare actual output with expected YAML, creating baseline if needed
     */
    private void assertMatchesExpectedYaml(String testName, String actualJson) {
        def expectedFile = expectedOutputDir.resolve("${testName}.yaml").toFile()
        
        // Parse the JSON output
        def jsonSlurper = new JsonSlurper()
        def actualData = jsonSlurper.parseText(actualJson)
        
        // Convert to YAML for comparison
        def yaml = new Yaml()
        def actualYaml = yaml.dump(actualData)
        
        if (!expectedFile.exists()) {
            // Create the expected file as baseline
            expectedFile.parentFile.mkdirs()
            expectedFile.text = actualYaml
            println "Created baseline YAML file: ${expectedFile.absolutePath}"
            return
        }
        
        // Compare with expected
        def expectedYaml = expectedFile.text
        assert actualYaml.trim() == expectedYaml.trim(), 
            "Output doesn't match expected YAML.\nExpected:\n$expectedYaml\n\nActual:\n$actualYaml"
    }

    def "test basic pipeline with environment variables"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    environment 'FOO', 'bar'
                    environment 'BUILD_NUMBER', '123'
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('basic-environment', json)
    }

    def "test pipeline interpolation and replace flags"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    interpolate = false
                    replace = true
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('interpolation-replace-flags', json)
    }

    def "test wait step"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    waitStep()
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('wait-step', json)
    }

    def "test wait step with continue on failure"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    waitStepContinueOnFailure()
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('wait-step-continue-on-failure', json)
    }

    def "test command step with basic properties"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Build'
                        command 'echo "Hello World"'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-basic', json)
    }

    def "test command step with multiple commands"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Multi-command'
                        commands 'echo "Step 1"', 'echo "Step 2"', 'echo "Step 3"'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-multiple-commands', json)
    }

    def "test command step with agent queue"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                defaultAgentQueue = 'default-queue'
                
                pipeline {
                    commandStep {
                        label 'Custom Queue'
                        command 'echo "test"'
                        agentQueue 'custom-queue'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-agent-queue', json)
    }

    def "test command step with agent queue and region"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Regional Queue'
                        command 'echo "test"'
                        agentQueue 'builder', 'us-west-2'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-agent-queue-region', json)
    }

    def "test command step with artifact paths"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Upload Artifacts'
                        command 'echo "build"'
                        artifactPath 'build/libs/*.jar'
                        artifactPath 'build/reports/**/*'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-artifacts', json)
    }

    def "test command step with concurrency"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Concurrent Job'
                        command 'echo "test"'
                        concurrency 'my-group', 2
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-concurrency', json)
    }

    def "test command step with environment variables"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'With Environment'
                        command 'echo "test"'
                        environment 'NODE_ENV', 'production'
                        environment 'DEBUG', 'true'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-environment', json)
    }

    def "test command step with parallelism"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Parallel Job'
                        command 'echo "test"'
                        parallelism 5
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-parallelism', json)
    }

    def "test command step with automatic retry"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Auto Retry'
                        command 'echo "test"'
                        automaticRetry {
                            exitStatus 255
                            limit 3
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-auto-retry', json)
    }

    def "test command step with skip"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Skipped'
                        command 'echo "test"'
                        skip()
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-skip', json)
    }

    def "test command step with skip reason"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Skipped with Reason'
                        command 'echo "test"'
                        skip 'Not needed for this build'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-skip-reason', json)
    }

    def "test command step with timeout"() {
        given:
        buildFile << """
            import java.time.Duration
            
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Timeout Step'
                        command 'echo "test"'
                        timeout Duration.ofMinutes(30)
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-timeout', json)
    }

    def "test command step with key and depends on"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'First Step'
                        command 'echo "first"'
                        key 'first'
                    }
                    commandStep {
                        label 'Second Step'
                        command 'echo "second"'
                        key 'second'
                        dependsOn 'first'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-key-depends-on', json)
    }

    def "test command step with branch conditionals"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'On Default Branch'
                        command 'echo "default"'
                        onDefaultBranch()
                    }
                    commandStep {
                        label 'Not On Default Branch'
                        command 'echo "other"'
                        notOnDefaultBranch()
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-branch-conditionals', json)
    }

    def "test command step with custom if condition"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Custom Condition'
                        command 'echo "test"'
                        ifCondition 'build.pull_request.id != null'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-if-condition', json)
    }

    def "test command step with allow dependency failure"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Cleanup'
                        command 'echo "cleanup"'
                        allowDependencyFailure()
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-allow-dependency-failure', json)
    }

    def "test command step with branches filter"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Branch Filter'
                        command 'echo "test"'
                        branches 'main', 'develop', 'feature/*'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-branches', json)
    }

    def "test command step with soft fail boolean"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Soft Fail'
                        command 'echo "test"'
                        softFail true
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-soft-fail-bool', json)
    }

    def "test command step with soft fail exit statuses"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Soft Fail Exit Codes'
                        command 'echo "test"'
                        softFail 1, 2, 255
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-soft-fail-exit-codes', json)
    }

    def "test command step with docker plugin"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Docker Build'
                        command 'gradle build'
                        docker {
                            image 'openjdk:11'
                            alwaysPull()
                            environment 'GRADLE_OPTS'
                            environment 'JAVA_HOME', '/usr/lib/jvm/java-11'
                            propagateEnvironment()
                            volume '/tmp/cache', '/cache'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-docker-plugin', json)
    }

    def "test command step with docker plugin entrypoint and shell"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Docker Custom'
                        command 'echo "test"'
                        docker {
                            image 'alpine:latest'
                            entrypoint '/bin/sh'
                            shell '/bin/sh', '-c'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-docker-entrypoint-shell', json)
    }

    def "test command step with docker compose plugin"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Docker Compose'
                        command 'gradle test'
                        dockerCompose {
                            build 'app', 'db'
                            run 'app'
                            environment 'DATABASE_URL'
                            environment 'REDIS_URL', 'redis://localhost:6379'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-docker-compose-plugin', json)
    }

    def "test command step with docker compose push and cache"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Docker Compose Advanced'
                        command 'gradle build'
                        dockerCompose {
                            run 'app'
                            push 'app', 'myrepo/app', 'latest'
                            imageRepository 'myrepo'
                            imageName 'my-build-\${BUILDKITE_BUILD_NUMBER}'
                            cacheFrom 'app', 'myrepo/app', 'latest'
                            composeFile 'docker-compose.ci.yml'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-docker-compose-advanced', json)
    }

    def "test command step with custom plugin"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    commandStep {
                        label 'Custom Plugin'
                        command 'echo "test"'
                        plugin 'my-org/my-plugin#v1.0.0', [key: 'value', nested: [foo: 'bar']]
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('command-step-custom-plugin', json)
    }

    def "test block step basic"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    blockStep 'Deploy to Production'
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('block-step-basic', json)
    }

    def "test block step with prompt"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    blockStep('Manual Approval') {
                        prompt 'Please review the changes before continuing'
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('block-step-prompt', json)
    }

    def "test block step with text field"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    blockStep('Release Information') {
                        textField('Release Notes', 'release_notes') {
                            hint 'Enter the release notes for this version'
                            required true
                            defaultValue 'Bug fixes and improvements'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('block-step-text-field', json)
    }

    def "test block step with select field"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    blockStep('Select Environment') {
                        selectField('Target Environment', 'environment') {
                            option 'Development', 'dev'
                            option 'Staging', 'staging'
                            option 'Production', 'prod'
                            defaultValue 'dev'
                            required true
                            hint 'Choose the deployment target'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('block-step-select-field', json)
    }

    def "test block step with multiple select field"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    blockStep('Select Services') {
                        selectField('Services to Deploy', 'services') {
                            option 'API', 'api'
                            option 'Web', 'web'
                            option 'Worker', 'worker'
                            multiple true
                            defaultValues 'api', 'web'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('block-step-select-multiple', json)
    }

    def "test trigger step basic"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    triggerStep 'deployment_pipeline'
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('trigger-step-basic', json)
    }

    def "test trigger step with label and async"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    triggerStep('deployment_pipeline') {
                        label 'Deploy'
                        async true
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('trigger-step-async', json)
    }

    def "test trigger step with build configuration"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline {
                    triggerStep('deployment_pipeline') {
                        label 'Trigger Deploy'
                        build {
                            message 'Deploy from main'
                            commit 'abc123'
                            branch 'main'
                            environment 'DEPLOY_ENV', 'production'
                            metadata {
                                version = '1.0.0'
                                releaseNotes = 'Initial release'
                            }
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('trigger-step-build-config', json)
    }

    def "test complex pipeline with multiple step types"() {
        given:
        buildFile << """
            import java.time.Duration
            
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                defaultAgentQueue = 'builder'
                
                pipeline {
                    environment 'CI', 'true'
                    environment 'BUILD_ENV', 'test'
                    
                    commandStep {
                        label 'Unit Tests'
                        key 'tests'
                        command 'gradle test'
                        artifactPath 'build/test-results/**/*'
                        parallelism 3
                        timeout Duration.ofMinutes(10)
                        docker {
                            image 'openjdk:11'
                            propagateEnvironment()
                        }
                    }
                    
                    waitStep()
                    
                    commandStep {
                        label 'Integration Tests'
                        command 'gradle integrationTest'
                        dependsOn 'tests'
                        allowDependencyFailure()
                        dockerCompose {
                            run 'app'
                            build 'app', 'db'
                        }
                    }
                    
                    blockStep('Deploy?') {
                        prompt 'Ready to deploy?'
                        selectField('Environment', 'env') {
                            option 'Staging', 'staging'
                            option 'Production', 'prod'
                            required true
                        }
                    }
                    
                    triggerStep('deployment_pipeline') {
                        label 'Deploy'
                        async false
                        build {
                            branch 'main'
                            environment 'TARGET_ENV', 'production'
                        }
                    }
                }
            }
        """

        when:
        def output = runUploadPipeline()
        def json = extractJsonFromOutput(output)

        then:
        assertMatchesExpectedYaml('complex-pipeline', json)
    }
}

