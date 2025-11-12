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

import com.exactpro.th2.common.metrics.PrometheusConfiguration
import com.exactpro.th2.common.schema.box.configuration.BoxConfiguration
import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.test.annotations.Th2AppFactory
import com.exactpro.th2.test.annotations.Th2TestFactory
import com.exactpro.th2.test.integration.ConfigurationWriter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Path
import kotlin.io.path.outputStream

public class Th2CommonFactoryExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    ParameterResolver {
    private class FactoryHolder(
        folder: Path,
    ) {
        val factory: CommonFactory =
            CommonFactory.createFromArguments(
                "-c",
                folder.toAbsolutePath().toString(),
            )
    }

    private lateinit var appCommonFactory: FactoryHolder
    private lateinit var testCommonFactory: FactoryHolder

    override fun beforeAll(context: ExtensionContext) {
        with(Th2.getAppConfigFolder(context)) {
            writeConfiguration("app")
        }
        with(Th2.getTestConfigFolder(context)) {
            writeConfiguration("test")
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        val appFolder: Path = Th2.getAppConfigFolder(context)
        appCommonFactory = FactoryHolder(appFolder)

        val testFolder: Path = Th2.getTestConfigFolder(context)
        testCommonFactory = FactoryHolder(testFolder)
    }

    override fun afterEach(context: ExtensionContext) {
        if (::testCommonFactory.isInitialized) {
            cleanUp(testCommonFactory, "test")
        }

        if (::appCommonFactory.isInitialized) {
            cleanUp(appCommonFactory, "app")
        }
    }

    private fun cleanUp(
        holder: FactoryHolder,
        name: String,
    ) {
        LOGGER.info { "Cleaning factory $name" }
        runCatching {
            holder.factory.close()
        }.onFailure {
            println("cannot close $name factory")
        }
        LOGGER.info { "Cleaning factory $name done" }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == CommonFactory::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): CommonFactory =
        when {
            parameterContext.isAnnotated(Th2AppFactory::class.java) -> {
                check(::appCommonFactory.isInitialized) { "app factory is not initialized" }
                appCommonFactory.factory
            }

            parameterContext.isAnnotated(Th2TestFactory::class.java) -> {
                check(::testCommonFactory.isInitialized) { "test factory is not initialized" }
                testCommonFactory.factory
            }

            else -> error("parameter must be annotated with ${Th2AppFactory::class} or ${Th2TestFactory::class} annotation")
        }

    private fun Path.writeConfiguration(name: String) {
        resolve(ConfigurationWriter.PROMETHEUS_CONFIG).outputStream().use {
            ConfigurationWriter.write(
                PrometheusConfiguration(enabled = false),
                it,
            )
        }
        resolve(ConfigurationWriter.BOX_CONFIG).outputStream().use {
            ConfigurationWriter.write(
                BoxConfiguration().apply {
                    setBoxName(name)
                },
                it,
            )
        }
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
