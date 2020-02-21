package com.widen.plugins.buildkite

/**
 * Common methods for objects that can receive environment variables.
 */
trait ConfigurableEnvironment {
    /**
     * Set an environment variable.
     */
    abstract void environment(String name, Object value)

    /**
     * Set a map of environment variables.
     */
    void environment(Map<String, Object> variables) {
        variables.each { name, value ->
            environment(name, value)
        }
    }

    /**
     * Set environment variables using a configure block.
     */
    void environment(@DelegatesTo(Map) Closure closure) {
        def map = [:]
        closure = (Closure) closure.clone()
        closure.delegate = map
        closure.resolveStrategy = Closure.OWNER_FIRST
        closure.call()
        environment(map)
    }
}
