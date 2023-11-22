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

import com.exactpro.th2.common.schema.grpc.configuration.GrpcRouterConfiguration
import java.time.Duration
import java.util.function.Consumer

public class GrpcSpec private constructor() {
    internal val clients: MutableSet<Client> = hashSetOf()
    internal val servers: MutableSet<Class<*>> = hashSetOf()
    internal val routerSpec = RouterConfigurationSpec()

    public fun registerClient(clientClass: Class<*>, vararg attributes: String): GrpcSpec = apply {
        clients += Client(clientClass, attributes.toSet())
    }

    public fun registerServer(serverClass: Class<*>): GrpcSpec = apply {
        servers += serverClass
    }

    public fun configureRouter(block: Consumer<RouterConfigurationSpec>): GrpcSpec = apply {
        block.accept(routerSpec)
    }

    internal class Client(
        val type: Class<*>,
        val attributes: Set<String>,
    )

    public companion object {
        @JvmStatic
        public fun create(): GrpcSpec = GrpcSpec()
    }
}

public class RouterConfigurationSpec internal constructor() {
    internal val routerConfig = GrpcRouterConfiguration()

    public fun enableSizeMeasuring(): RouterConfigurationSpec = apply {
        routerConfig.enableSizeMeasuring = true
    }

    public fun withMaxRetries(count: Int): RouterConfigurationSpec = apply {
        routerConfig.retryConfiguration = routerConfig.retryConfiguration.copy(
            maxAttempts = count,
        )
    }

    public fun withMinDelay(delayMillis: Long): RouterConfigurationSpec = apply {
        routerConfig.retryConfiguration = routerConfig.retryConfiguration.copy(
            minMethodRetriesTimeout = delayMillis,
        )
    }

    public fun withMinDelay(delay: Duration): RouterConfigurationSpec = apply {
        routerConfig.retryConfiguration = routerConfig.retryConfiguration.copy(
            minMethodRetriesTimeout = delay.toMillis(),
        )
    }

    public fun withMaxDelay(delayMillis: Long): RouterConfigurationSpec = apply {
        routerConfig.retryConfiguration = routerConfig.retryConfiguration.copy(
            maxMethodRetriesTimeout = delayMillis,
        )
    }

    public fun withMaxDelay(delay: Duration): RouterConfigurationSpec = apply {
        routerConfig.retryConfiguration = routerConfig.retryConfiguration.copy(
            maxMethodRetriesTimeout = delay.toMillis(),
        )
    }
}

public inline fun <reified T> GrpcSpec.client(vararg attributes: String): GrpcSpec =
    registerClient(T::class.java, *attributes)

public inline fun <reified T> GrpcSpec.server(): GrpcSpec = registerServer(T::class.java)

public fun GrpcSpec.routerConfig(block: RouterConfigurationSpec.() -> Unit): GrpcSpec =
    configureRouter(Consumer(block))
