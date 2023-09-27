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

import com.exactpro.cradle.cassandra.CassandraStorageSettings
import com.exactpro.th2.common.schema.cradle.CradleConfidentialConfiguration
import com.exactpro.th2.test.spec.CradleSpec
import com.fasterxml.jackson.annotation.JsonUnwrapped

internal object CradleConfigProvider {
    fun getCradleConnectionConfig(cradle: CradleIntegration, spec: CradleSpec): CradleConfidentialConfiguration {
        val contactPoint = cradle.container.contactPoint
        return CradleConfidentialConfiguration(
            host = contactPoint.hostName,
            port = contactPoint.port,
            dataCenter = cradle.container.localDatacenter,
            keyspace = spec.settings.keyspace,
            username = cradle.container.username,
            password = cradle.container.password,
        )
    }

    fun getCradleParametersConfig(spec: CradleSpec, prepareStorage: Boolean = false): CombinedCassandraStorageSettings {
        // this is done to group both configuration in one object
        // change it when the common factory will distinguish between them
        return CombinedCassandraStorageSettings(prepareStorage, spec.settings)
    }
}

internal class CombinedCassandraStorageSettings(
    @Suppress("unused")
    val prepareStorage: Boolean = false,
    settings: CassandraStorageSettings,
) {
    @Suppress("unused")
    @field:JsonUnwrapped
    val settings: CassandraStorageSettings = settings
}
