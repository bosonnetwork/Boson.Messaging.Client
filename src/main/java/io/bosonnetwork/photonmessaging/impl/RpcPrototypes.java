/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.InviteTicket;
import io.bosonnetwork.photonmessaging.SessionInfo;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcMethod;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcMethodPrototype;

/**
 * The RpcPrototypes class provides predefined RPC (Remote Procedure Call) method prototypes
 * for various operations. These prototypes define the method name, input parameter types,
 * and return types for RPC calls, ensuring type safety and consistency across the system.
 */
public class RpcPrototypes {
	public static TypeReference<List<Id>> IDS_TYPE = new TypeReference<>() {};

	public static final RpcMethodPrototype<Class<Void>, TypeReference<List<SessionInfo>>> SESSION_LIST =
			new RpcMethodPrototype<>(RpcMethod.SESSION_LIST, Void.class, new TypeReference<>() {});

	public static final RpcMethodPrototype<Class<Id>, Class<Boolean>> SESSION_REVOKE =
			new RpcMethodPrototype<>(RpcMethod.SESSION_REVOKE, Id.class, Boolean.class);

	public static final RpcMethodPrototype<Class<ContactMutation>, Class<Integer>> CONTACT_MUTATE =
			new RpcMethodPrototype<>(RpcMethod.CONTACT_MUTATE, ContactMutation.class, Integer.class);

	public static final RpcMethodPrototype<Class<CreateChannelParams>, Class<Channel>> CHANNEL_CREATE =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_CREATE, CreateChannelParams.class, Channel.class);

	public static final RpcMethodPrototype<Class<Void>, Class<Boolean>> CHANNEL_DELETE =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_DELETE, Void.class, Boolean.class);

	public static final RpcMethodPrototype<Class<Id>, Class<Boolean>> CHANNEL_TRANSFER_OWNERSHIP =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_TRANSFER_OWNERSHIP, Id.class, Boolean.class);

	public static final RpcMethodPrototype<Class<ChannelSessionKeyRotationParams>, Class<Boolean>> CHANNEL_ROTATE_SESSION_KEY =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_ROTATE_SESSION_KEY, ChannelSessionKeyRotationParams.class, Boolean.class);

	public static final RpcMethodPrototype<Class<JsonNode>, Class<Boolean>> CHANNEL_UPDATE_INFO =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_UPDATE_INFO, JsonNode.class, Boolean.class);

	public static final RpcMethodPrototype<Class<ChannelMembersRoleParams>, TypeReference<List<Id>>> CHANNEL_UPDATE_MEMBERS_ROLE =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_UPDATE_MEMBERS_ROLE, ChannelMembersRoleParams.class, IDS_TYPE);

	public static final RpcMethodPrototype<TypeReference<List<Id>> , TypeReference<List<Id>>> CHANNEL_BAN_MEMBERS =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_BAN_MEMBERS, IDS_TYPE, IDS_TYPE);

	public static final RpcMethodPrototype<TypeReference<List<Id>> , TypeReference<List<Id>>> CHANNEL_UNBAN_MEMBERS =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_UNBAN_MEMBERS, IDS_TYPE, IDS_TYPE);

	public static final RpcMethodPrototype<TypeReference<List<Id>> , TypeReference<List<Id>>> CHANNEL_REMOVE_MEMBERS =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_REMOVE_MEMBERS, IDS_TYPE, IDS_TYPE);

	public static final RpcMethodPrototype<Class<InviteTicket>, Class<Channel.Member>> CHANNEL_JOIN =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_JOIN, InviteTicket.class, Channel.Member.class);

	public static final RpcMethodPrototype<Class<Void>, Class<Boolean>> CHANNEL_LEAVE =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_LEAVE, Void.class, Boolean.class);

	public static final RpcMethodPrototype<Class<Void>, Class<Channel>> CHANNEL_INFO =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_INFO, Void.class, Channel.class);

	public static final RpcMethodPrototype<Class<Void>, TypeReference<List<Channel.Member>>> CHANNEL_MEMBERS =
			new RpcMethodPrototype<>(RpcMethod.CHANNEL_MEMBERS, Void.class, new TypeReference<>() {});

	public record CreateChannelParams(@JsonProperty(value = "sid", required = true) Id sessionId,
									  @JsonProperty(value = "p", required = true) Channel.Permission permission,
									  @JsonProperty("n") String name,
									  @JsonProperty("nt") String notice,
									  @JsonProperty("a") boolean announce) {
	}

	public record ChannelInfo(@JsonProperty(value = "id", required = true) Id channelId,
	                          @JsonProperty(value = "o", required = true) Id ownerId,
	                          @JsonProperty(value = "sid", required = true) Id sessionId,
	                          @JsonProperty(value = "sk", required = true) byte[] sessionKey,
	                          @JsonProperty(value = "p", required = true) Channel.Permission permission,
	                          @JsonProperty(value = "n") @JsonInclude(JsonInclude.Include.NON_NULL) String name,
	                          @JsonProperty(value = "nt") @JsonInclude(JsonInclude.Include.NON_EMPTY) String notice,
	                          @JsonProperty(value = "a") @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean announce,
	                          @JsonProperty(value = "c") long createdAt,
	                          @JsonProperty(value = "u") long updateAt) {
	}

	public record ChannelSessionKeyRotationParams(@JsonProperty(value = "sid", required = true) Id sessionId,
												  @JsonProperty(value = "sk", required = true) byte[] sessionKey) {
	}

	public record ChannelMembersRoleParams(@JsonProperty(value = "ids", required = true) List<Id> memberIds,
											@JsonProperty(value = "r", required = true) Channel.Role role) {
	}
}