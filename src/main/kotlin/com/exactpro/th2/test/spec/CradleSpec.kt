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

import com.exactpro.cradle.cassandra.CassandraStorageSettings
import java.time.Duration

public class CradleSpec private constructor(
    keyspace: String,
) {
    internal val settings = CassandraStorageSettings()
    internal var autoPages: Boolean = true
        private set
    internal var autoPageInterval: Duration = Duration.ofMinutes(10)
        private set
    internal var pageDuration: Duration = Duration.ofMinutes(1)
        private set
    internal var reuseKeyspace: Boolean = false
        private set

    init {
        require(keyspace.isNotBlank()) { "blank keyspace" }
        settings.keyspace = keyspace
        settings.bookRefreshIntervalMillis = 100 // for test purposes
        settings.resultPageSize = 2
    }

    public fun withPageSize(size: Int): CradleSpec =
        apply {
            require(size > 1) { "page size must be greater than 1" }
            settings.resultPageSize = size
        }

    public fun withRefreshBookInterval(intervalMillis: Long): CradleSpec =
        apply {
            settings.bookRefreshIntervalMillis = intervalMillis
        }

    public fun withQueryTimeout(timeout: Long): CradleSpec =
        apply {
            require(timeout > 0) { "timeout must be positive" }
            settings.timeout = timeout
        }

    public fun disableAutoPages(): CradleSpec =
        apply {
            autoPages = false
        }

    public fun withAutoPageInterval(interval: Duration): CradleSpec =
        apply {
            require(interval > Duration.ZERO) { "interval must be positive" }
            require(interval.toMillis() > settings.calculatePageActionRejectionThreshold()) {
                "interval must be longer than bookRefreshIntervalMillis ${settings.bookRefreshIntervalMillis}"
            }
            checkPageDurationAndInterval()
            autoPageInterval = interval
        }

    public fun withPageDuration(duration: Duration): CradleSpec =
        apply {
            require(duration > Duration.ZERO) { "duration must be positive" }
            require(duration.toMillis() > settings.calculatePageActionRejectionThreshold())
            checkPageDurationAndInterval()
            pageDuration = duration
        }

    public fun reuseKeyspace(): CradleSpec =
        apply {
            reuseKeyspace = true
        }

    private fun checkPageDurationAndInterval() {
        require(pageDuration <= autoPageInterval) {
            "pageDuration ($pageDuration) must be less or equal to autoPageInterval ($autoPageInterval)"
        }
    }

    public companion object {
        @JvmStatic
        @JvmOverloads
        public fun create(keyspace: String = "test_keyspace"): CradleSpec = CradleSpec(keyspace)
    }
}
