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

import com.exactpro.th2.common.schema.message.configuration.MessageRouterConfiguration
import com.exactpro.th2.common.schema.message.impl.rabbitmq.configuration.RabbitMQConfiguration
import com.exactpro.th2.test.integration.ConfigurationWriter
import com.exactpro.th2.test.integration.RabbitMqConfigProvider
import com.exactpro.th2.test.integration.RabbitMqConfigurator
import com.exactpro.th2.test.integration.RabbitMqIntegration
import com.exactpro.th2.test.spec.RabbitMqSpec
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import java.lang.reflect.Field
import java.nio.file.Path
import kotlin.io.path.outputStream

public class Th2RabbitMqExtension :
    TestInstancePostProcessor,
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback {
    private var spec = RabbitMqSpec.create()
    private lateinit var rabbitmq: RabbitMqIntegration

    override fun postProcessTestInstance(
        testInstance: Any,
        context: ExtensionContext,
    ) {
        val fields: List<Field> = testInstance::class.findFields<RabbitMqSpec>().ifEmpty { return }
        spec = testInstance.getSingle(fields)
    }

    override fun beforeAll(context: ExtensionContext) {
        startMq(context.requiredTestInstance)
        val mqConnection = RabbitMqConfigProvider.getConnectionConfig(rabbitmq)
        with(Th2.getAppConfigFolder(context)) {
            writeConfig(mqConnection, RabbitMqConfigProvider.getComponentConfig(spec))
        }

        with(Th2.getTestConfigFolder(context)) {
            writeConfig(mqConnection, RabbitMqConfigProvider.getTestConfig(spec))
        }
    }

    override fun beforeEach(context: ExtensionContext?) {
        if (::rabbitmq.isInitialized) {
            LOGGER.info { "Cleaning queues" }
            RabbitMqConfigurator.purgeAllQueues(rabbitmq, spec)
        }
    }

    private fun Path.writeConfig(
        mqConnection: RabbitMQConfiguration,
        routerConfiguration: MessageRouterConfiguration,
    ) {
        resolve(ConfigurationWriter.ROUTER_MQ_CONFIG).outputStream().use {
            ConfigurationWriter.write(routerConfiguration, it)
        }
        resolve(ConfigurationWriter.CONNECTION_MQ_CONFIG).outputStream().use {
            ConfigurationWriter.write(mqConnection, it)
        }
    }

    private fun startMq(testInstance: Any) {
        rabbitmq =
            testInstance
                .getFieldOrDefault {
                    RabbitMqIntegration.defaultImage().withDefaultExchange()
                }.also {
                    RabbitMqConfigurator.setupQueues(it, spec)
                }
        rabbitmq.start()
    }

    override fun afterAll(context: ExtensionContext) {
        if (::rabbitmq.isInitialized) {
            runCatching {
                LOGGER.info { "Stopping rabbitmq" }
                rabbitmq.stop()
            }.onFailure {
                LOGGER.warn(it) { "cannot stop rabbitmq" }
            }
        }
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
