/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.test.integration

import mu.KotlinLogging
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.util.function.Consumer

public class RabbitMqIntegration private constructor(
    rabbitMqImageName: DockerImageName,
) : Startable {
    private val logger = KotlinLogging.logger { }
    internal val container = RabbitMQContainer(rabbitMqImageName)
        .withLogConsumer(Slf4jLogConsumer(logger).withSeparateOutputStreams())

    override fun start() {
        container.start()
    }

    override fun stop() {
        container.stop()
    }

    public fun configureContainer(block: Consumer<RabbitMQContainer>): RabbitMqIntegration = apply {
        block.accept(container)
    }

    public fun withDurableExchange(): RabbitMqIntegration = apply {
        withExchange(true)
    }

    public fun withDefaultExchange(): RabbitMqIntegration = apply {
        withExchange(false)
    }

    @JvmOverloads
    public fun withQueue(
        queueName: String,
        durable: Boolean = false,
    ): RabbitMqIntegration = apply {
        container.withQueue(queueName, false, durable, emptyMap())
    }

    public fun withBinding(
        queueName: String,
        routingKey: String,
    ): RabbitMqIntegration = apply {
        container.withBinding(
            DEFAULT_EXCHANGE,
            queueName,
            emptyMap(),
            routingKey,
            QUEUE_DESTINATION_TYPE,
        )
    }

    public fun purgeQueue(queueName: String): RabbitMqIntegration = apply {
        check(container.isRunning) { "cannot clear queue in not running container" }
        val result = container.execInContainer(
            "rabbitmqadmin",
            "purge",
            "queue",
            "name=$queueName",
        )
        check(result.exitCode == 0) { "cannot purge queue $queueName: ${result.stderr}" }
    }

    private fun withExchange(durable: Boolean) {
        container.withExchange(
            DEFAULT_EXCHANGE,
            DIRECT_EXCHANGE,
            false, // auto delete
            false, // internal
            durable, // durable
            emptyMap(),
        )
    }

    public companion object {
        public const val DEFAULT_EXCHANGE: String = "test_exchange"
        private const val DIRECT_EXCHANGE: String = "direct"
        private const val QUEUE_DESTINATION_TYPE: String = "queue"

        @JvmStatic
        public fun fromImage(imageName: String): RabbitMqIntegration =
            RabbitMqIntegration(DockerImageName.parse(imageName))

        @JvmStatic
        public fun defaultImage(): RabbitMqIntegration = RabbitMqIntegration(DEFAULT_IMAGE)

        @JvmField
        public val DEFAULT_IMAGE: DockerImageName =
            DockerImageName.parse("rabbitmq:3.10-management")
    }
}

public fun RabbitMqIntegration.container(block: RabbitMQContainer.() -> Unit): RabbitMqIntegration =
    configureContainer(Consumer(block))
