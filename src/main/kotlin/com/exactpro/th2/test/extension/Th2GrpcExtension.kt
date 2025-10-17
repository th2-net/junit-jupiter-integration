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

import com.exactpro.th2.common.schema.grpc.configuration.GrpcConfiguration
import com.exactpro.th2.common.schema.grpc.configuration.GrpcEndpointConfiguration
import com.exactpro.th2.common.schema.grpc.configuration.GrpcRawRobinStrategy
import com.exactpro.th2.common.schema.grpc.configuration.GrpcRouterConfiguration
import com.exactpro.th2.common.schema.grpc.configuration.GrpcServerConfiguration
import com.exactpro.th2.common.schema.grpc.configuration.GrpcServiceConfiguration
import com.exactpro.th2.common.schema.strategy.route.impl.RobinRoutingStrategy
import com.exactpro.th2.test.integration.ConfigurationWriter
import com.exactpro.th2.test.spec.GrpcSpec
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import java.io.IOException
import java.net.ServerSocket
import kotlin.io.path.outputStream

public class Th2GrpcExtension :
    TestInstancePostProcessor,
    BeforeAllCallback {
    private var spec: GrpcSpec? = null

    override fun postProcessTestInstance(
        testInstance: Any,
        context: ExtensionContext,
    ) {
        val fields = testInstance::class.findFields<GrpcSpec>().ifEmpty { return }
        spec = testInstance.getSingle(fields)
    }

    override fun beforeAll(context: ExtensionContext) {
        val grpcSpec = spec ?: return
        val appPort: Int = findFreePort()
        val testPort: Int = findFreePort(start = appPort + 1)
        val routerConfig: GrpcRouterConfiguration = grpcSpec.routerSpec.routerConfig
        with(Th2.getAppConfigFolder(context)) {
            resolve(ConfigurationWriter.GRPC_CONFIG).outputStream().use { out ->
                ConfigurationWriter.write(
                    createAppGrpcConfig(appPort, grpcSpec, testPort),
                    out,
                )
            }
            resolve(ConfigurationWriter.GRPC_ROUTER_CONFIG).outputStream().use {
                ConfigurationWriter.write(routerConfig, it)
            }
        }
        with(Th2.getTestConfigFolder(context)) {
            resolve(ConfigurationWriter.GRPC_CONFIG).outputStream().use { out ->
                ConfigurationWriter.write(
                    createTestGrpcConfig(appPort, grpcSpec, testPort),
                    out,
                )
            }
            resolve(ConfigurationWriter.GRPC_ROUTER_CONFIG).outputStream().use {
                ConfigurationWriter.write(routerConfig, it)
            }
        }
    }

    private fun createAppGrpcConfig(
        appPort: Int,
        grpcSpec: GrpcSpec,
        testPort: Int,
    ) = GrpcConfiguration(
        serverConfiguration = GrpcServerConfiguration(port = appPort),
        services =
        grpcSpec.clients.associate {
            "${it.type.simpleName}-${RandomStringUtils.insecure().nextAlphabetic(5)}" to
                GrpcServiceConfiguration(
                    strategy =
                    RobinRoutingStrategy().apply {
                        init(GrpcRawRobinStrategy(endpoints = listOf("test-endpoint")))
                    },
                    serviceClass = it.type,
                    endpoints =
                    mapOf(
                        "test-endpoint" to
                            GrpcEndpointConfiguration(
                                host = "localhost",
                                port = testPort,
                                attributes = it.attributes.toList(),
                            ),
                    ),
                )
        },
    )

    private fun createTestGrpcConfig(
        appPort: Int,
        grpcSpec: GrpcSpec,
        testPort: Int,
    ) = GrpcConfiguration(
        serverConfiguration = GrpcServerConfiguration(port = testPort),
        services =
        grpcSpec.servers.associate {
            "${it.simpleName}-${RandomStringUtils.insecure().nextAlphabetic(5)}" to
                GrpcServiceConfiguration(
                    strategy =
                    RobinRoutingStrategy().apply {
                        init(GrpcRawRobinStrategy(endpoints = listOf("test-endpoint")))
                    },
                    serviceClass = it,
                    endpoints =
                    mapOf(
                        "test-endpoint" to
                            GrpcEndpointConfiguration(
                                host = "localhost",
                                port = appPort,
                            ),
                    ),
                )
        },
    )

    private fun findFreePort(
        start: Int = 1025,
        end: Int = 30000,
    ): Int {
        for (port in start..end) {
            try {
                ServerSocket(port).use {
                    return port
                }
            } catch (ex: IOException) {
                val msg = ex.message ?: throw ex
                if (!msg.contains("Address already in use")) {
                    throw ex
                }
            }
        }
        error("cannot find free port between $start and $end")
    }
}
