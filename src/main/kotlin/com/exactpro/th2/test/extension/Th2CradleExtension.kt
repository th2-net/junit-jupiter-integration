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

import com.exactpro.cradle.BookId
import com.exactpro.cradle.BookInfo
import com.exactpro.cradle.BookToAdd
import com.exactpro.cradle.CradleManager
import com.exactpro.cradle.CradleStorage
import com.exactpro.cradle.cassandra.CassandraCradleManager
import com.exactpro.cradle.cassandra.CassandraStorageSettings
import com.exactpro.cradle.cassandra.connection.CassandraConnectionSettings
import com.exactpro.th2.common.schema.box.configuration.BoxConfiguration
import com.exactpro.th2.test.integration.ConfigurationWriter
import com.exactpro.th2.test.integration.CradleConfigProvider
import com.exactpro.th2.test.integration.CradleConfigurator
import com.exactpro.th2.test.integration.CradleIntegration
import com.exactpro.th2.test.spec.CradleSpec
import mu.KotlinLogging
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.outputStream
import kotlin.math.max
import kotlin.reflect.cast

public class Th2CradleExtension :
    TestInstancePostProcessor,
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback,
    ParameterResolver {
    private var cradleSpec: CradleSpec? = null
    private lateinit var cradle: CradleIntegration

    override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
        val fields = testInstance::class.findFields<CradleSpec>().ifEmpty { return }
        cradleSpec = testInstance.getSingle(fields)
    }

    override fun beforeAll(context: ExtensionContext) {
        cradleSpec?.also { spec ->
            startCradle(spec, context.requiredTestInstance)
            val cradleConnection = CradleConfigProvider.getCradleConnectionConfig(cradle, spec)
            val appFolder: Path = context.getStore(Th2.NAMESPACE).getRequired(Th2ConfigExtension.APP_CONFIG)
            with(appFolder) {
                resolve(ConfigurationWriter.CRADLE_CONNECTION_CONFIG).outputStream().use {
                    ConfigurationWriter.write(cradleConnection, it)
                }
                resolve(ConfigurationWriter.CRADLE_PARAMETERS_CONFIG).outputStream().use {
                    ConfigurationWriter.write(
                        CradleConfigProvider.getCradleParametersConfig(spec, prepareStorage = true),
                        it,
                    )
                }
            }

            val testFolder: Path = context.getStore(Th2.NAMESPACE).getRequired(Th2ConfigExtension.TEST_CONFIG)
            with(testFolder) {
                resolve(ConfigurationWriter.CRADLE_CONNECTION_CONFIG).outputStream().use {
                    ConfigurationWriter.write(cradleConnection, it)
                }
                resolve(ConfigurationWriter.CRADLE_PARAMETERS_CONFIG).outputStream().use {
                    ConfigurationWriter.write(
                        CradleConfigProvider.getCradleParametersConfig(spec, prepareStorage = false),
                        it,
                    )
                }
            }
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        cradleSpec?.also {
            if (!it.reuseKeyspace) {
                // Re-create keyspace only if we don't reuse it
                setupCradle(it)
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        if (::cradle.isInitialized) {
            runCatching {
                LOGGER.info { "Stopping cradle" }
                cradle.stop()
            }.onFailure {
                LOGGER.warn(it) { "cannot stop cradle" }
            }
        }
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type == CradleManager::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        val spec = checkNotNull(cradleSpec) { "cannot create Cradle manager without spec specified" }
        val resource = extensionContext.getStore(Th2.NAMESPACE).getOrComputeIfAbsent(
            Th2CradleExtension::class,
        ) {
            ClosableCradleResource(createManager(spec))
        }.let(ClosableCradleResource::class::cast)
        return resource.manager
    }

    private fun createPagesForDefaultBook(spec: CradleSpec) {
        createManager(spec).use {
            if (it.storage.getAllPages(BookId(BoxConfiguration.DEFAULT_BOOK_NAME)).isNotEmpty() && spec.reuseKeyspace) {
                LOGGER.info { "Skip creating pages because book already exists" }
                return
            }
            it.storage.createPages(
                BoxConfiguration.DEFAULT_BOOK_NAME,
                spec,
                spec.settings.calculatePageActionRejectionThreshold(),
            )
        }
    }

    private fun startCradle(spec: CradleSpec, testInstance: Any) {
        cradle = testInstance.getFieldOrDefault { CradleIntegration.defaultImage() }
        cradle.start()
        if (spec.reuseKeyspace) {
            // create keyspace on start if we are going to reuse it
            setupCradle(spec)
        }
    }

    private fun setupCradle(spec: CradleSpec) {
        CradleConfigurator.configureCradle(cradle, spec)
        if (spec.autoPages) {
            createPagesForDefaultBook(spec)
        }
    }

    private fun createManager(
        spec: CradleSpec,
    ): CassandraCradleManager {
        val contactPoint = cradle.container.contactPoint
        return CassandraCradleManager(
            CassandraConnectionSettings(
                contactPoint.hostName,
                contactPoint.port,
                cradle.container.localDatacenter,
            ),
            CassandraStorageSettings(spec.settings),
            true,
        )
    }

    private class ClosableCradleResource(
        val manager: CradleManager,
    ) : CloseableResource {
        override fun close() {
            LOGGER.info { "Closing cradle manager resolved as a parameter" }
            manager.close()
        }
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}

private fun CradleStorage.createPages(bookName: String, spec: CradleSpec, minDistanceBetweenPages: Long) {
    val bookStart = Instant.now()
    val bookInfo: BookInfo = addBook(
        BookToAdd(
            bookName,
            bookStart,
        ),
    )
    var pageStart: Instant = bookStart
    val pageEnd: Instant = pageStart + spec.autoPageInterval
    var index = 1
    while (pageStart < pageEnd) {
        addPage(bookInfo.id, "page-$index", pageStart, "auto page $index")
        pageStart = pageStart.plus(max(minDistanceBetweenPages, spec.pageDuration.toMillis()), ChronoUnit.MILLIS)
        index += 1
    }
}
