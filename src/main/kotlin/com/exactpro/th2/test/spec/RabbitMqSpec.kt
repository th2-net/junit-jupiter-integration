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

package com.exactpro.th2.test.spec

import com.exactpro.th2.common.schema.filter.strategy.impl.AbstractTh2MsgFilterStrategy
import com.exactpro.th2.common.schema.message.QueueAttribute
import com.exactpro.th2.common.schema.message.configuration.FieldFilterConfiguration
import com.exactpro.th2.common.schema.message.configuration.FieldFilterOperation
import java.util.function.Consumer

@DslMarker
private annotation class RabbitMqSpecDsl

@RabbitMqSpecDsl
public class RabbitMqSpec private constructor() {
    internal val pinsSpec: PinsSpec = PinsSpec()

    public fun configurePins(block: Consumer<PinsSpec>): RabbitMqSpec =
        apply {
            block.accept(pinsSpec)
        }

    public companion object {
        public const val EVENTS_PIN_NAME: String = "events"

        @JvmStatic
        public fun create(): RabbitMqSpec =
            RabbitMqSpec()
                .pins {
                    publishers {
                        pin(EVENTS_PIN_NAME) {
                            attributes("event")
                        }
                    }
                }
    }
}

public fun RabbitMqSpec.pins(block: PinsSpec.() -> Unit): RabbitMqSpec = configurePins(Consumer(block))

@RabbitMqSpecDsl
public class PinsSpec internal constructor() {
    internal val subscribers: PinCollectionSpec = PinCollectionSpec(setOf(QueueAttribute.SUBSCRIBE.value))
    internal val publishers: PinCollectionSpec = PinCollectionSpec(setOf(QueueAttribute.PUBLISH.value))

    public fun configureSubscribers(block: Consumer<PinCollectionSpec>) {
        block.accept(subscribers)
    }

    public fun configurePublishers(block: Consumer<PinCollectionSpec>) {
        block.accept(publishers)
    }
}

public fun PinsSpec.subscribers(block: PinCollectionSpec.() -> Unit) {
    configureSubscribers(Consumer(block))
}

public fun PinsSpec.publishers(block: PinCollectionSpec.() -> Unit) {
    configurePublishers(Consumer(block))
}

@RabbitMqSpecDsl
public class PinCollectionSpec internal constructor(
    private val defaultAttributes: Set<String> = emptySet(),
) {
    internal val pins: MutableMap<String, PinSpec> = hashMapOf()

    public fun configurePin(
        name: String,
        block: Consumer<PinSpec>,
    ): PinCollectionSpec =
        apply {
            val pinSpec =
                PinSpec().also(block::accept).apply {
                    attributeSet.addAll(this@PinCollectionSpec.defaultAttributes)
                }
            check(pins.put(name, pinSpec) == null) {
                "duplicated pin $name"
            }
        }
}

public fun PinCollectionSpec.pin(
    name: String,
    block: PinSpec.() -> Unit,
): PinCollectionSpec = configurePin(name, Consumer(block))

@RabbitMqSpecDsl
public class PinSpec internal constructor() {
    internal val attributeSet: MutableSet<String> = hashSetOf()
    internal val filters: MutableList<FilterContainerSpec> = arrayListOf()

    public fun attributes(vararg attributes: String): PinSpec =
        apply {
            attributeSet.addAll(attributes)
        }

    public fun addFilter(block: Consumer<FilterContainerSpec>): PinSpec =
        apply {
            filters.add(FilterContainerSpec().also(block::accept))
        }
}

public fun PinSpec.filter(block: FilterContainerSpec.() -> Unit): PinSpec = addFilter(Consumer(block))

@RabbitMqSpecDsl
public class FilterContainerSpec internal constructor() {
    internal val metadata: FilterSpec = FilterSpec()
    internal val message: FilterSpec = FilterSpec()

    public fun configureMetadataFilter(block: Consumer<FilterSpec>) {
        block.accept(metadata)
    }

    public fun configureMessageFilter(block: Consumer<FilterSpec>) {
        block.accept(message)
    }
}

public fun FilterContainerSpec.metadata(block: FilterSpec.() -> Unit): Unit = configureMetadataFilter(Consumer(block))

public fun FilterContainerSpec.message(block: FilterSpec.() -> Unit): Unit = configureMessageFilter(Consumer(block))

@RabbitMqSpecDsl
public class FilterSpec internal constructor() {
    internal val filters: MutableList<FieldFilterConfiguration> = arrayListOf()

    public fun field(fieldName: String): Expectation = Expectation(fieldName, filters, this)

    public fun messageType(): Expectation = field(AbstractTh2MsgFilterStrategy.MESSAGE_TYPE_KEY)

    public fun book(): Expectation = field(AbstractTh2MsgFilterStrategy.BOOK_KEY)

    public fun sessionAlias(): Expectation = field(AbstractTh2MsgFilterStrategy.SESSION_ALIAS_KEY)

    public fun sessionGroup(): Expectation = field(AbstractTh2MsgFilterStrategy.SESSION_GROUP_KEY)

    public fun direction(): Expectation = field(AbstractTh2MsgFilterStrategy.DIRECTION_KEY)

    public fun protocol(): Expectation = field(AbstractTh2MsgFilterStrategy.PROTOCOL_KEY)

    public class Expectation internal constructor(
        private val fieldName: String,
        private val filters: MutableList<FieldFilterConfiguration>,
        private val root: FilterSpec,
    ) {
        public fun shouldBeEmpty(): FilterSpec = addFilter(FieldFilterOperation.EMPTY)

        public fun shouldNotBeEmpty(): FilterSpec = addFilter(FieldFilterOperation.NOT_EMPTY)

        public infix fun shouldBeEqualTo(expectedValue: String): FilterSpec = addFilter(FieldFilterOperation.EQUAL, expectedValue)

        public infix fun shouldNotBeEqualTo(expectedValue: String): FilterSpec = addFilter(FieldFilterOperation.NOT_EQUAL, expectedValue)

        public infix fun shouldMatchWildcard(wildcard: String): FilterSpec = addFilter(FieldFilterOperation.WILDCARD, wildcard)

        private fun addFilter(
            operation: FieldFilterOperation,
            expectedValue: String? = null,
        ): FilterSpec {
            filters +=
                FieldFilterConfiguration(
                    fieldName = fieldName,
                    expectedValue = expectedValue,
                    operation = operation,
                )
            return root
        }
    }
}
