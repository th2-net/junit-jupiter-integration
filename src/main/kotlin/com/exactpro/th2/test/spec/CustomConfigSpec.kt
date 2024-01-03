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

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory
import java.util.function.Supplier

public class CustomConfigSpec private constructor(
    internal val contentSupplier: Supplier<String>,
) {
    public companion object {
        @JvmStatic
        public fun fromString(content: String): CustomConfigSpec =
            CustomConfigSpec { content }

        @JvmStatic
        public fun fromObject(obj: Any): CustomConfigSpec {
            val content = AbstractCommonFactory.MAPPER.writeValueAsString(obj)
            return fromString(content)
        }

        @JvmStatic
        public fun fromSupplier(supplier: Supplier<String>): CustomConfigSpec =
            CustomConfigSpec(supplier)
    }
}
