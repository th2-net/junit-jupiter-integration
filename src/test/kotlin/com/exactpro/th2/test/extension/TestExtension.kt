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

package com.exactpro.th2.test.extension

import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.common.schema.factory.extensions.getCustomConfiguration
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageId
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.test.annotations.CustomConfigProvider
import com.exactpro.th2.test.annotations.Th2AppFactory
import com.exactpro.th2.test.annotations.Th2IntegrationTest
import com.exactpro.th2.test.annotations.Th2TestFactory
import com.exactpro.th2.test.queue.CollectorMessageListener
import com.exactpro.th2.test.spec.CradleSpec
import com.exactpro.th2.test.spec.CustomConfigSpec
import com.exactpro.th2.test.spec.RabbitMqSpec
import com.exactpro.th2.test.spec.pin
import com.exactpro.th2.test.spec.pins
import com.exactpro.th2.test.spec.subscribers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

@Th2IntegrationTest
internal class TestExtension {
    @Suppress("unused")
    @JvmField
    internal val spec: RabbitMqSpec = RabbitMqSpec.create()
        .pins {
            subscribers {
                pin("test") {
                    attributes("transport-group")
                }
            }
        }

    @JvmField
    internal val cradle: CradleSpec = CradleSpec.create("test")
        .reuseKeyspace()
        .withPageDuration(Duration.ofMinutes(1))
        .withAutoPageInterval(Duration.ofMinutes(15))
        .withRefreshBookInterval(100)

    @JvmField
    internal val customConfigSpec: CustomConfigSpec = CustomConfigSpec.fromString(
        """
        {
            "test": 42
        }
        """.trimIndent(),
    )

    @Test
    fun testFactoryCanSendMessagesToApp(
        @Th2AppFactory factory: CommonFactory,
        @Th2TestFactory testFactory: CommonFactory,
        registry: CleanupExtension.Registry,
    ) {
        val listener = CollectorMessageListener.createUnbound<GroupBatch>()
        val monitor = factory.transportGroupBatchRouter.subscribe(listener)
        registry.add("monitor") {
            monitor.unsubscribe()
        }

        val groupBatch = GroupBatch.builder()
            .setBook("book")
            .setSessionGroup("group")
            .addGroup(
                MessageGroup.builder()
                    .addMessage(
                        ParsedMessage.builder()
                            .setId(MessageId.DEFAULT)
                            .setType("test")
                            .apply {
                                bodyBuilder()
                                    .put("test", "A")
                            }
                            .build(),
                    )
                    .build(),
            )
            .build()
        testFactory.transportGroupBatchRouter.send(groupBatch)

        val batch = listener.poll(Duration.ofSeconds(5))
        Assertions.assertNotNull(batch, "batch was not received")
        Assertions.assertEquals(groupBatch, batch, "unexpected batch received")
    }

    @Test
    fun canAccessCustomConfig(
        @Th2AppFactory factory: CommonFactory,
    ) {
        val customConfig = factory.getCustomConfiguration<CustomConfig>()
        Assertions.assertEquals(
            CustomConfig(
                test = 42,
            ),
            customConfig,
            "unexpected custom config",
        )
    }

    @Test
    @CustomConfigProvider("newCustomConfig")
    fun customConfigCanBeOverridedForTest(
        @Th2AppFactory factory: CommonFactory,
    ) {
        val customConfig = factory.getCustomConfiguration<CustomConfig>()
        Assertions.assertEquals(
            CustomConfig(
                test = 53,
            ),
            customConfig,
            "unexpected custom config",
        )
    }

    @Suppress("unused") // used by CustomConfigProvider
    fun newCustomConfig(): CustomConfigSpec = CustomConfigSpec.fromObject(CustomConfig(53))

    private data class CustomConfig(
        val test: Int,
    )
}
