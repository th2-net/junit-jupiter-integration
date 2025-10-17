/*
 * Copyright 2023-2025 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.test.extension

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.util.LinkedList

public class CleanupExtension :
    BeforeEachCallback,
    BeforeAllCallback,
    AfterEachCallback,
    ParameterResolver {
    override fun beforeEach(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(AFTER_TEST_KEY, ClosableRegistry(Registry()))
    }

    override fun beforeAll(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(AFTER_ALL_KEY, ClosableRegistry(Registry()))
    }

    override fun afterEach(context: ExtensionContext) {
        // we do it here because the automated cleanup happens after all `afterEach` methods are invoked
        // but we need to control the order of execution
        context.getStore(NAMESPACE).remove(AFTER_TEST_KEY, ClosableRegistry::class.java)?.close()
    }

    public class Registry {
        internal val resources = LinkedList<Pair<String, AutoCloseable>>()

        public fun add(resource: AutoCloseable) {
            add(RandomStringUtils.insecure().nextAlphabetic(10), resource)
        }

        public fun add(
            name: String,
            resource: AutoCloseable,
        ) {
            check(resources.find { it.first == name } == null) {
                "duplicated resource $name"
            }
            resources.add(name to resource)
        }
    }

    private class ClosableRegistry(
        val registry: Registry,
    ) : AutoCloseable {
        override fun close() {
            registry.resources.descendingIterator().forEach { (name, resource) ->
                runCatching {
                    LOGGER.info { "closing resource '$name'" }
                    resource.close()
                }.onFailure {
                    LOGGER.warn(it) { "cannot close resource '$name' (${resource::class})" }
                }
            }
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == Registry::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any {
        val store = extensionContext.getStore(NAMESPACE)
        // if we have registry for AFTER_TEST_KEY key it means the parameter is resolved for test method
        // or in before
        val autoClosableRegistry = (
            store.get(AFTER_TEST_KEY, ClosableRegistry::class.java)
                ?: store.get(AFTER_ALL_KEY, ClosableRegistry::class.java)
            )
        return autoClosableRegistry?.registry ?: error("registry is not created")
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger { }
        private val NAMESPACE: Namespace = Namespace.create(CleanupExtension::class)
        private const val AFTER_ALL_KEY: String = "clean_after_all"
        private const val AFTER_TEST_KEY: String = "clean_after_test"
    }
}
