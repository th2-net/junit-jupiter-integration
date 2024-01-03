/*
 * Copyright 2024 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.common.schema.factory.extensions.getCustomConfiguration
import com.exactpro.th2.test.annotations.CustomConfigProvider
import com.exactpro.th2.test.annotations.Th2AppFactory
import com.exactpro.th2.test.annotations.Th2IntegrationTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Th2IntegrationTest
internal class TestCustomConfigSpec {
    private var testValue: Int = 0

    val custom = CustomConfigSpec.fromSupplier {
        """
        {
            "test": "$testValue"
        }
        """.trimIndent()
    }

    @BeforeEach
    fun setUp() {
        testValue = 42
    }

    @Test
    fun `before each change is visible in test`(
        @Th2AppFactory factory: CommonFactory,
    ) {
        val setting = factory.getCustomConfiguration<Setting>()
        Assertions.assertEquals(42, setting.test, "unexpected value in property")
    }

    @Test
    @CustomConfigProvider("config")
    fun `can override custom config per test`(
        @Th2AppFactory factory: CommonFactory,
    ) {
        val setting = factory.getCustomConfiguration<Setting>()
        Assertions.assertEquals(54, setting.test, "unexpected value in property")
    }

    fun config(): CustomConfigSpec =
        CustomConfigSpec.fromObject(Setting(54))

    private data class Setting(val test: Int)
}