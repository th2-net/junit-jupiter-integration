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

import com.exactpro.th2.common.schema.factory.CommonFactory
import java.io.OutputStream

internal object ConfigurationWriter {
    private val MAPPER = CommonFactory.MAPPER
    const val ROUTER_MQ_CONFIG: String = "mq.json"
    const val CONNECTION_MQ_CONFIG: String = "rabbitMQ.json"
    const val PROMETHEUS_CONFIG: String = "prometheus.json"
    const val CRADLE_CONNECTION_CONFIG: String = "cradle.json"
    const val CRADLE_PARAMETERS_CONFIG: String = "cradle_manager.json"
    const val CUSTOM_CONFIG: String = "custom.json"
    const val BOX_CONFIG: String = "box.json"

    fun write(cfg: Any, output: OutputStream) {
        MAPPER.writeValue(output, cfg)
    }
}
