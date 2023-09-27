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

import com.exactpro.th2.common.schema.message.QueueAttribute
import com.exactpro.th2.common.schema.message.configuration.MessageRouterConfiguration
import com.exactpro.th2.common.schema.message.configuration.MqRouterFilterConfiguration
import com.exactpro.th2.common.schema.message.configuration.QueueConfiguration
import com.exactpro.th2.common.schema.message.impl.rabbitmq.configuration.RabbitMQConfiguration
import com.exactpro.th2.test.spec.PinSpec
import com.exactpro.th2.test.spec.RabbitMqSpec

internal object RabbitMqConfigProvider {

    fun getConnectionConfig(rabbitmq: RabbitMqIntegration): RabbitMQConfiguration {
        return RabbitMQConfiguration(
            host = rabbitmq.container.host,
            vHost = "",
            port = rabbitmq.container.amqpPort,
            username = rabbitmq.container.adminUsername,
            password = rabbitmq.container.adminPassword,
            exchangeName = RabbitMqIntegration.DEFAULT_EXCHANGE,
        )
    }

    /**
     * Returns [MessageRouterConfiguration] that can be used by component under the test
     */
    fun getComponentConfig(spec: RabbitMqSpec): MessageRouterConfiguration {
        val publishers = spec.pinsSpec.publishers.pins.mapValues { (name, spec) ->
            pinSpecToConfiguration(name, spec, isPublisher = true)
        }

        val subscribers = spec.pinsSpec.subscribers.pins.mapValues { (name, spec) ->
            pinSpecToConfiguration(name, spec, isPublisher = false)
        }

        return MessageRouterConfiguration(
            queues = publishers + subscribers,
        )
    }

    /**
     * Returns [MessageRouterConfiguration] that can be used to send/receive message to component under the test
     */
    fun getTestConfig(spec: RabbitMqSpec): MessageRouterConfiguration {
        val queuesForPublishers = spec.pinsSpec.publishers.pins.mapValues { (name, spec) ->
            pinSpecToConfiguration(
                name,
                spec,
                isPublisher = false,
                ignoreFilters = true,
                attributes = spec.attributeSet.replaceValues(
                    QueueAttribute.PUBLISH.value to QueueAttribute.SUBSCRIBE.value,
                ) + listOf(name),
            )
        }

        val routingForSubscribers = spec.pinsSpec.subscribers.pins.mapValues { (name, spec) ->
            pinSpecToConfiguration(
                name,
                spec,
                isPublisher = true,
                ignoreFilters = true,
                attributes = spec.attributeSet.replaceValues(
                    QueueAttribute.SUBSCRIBE.value to QueueAttribute.PUBLISH.value,
                ) + listOf(name),
            )
        }
        val queues: MutableMap<String, QueueConfiguration> = (queuesForPublishers + routingForSubscribers)
            .toMutableMap()
        addEventRoutingForTestFactory(queues)
        return MessageRouterConfiguration(
            queues = queues,
        )
    }

    private fun addEventRoutingForTestFactory(queues: MutableMap<String, QueueConfiguration>) {
        val key = "events-${System.currentTimeMillis()}"
        queues[key] = QueueConfiguration(
            routingKey = key,
            queue = "",
            exchange = RabbitMqIntegration.DEFAULT_EXCHANGE,
            attributes = listOf(QueueAttribute.PUBLISH.value, QueueAttribute.EVENT.value),
            isWritable = true,
            isReadable = false,
        )
    }

    private fun Set<String>.replaceValues(
        vararg pairs: Pair<String, String>,
    ): List<String> {
        return toMutableSet().apply {
            for ((current, new) in pairs) {
                remove(current)
                add(new)
            }
        }.toList()
    }

    private fun pinSpecToConfiguration(
        name: String,
        spec: PinSpec,
        isPublisher: Boolean,
        ignoreFilters: Boolean = false,
        attributes: List<String> = spec.attributeSet.toList(),
    ) = QueueConfiguration(
        routingKey = if (isPublisher) name else "",
        queue = if (isPublisher) "" else name,
        exchange = RabbitMqIntegration.DEFAULT_EXCHANGE,
        attributes = attributes,
        isReadable = !isPublisher,
        isWritable = isPublisher,
        filters = spec.filters.takeUnless { ignoreFilters }?.map {
            MqRouterFilterConfiguration(
                it.message.filters,
                it.metadata.filters,
            )
        } ?: emptyList(),
    )
}
