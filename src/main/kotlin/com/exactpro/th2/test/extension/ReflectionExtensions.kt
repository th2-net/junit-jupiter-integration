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

import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
import java.lang.reflect.Field
import kotlin.reflect.KClass

private fun List<Field>.definitions(): String =
    joinToString {
        "${it.name} in ${it.declaringClass}"
    }

internal inline fun <reified T> Any.getSingle(fields: List<Field>): T {
    require(fields.size == 1) {
        "only one field with ${T::class} can be specified but found: ${fields.definitions()}"
    }
    val field = fields.single()
    return getFieldValue<T>(field)
}

internal inline fun <reified T> Any.getFieldValue(field: Field): T {
    if (!field.canAccess(this)) {
        require(field.trySetAccessible()) {
            "cannot set access to the field $field"
        }
    }
    return T::class.java.cast(field.get(this))
}

internal inline fun <reified T> Any.getFieldOrDefault(default: () -> T): T {
    val fields = this::class.findFields<T>()
    check(fields.size < 2) {
        "only one ${T::class.simpleName} can be specified in the class"
    }
    return fields.singleOrNull()?.let(this::getFieldValue)
        ?: default()
}

internal inline fun <reified T> KClass<*>.findFields(): List<Field> =
    ReflectionSupport.findFields(
        java,
        { field -> field.type == T::class.java },
        HierarchyTraversalMode.TOP_DOWN,
    )
