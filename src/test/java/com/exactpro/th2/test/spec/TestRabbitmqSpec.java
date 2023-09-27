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

package com.exactpro.th2.test.spec;

import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.schema.box.configuration.BoxConfiguration;
import com.exactpro.th2.common.schema.factory.CommonFactory;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Message;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageId;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage;
import com.exactpro.th2.test.annotations.Th2AppFactory;
import com.exactpro.th2.test.annotations.Th2IntegrationTest;
import com.exactpro.th2.test.annotations.Th2TestFactory;
import com.exactpro.th2.test.queue.CollectorMessageListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Th2IntegrationTest
class TestRabbitmqSpec {
    final RabbitMqSpec spec = RabbitMqSpec.create().configurePins(pinsSpec -> {
        pinsSpec.configureSubscribers(subscribers -> {

        });
        pinsSpec.configurePublishers(publishers -> {
            publishers.configurePin("pin_name", pinSpec -> {
                pinSpec.attributes("a", "b", "transport-group")
                        .addFilter(filterContainer -> {
                            filterContainer.configureMessageFilter(filterSpec -> {
                                filterSpec
                                        .field("session_alias").shouldMatchWildcard("test_*")
                                        .field("message_type").shouldBeEqualTo("test");
                            });
                        });
            });
        });
    });

    @Test
    void connectsEventPinBetweenAppAndTestFactories(
            @Th2AppFactory CommonFactory appFactory,
            @Th2TestFactory CommonFactory testFactory
    ) {
        EventID rootEventId = appFactory.getRootEventId();
        var events = CollectorMessageListener.<EventBatch>createUnbound();
        testFactory.getEventBatchRouter().subscribe(events, "events");
        EventBatch batch = events.poll(Duration.ofSeconds(1));
        Assertions.assertNotNull(batch, "no batch received");
        Assertions.assertEquals(1, batch.getEventsCount(),
                () -> "unexpected number of events: " + MessageUtils.toJson(batch));
        Assertions.assertEquals(
                rootEventId,
                batch.getEvents(0).getId(),
                "unexpected root event ID sent"
        );
    }

    @Test
    void connectsConfiguredPin(
            @Th2AppFactory CommonFactory appFactory,
            @Th2TestFactory CommonFactory testFactory
    ) throws IOException {
        MessageRouter<GroupBatch> appRouter = appFactory.getTransportGroupBatchRouter();
        MessageId messageId = MessageId.builder()
                .setSessionAlias("test_1")
                .setSequence(1)
                .setTimestamp(Instant.now())
                .setDirection(Direction.OUTGOING)
                .build();
        appRouter.send(GroupBatch.builder()
                .setBook(BoxConfiguration.DEFAULT_BOOK_NAME)
                .setSessionGroup("session_group")
                .setGroups(
                        List.of(
                                MessageGroup.builder()
                                        .addMessage(ParsedMessage.builder()
                                                .setType("test")
                                                .setId(messageId)
                                                .setBody(Map.of("test", 42))
                                                .build())
                                        .build()
                        )
                )
                .build(), "a");

        CollectorMessageListener<GroupBatch> listener = CollectorMessageListener.createUnbound();
        testFactory.getTransportGroupBatchRouter().subscribe(listener, "pin_name");

        GroupBatch batch = listener.poll(Duration.ofSeconds(1));
        Assertions.assertNotNull(batch, "batch was not received");
        Assertions.assertEquals(
                BoxConfiguration.DEFAULT_BOOK_NAME,
                batch.getBook(),
                "unexpected book"
        );
        Assertions.assertEquals(
                "session_group",
                batch.getSessionGroup(),
                "unexpected session group"
        );
        Assertions.assertEquals(
                1,
                batch.getGroups().size(),
                () -> "unexpected groups number: " + batch.getGroups()
        );
        MessageGroup group = batch.getGroups().get(0);
        Assertions.assertEquals(
                1,
                group.getMessages().size(),
                () -> "unexpected messages count: " + group.getMessages()
        );
        Message<?> message = group.getMessages().get(0);
        Assertions.assertEquals(
                messageId,
                message.getId(),
                "unexpected message ID"
        );
        Assertions.assertInstanceOf(ParsedMessage.class, message, "unexpected message type");
    }
}
