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

package com.exactpro.th2.test.integration

import com.datastax.oss.driver.api.core.CqlSession
import org.slf4j.LoggerFactory
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName
import java.util.function.Consumer

public class CradleIntegration private constructor(
    cassandraImageName: DockerImageName,
) : Startable {
    internal val container = CassandraContainer(cassandraImageName).apply {
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger(CradleIntegration::class.java)).withSeparateOutputStreams())
    }

    override fun start() {
        container.start()
    }

    override fun stop() {
        container.stop()
    }

    public fun configureContainer(block: Consumer<CassandraContainer>): CradleIntegration = apply {
        block.accept(container)
    }

    internal fun recreateKeyspace(keyspace: String) {
        CqlSession.builder()
            .addContactPoint(container.contactPoint)
            .withLocalDatacenter(container.localDatacenter)
            .build().use {
                it.execute("DROP KEYSPACE IF EXISTS $keyspace")
                it.execute("CREATE KEYSPACE $keyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};")
            }
    }

    public companion object {
        @JvmField
        public val DEFAULT_IMAGE: DockerImageName = DockerImageName.parse("cassandra:4.0.5")

        @JvmStatic
        public fun defaultImage(): CradleIntegration = CradleIntegration(DEFAULT_IMAGE)

        @JvmStatic
        public fun fromImage(imageName: DockerImageName): CradleIntegration =
            CradleIntegration(imageName)
    }
}

public fun CradleIntegration.container(block: CassandraContainer.() -> Unit): CradleIntegration =
    configureContainer(Consumer(block))
