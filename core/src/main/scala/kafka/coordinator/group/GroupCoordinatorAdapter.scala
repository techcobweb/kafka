/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kafka.coordinator.group

import kafka.server.RequestLocal
import org.apache.kafka.common.message.{JoinGroupRequestData, JoinGroupResponseData}
import org.apache.kafka.common.requests.RequestContext
import org.apache.kafka.common.utils.BufferSupplier

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

/**
 * GroupCoordinatorAdapter is a thin wrapper around kafka.coordinator.group.GroupCoordinator
 * that exposes the new org.apache.kafka.coordinator.group.GroupCoordinator interface.
 */
class GroupCoordinatorAdapter(
  val coordinator: GroupCoordinator
) extends org.apache.kafka.coordinator.group.GroupCoordinator {

  override def joinGroup(
    context: RequestContext,
    request: JoinGroupRequestData,
    bufferSupplier: BufferSupplier
  ): CompletableFuture[JoinGroupResponseData] = {
    val future = new CompletableFuture[JoinGroupResponseData]()

    def callback(joinResult: JoinGroupResult): Unit = {
      future.complete(new JoinGroupResponseData()
        .setErrorCode(joinResult.error.code)
        .setGenerationId(joinResult.generationId)
        .setProtocolType(joinResult.protocolType.orNull)
        .setProtocolName(joinResult.protocolName.orNull)
        .setLeader(joinResult.leaderId)
        .setSkipAssignment(joinResult.skipAssignment)
        .setMemberId(joinResult.memberId)
        .setMembers(joinResult.members.asJava)
      )
    }

    val groupInstanceId = Option(request.groupInstanceId)

    // Only return MEMBER_ID_REQUIRED error if joinGroupRequest version is >= 4
    // and groupInstanceId is configured to unknown.
    val requireKnownMemberId = context.apiVersion >= 4 && groupInstanceId.isEmpty

    val protocols = request.protocols.valuesList.asScala.map { protocol =>
      (protocol.name, protocol.metadata)
    }.toList

    val supportSkippingAssignment = context.apiVersion >= 9

    coordinator.handleJoinGroup(
      request.groupId,
      request.memberId,
      groupInstanceId,
      requireKnownMemberId,
      supportSkippingAssignment,
      context.clientId,
      context.clientAddress.toString,
      request.rebalanceTimeoutMs,
      request.sessionTimeoutMs,
      request.protocolType,
      protocols,
      callback,
      Option(request.reason),
      RequestLocal(bufferSupplier)
    )

    future
  }
}
