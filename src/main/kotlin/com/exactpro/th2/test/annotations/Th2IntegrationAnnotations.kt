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

package com.exactpro.th2.test.annotations

import com.exactpro.th2.test.extension.CleanupExtension
import com.exactpro.th2.test.extension.Th2CommonFactoryExtension
import com.exactpro.th2.test.extension.Th2ConfigExtension
import com.exactpro.th2.test.extension.Th2CradleExtension
import com.exactpro.th2.test.extension.Th2CustomConfigExtension
import com.exactpro.th2.test.extension.Th2GrpcExtension
import com.exactpro.th2.test.extension.Th2RabbitMqExtension
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

/**
 * `@Th2IntegrationTest` is a JUnit Jupiter extension to activate automatic
 * setup MQ and Cradle services for integration testing.
 *
 * The th2Integration extension configures required services based on the fields
 * defined in the test class.
 *
 * + [RabbitMqSpec][com.exactpro.th2.test.spec.RabbitMqSpec]
 * is used to configure MQ pins
 * + [CradleSpec][com.exactpro.th2.test.spec.CradleSpec]
 * is used to configure Cradle service
 * + [CustomConfigSpec][com.exactpro.th2.test.spec.CustomConfigSpec]
 * is used to configure custom configuration for component
 * + [GrpcSpec][com.exactpro.th2.test.spec.GrpcSpec]
 * is used to configure gRPC services
 *
 * In order to inject pre-configured [CommonFactory][com.exactpro.th2.common.schema.factory.CommonFactory]
 * you need to add parameter with corresponding type to a test method.
 * Use one of [Th2AppFactory] or [Th2TestFactory] annotation to specify which factory you need.
 *
 * ```kotlin
 * @Th2IntegrationTest
 * class YourTest {
 *   @JvmField
 *   val mqSpec: RabbitMqSpec = // configure spec
 *
 *   @JvmField
 *   val cradle: CradleSpec = // configure spec
 *
 *
 *   @Test
 *   fun test(
 *     @Th2AppFactory factory,
 *     @Th2TestFactory testFactory,
 *   ) {
 *     // test
 *   }
 * }
 * ```
 *
 * @see Th2AppFactory
 * @see Th2TestFactory
 * @see CustomConfigProvider
 */
@Target(AnnotationTarget.CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(
    Th2ConfigExtension::class,
    Th2RabbitMqExtension::class,
    Th2CradleExtension::class,
    Th2GrpcExtension::class,
    Th2CustomConfigExtension::class,
    Th2CommonFactoryExtension::class,
    CleanupExtension::class,
)
public annotation class Th2IntegrationTest

/**
 * `@Th2AppFactory` is an annotation marker that mark
 * [CommonFactory][com.exactpro.th2.common.schema.factory.CommonFactory] parameter
 * that should be used to initialize the application under the test.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Th2AppFactory

/**
 * `@Th2TestFactory` is an annotation marker that mark
 * [CommonFactory][com.exactpro.th2.common.schema.factory.CommonFactory] parameter
 * that should be used to test the application under the test.
 *
 * The test common factory is connected to application common factory.
 * It receives messages sent by the application via MQ
 * and allows to send message to the application via MQ.
 * In order to do that the corresponding router from [CommonFactory][com.exactpro.th2.common.schema.factory.CommonFactory]
 * should be used and attribute with **pin name** needs to be added when calling send or subscribe methods on the router
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Th2TestFactory

/**
 * `@CustomConfigProvider` is an annotation that provides a method name to get
 * [CustomConfigSpec][com.exactpro.th2.test.spec.CustomConfigSpec] for a particular test
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class CustomConfigProvider(
    /**
     * Method name that should be invoked to get a [CustomConfigSpec][com.exactpro.th2.test.spec.CustomConfigSpec]
     * for the annotated test method
     */
    val name: String,
)
