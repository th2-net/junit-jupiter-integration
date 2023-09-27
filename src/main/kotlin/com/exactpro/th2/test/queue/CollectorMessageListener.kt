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

package com.exactpro.th2.test.queue

import com.exactpro.th2.common.schema.message.DeliveryMetadata
import com.exactpro.th2.common.schema.message.MessageListener
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

public class CollectorMessageListener<T> private constructor(
    private val queue: BlockingQueue<T>,
) : MessageListener<T>, Iterable<T> by queue {

    public fun isEmpty(): Boolean = queue.isEmpty()

    public fun poll(duration: Duration): T? = queue.poll(duration.toMillis(), TimeUnit.MILLISECONDS)

    override fun handle(deliveryMetadata: DeliveryMetadata, message: T) {
        queue.put(message)
    }

    public companion object {
        @JvmStatic
        public fun <T> createWithCapacity(capacity: Int): CollectorMessageListener<T> =
            CollectorMessageListener(ArrayBlockingQueue(capacity))

        @JvmStatic
        public fun <T> createUnbound(): CollectorMessageListener<T> =
            CollectorMessageListener(LinkedBlockingQueue())
    }
}

public fun CollectorMessageListener<*>.isNotEmpty(): Boolean = !isEmpty()
