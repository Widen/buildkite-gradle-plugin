package com.widen.plugins.buildkite

class PluginBuilder {
    protected final Map<String, Object> model = [:]

    static Object expand(Object value) {
        if (value instanceof Closure) {
            def builder = new PluginBuilder()
            Closure cloned = (Closure) value.clone()
            cloned.delegate = builder
            cloned.resolveStrategy = Closure.DELEGATE_FIRST
            cloned.call()
            value = builder.model
        }

        if (value instanceof Map) {
            return value.collectEntries {
                [(it.key): expand(it.value)]
            }
        }

        if (value instanceof Iterable) {
            return value.collect {
                expand(it)
            }
        }

        return value
    }

    Object invokeMethod(String name, Object args) {
        Collection expandedArgs = args.collect {
            expand(it)
        }

        if (expandedArgs.isEmpty()) {
            model[name] = null
        } else if (expandedArgs.size() == 1) {
            model[name] = expandedArgs[0]
        } else {
            model[name] = expandedArgs
        }
    }

    void setProperty(String propertyName, Object newValue) {
        model[propertyName] = expand(newValue)
    }
}
