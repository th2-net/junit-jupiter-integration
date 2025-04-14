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

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteRecursively

public class Th2ConfigExtension : BeforeAllCallback, AfterAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        val store = context.getStore(Th2.NAMESPACE)
        val root = Files.createTempDirectory("th2")
        LOGGER.info { "Config dirs $root" }
        store.put(ROOT_CONFIG, root)
        store.put(APP_CONFIG, root.resolve("app").createDirectory())
        store.put(TEST_CONFIG, root.resolve("test").createDirectory())
    }

    @OptIn(ExperimentalPathApi::class)
    override fun afterAll(context: ExtensionContext) {
        context.getStore(Th2.NAMESPACE).get(ROOT_CONFIG, Path::class.java)
            ?.also {
                LOGGER.info { "Cleaning config dirs $it" }
                it.deleteRecursively()
            }
    }

    internal companion object {
        private val LOGGER = KotlinLogging.logger { }

        internal const val APP_CONFIG: String = "app"

        internal const val TEST_CONFIG: String = "test"

        private const val ROOT_CONFIG: String = "root"
    }
}
