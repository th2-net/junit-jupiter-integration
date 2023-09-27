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

package com.exactpro.th2.test.spec

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.common.schema.box.configuration.BoxConfiguration
import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageId
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.test.annotations.Th2AppFactory
import com.exactpro.th2.test.annotations.Th2IntegrationTest
import com.exactpro.th2.test.annotations.Th2TestFactory
import com.exactpro.th2.test.queue.CollectorMessageListener.Companion.createUnbound
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

@Th2IntegrationTest
internal class TestRabbitMqSpec {
    @JvmField
    internal val spec = RabbitMqSpec.create()
        .pins {
            publishers {
                pin("pin_name") {
                    attributes("a", "b", "transport-group")
                    filter {
                        message {
                            sessionAlias() shouldMatchWildcard "test_*"
                            messageType() shouldBeEqualTo "test"
                        }
                    }
                }
            }
        }

    @Test
    fun connectsEventPinBetweenAppAndTestFactories(
        @Th2AppFactory appFactory: CommonFactory,
        @Th2TestFactory testFactory: CommonFactory,
    ) {
        val rootEventId = appFactory.rootEventId
        val events = createUnbound<EventBatch>()
        testFactory.eventBatchRouter.subscribe(events, "events")
        val batch = events.poll(Duration.ofSeconds(1))
        Assertions.assertNotNull(batch, "no batch received")
        Assertions.assertEquals(
            1,
            batch!!.eventsCount,
        ) { "unexpected number of events: " + batch.toJson() }
        Assertions.assertEquals(
            rootEventId,
            batch.getEvents(0).id,
            "unexpected root event ID sent",
        )
    }

    @Test
    @Throws(IOException::class)
    fun connectsConfiguredPin(
        @Th2AppFactory appFactory: CommonFactory,
        @Th2TestFactory testFactory: CommonFactory,
    ) {
        val appRouter = appFactory.transportGroupBatchRouter
        val messageId: MessageId = MessageId.builder()
            .setSessionAlias("test_1")
            .setSequence(1)
            .setTimestamp(Instant.now())
            .setDirection(Direction.OUTGOING)
            .build()
        appRouter.send(
            GroupBatch.builder()
                .setBook(BoxConfiguration.DEFAULT_BOOK_NAME)
                .setSessionGroup("session_group")
                .setGroups(
                    listOf(
                        MessageGroup.builder()
                            .addMessage(
                                ParsedMessage.builder()
                                    .setType("test")
                                    .setId(messageId)
                                    .setBody(mapOf("test" to 42))
                                    .build(),
                            )
                            .build(),
                    ),
                )
                .build(),
            "a",
        )
        val listener = createUnbound<GroupBatch>()
        testFactory.transportGroupBatchRouter.subscribe(listener, "pin_name")
        val batch = listener.poll(Duration.ofSeconds(1))
        Assertions.assertNotNull(batch, "batch was not received")
        Assertions.assertEquals(
            BoxConfiguration.DEFAULT_BOOK_NAME,
            batch!!.book,
            "unexpected book",
        )
        Assertions.assertEquals(
            "session_group",
            batch.sessionGroup,
            "unexpected session group",
        )
        Assertions.assertEquals(
            1,
            batch.groups.size,
        ) { "unexpected groups number: " + batch.groups }
        val (messages) = batch.groups[0]
        Assertions.assertEquals(
            1,
            messages.size,
        ) { "unexpected messages count: $messages" }
        val message = messages[0]
        Assertions.assertEquals(
            messageId,
            message.id,
            "unexpected message ID",
        )
        Assertions.assertInstanceOf(ParsedMessage::class.java, message, "unexpected message type")
    }
}
