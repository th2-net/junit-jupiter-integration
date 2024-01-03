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

import com.exactpro.th2.test.annotations.CustomConfigProvider
import com.exactpro.th2.test.integration.ConfigurationWriter
import com.exactpro.th2.test.spec.CustomConfigSpec
import mu.KotlinLogging
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import org.junit.platform.commons.support.AnnotationSupport
import org.junit.platform.commons.support.ReflectionSupport
import kotlin.io.path.outputStream

public class Th2CustomConfigExtension : TestInstancePostProcessor, BeforeTestExecutionCallback {
    private var spec: CustomConfigSpec? = null
    override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
        val fields = testInstance::class.findFields<CustomConfigSpec>().ifEmpty { return }
        spec = testInstance.getSingle(fields)
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val testMethod = context.requiredTestMethod
        val customConfigSpec: CustomConfigSpec = AnnotationSupport.findAnnotation(testMethod, CustomConfigProvider::class.java)
            .map {
                val configProvider = ReflectionSupport.findMethod(
                    testClass,
                    it.name,
                ).orElseGet { error("cannot find method ${it.name} in class $testClass") }
                val config = ReflectionSupport.invokeMethod(configProvider, context.requiredTestInstance)
                (config as? CustomConfigSpec) ?: error("method $configProvider does not return ${CustomConfigSpec::class}")
            }.orElseGet {
                spec
            } ?: return
        writeSpec(context, customConfigSpec)
    }

    private fun writeSpec(
        context: ExtensionContext,
        customSpec: CustomConfigSpec,
    ) {
        val appFolder = Th2.getAppConfigFolder(context)
        val customConfigFile = appFolder.resolve(ConfigurationWriter.CUSTOM_CONFIG)
        LOGGER.debug { "Writing custom config to file $customConfigFile" }
        customConfigFile.outputStream().use {
            // the content is a JSON already
            it.write(customSpec.contentSupplier.get().toByteArray(Charsets.UTF_8))
        }
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}
