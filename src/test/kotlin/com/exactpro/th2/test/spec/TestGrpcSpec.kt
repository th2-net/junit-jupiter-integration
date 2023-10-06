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

import com.exactpro.th2.check1.grpc.Check1Grpc
import com.exactpro.th2.check1.grpc.Check1Service
import com.exactpro.th2.check1.grpc.CheckpointRequest
import com.exactpro.th2.check1.grpc.CheckpointResponse
import com.exactpro.th2.common.grpc.Checkpoint
import com.exactpro.th2.common.schema.factory.CommonFactory
import com.exactpro.th2.test.annotations.Th2AppFactory
import com.exactpro.th2.test.annotations.Th2IntegrationTest
import com.exactpro.th2.test.annotations.Th2TestFactory
import com.google.protobuf.util.Timestamps
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class TestGrpcSpec {
    @Nested
    @Th2IntegrationTest
    inner class Service {
        @JvmField
        internal val grpc = GrpcSpec.create()
            .client<Check1Service>()

        @Test
        fun `registers grpc service`(
            @Th2AppFactory factory: CommonFactory,
            @Th2TestFactory testFactory: CommonFactory,
        ) {
            testFactory.grpcRouter.startServer(
                TestCheck1Service(),
            ).start()

            val check1Service = factory.grpcRouter.getService(Check1Service::class.java)

            val response = check1Service.createCheckpoint(
                CheckpointRequest.getDefaultInstance(),
            )
            val sessionAliasToDirectionCheckpoint =
                response.checkpoint.bookNameToSessionAliasToDirectionCheckpointMap["test_book"]
            Assertions.assertNotNull(sessionAliasToDirectionCheckpoint, "cannot find book test_book")
            val directionCheckpoint =
                sessionAliasToDirectionCheckpoint!!.sessionAliasToDirectionCheckpointMap["test_alias"]
            Assertions.assertNotNull(directionCheckpoint, "cannot find alias test_alias")
            val checkpointData = directionCheckpoint!!.directionToCheckpointDataMap[1]
            Assertions.assertNotNull(checkpointData, "cannot find checkpoint for 1 direction")
            Assertions.assertEquals(
                42,
                checkpointData!!.sequence,
                "unexpected sequence in checkpoint",
            )
        }
    }

    @Nested
    @Th2IntegrationTest
    inner class Server {
        @JvmField
        internal val grpc = GrpcSpec.create()
            .server<Check1Service>()

        @Test
        fun `registers grpc server`(
            @Th2AppFactory factory: CommonFactory,
            @Th2TestFactory testFactory: CommonFactory,
        ) {
            factory.grpcRouter.startServer(
                TestCheck1Service(),
            ).start()

            val check1Service = testFactory.grpcRouter.getService(Check1Service::class.java)

            val response = check1Service.createCheckpoint(
                CheckpointRequest.getDefaultInstance(),
            )
            val sessionAliasToDirectionCheckpoint =
                response.checkpoint.bookNameToSessionAliasToDirectionCheckpointMap["test_book"]
            Assertions.assertNotNull(sessionAliasToDirectionCheckpoint, "cannot find book test_book")
            val directionCheckpoint =
                sessionAliasToDirectionCheckpoint!!.sessionAliasToDirectionCheckpointMap["test_alias"]
            Assertions.assertNotNull(directionCheckpoint, "cannot find alias test_alias")
            val checkpointData = directionCheckpoint!!.directionToCheckpointDataMap[1]
            Assertions.assertNotNull(checkpointData, "cannot find checkpoint for 1 direction")
            Assertions.assertEquals(
                42,
                checkpointData!!.sequence,
                "unexpected sequence in checkpoint",
            )
        }
    }
}

private class TestCheck1Service : Check1Grpc.Check1ImplBase() {
    override fun createCheckpoint(
        request: CheckpointRequest,
        responseObserver: StreamObserver<CheckpointResponse>,
    ) {
        val checkpoint = Checkpoint.newBuilder()
            .putBookNameToSessionAliasToDirectionCheckpoint(
                "test_book",
                Checkpoint.SessionAliasToDirectionCheckpoint.newBuilder()
                    .putSessionAliasToDirectionCheckpoint(
                        "test_alias",
                        Checkpoint.DirectionCheckpoint.newBuilder()
                            .putDirectionToCheckpointData(
                                1,
                                Checkpoint.CheckpointData.newBuilder()
                                    .setSequence(42)
                                    .setTimestamp(Timestamps.now())
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        with(responseObserver) {
            onNext(CheckpointResponse.newBuilder().setCheckpoint(checkpoint).build())
            onCompleted()
        }
    }
}
