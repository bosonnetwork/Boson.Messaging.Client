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
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.InviteTicket;
import io.bosonnetwork.photonmessaging.SessionInfo;
import io.bosonnetwork.photonmessaging.impl.rpc.GenericRpcResponse;
import io.bosonnetwork.photonmessaging.impl.rpc.InvalidRpcResultException;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcError;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcException;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcMethod;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcRequest;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcResponse;

public class RpcCall<P, R> {
	private static final long DEFAULT_TIMEOUT = 30 * 1000; // 30 seconds
	private final RpcRequest<P> request;
	private RpcResponse<R> response;
	private final long timeout; // in milliseconds
	private final JavaType resultType;
	private final Promise<R> responsePromise;

	private static final AtomicLong nextId = new AtomicLong(new Random().nextLong(8192, 65535));

	private RpcCall(RpcMethod method, P params, JavaType resultType, long timeout) {
		this.request = new RpcRequest<>(nextId.getAndIncrement(), method, params, null);
		this.resultType = resultType;
		this.timeout = timeout;
		this.responsePromise = Promise.promise();
	}

	private RpcCall(RpcMethod method, P params, JavaType resultType) {
		this(method, params, resultType, DEFAULT_TIMEOUT);
	}

	public long getId() {
		return request.getId();
	}

	public RpcMethod getMethod() {
		return request.getMethod();
	}

	public RpcRequest<P> getRequest() {
		return request;
	}

	public  RpcResponse<R> getResponse() {
		return response;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setResponse(GenericRpcResponse response) {
		if (response.getError() != null) {
			// noinspection unchecked
			this.response = (RpcResponse<R>) response;
			RpcError error = response.getError();
			responsePromise.tryFail(rpcErrorToException(error));
		} else {
			try {
				this.response = new RpcResponse<>(response.getId(), response.getResultAs(resultType), null);
				responsePromise.tryComplete(this.response.getResult());
			} catch (InvalidRpcResultException e) {
				responsePromise.tryFail(e);
			}
		}
	}

	public Future<R> getFuture() {
		return timeout <= 0 ? responsePromise.future() :
				responsePromise.future().timeout(timeout, TimeUnit.MILLISECONDS);
	}

	private static RpcException rpcErrorToException(RpcError error) {
		// TODO: map error codes to the corresponding exceptions
		return new RpcException(error.getCode(), error.getMessage());
	}

	public static RpcCall<Void, List<SessionInfo>> sessionList() {
		return new RpcCall<>(RpcMethod.SESSION_LIST, null, RpcPrototypes.SESSION_LIST.resultType());
	}

	public static RpcCall<Id, Void> revokeSession(Id deviceId) {
		return new RpcCall<>(RpcMethod.SESSION_REVOKE, deviceId, RpcPrototypes.SESSION_REVOKE.resultType());
	}

	public static <T> RpcCall<ContactMutation, Integer> contactMutate(ContactMutation mutation) {
		return new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation, RpcPrototypes.CONTACT_MUTATE.resultType());
	}

	public static RpcCall<NewChannelInfo, ChannelInfo> createChannel(NewChannelInfo params) {
		return new RpcCall<>(RpcMethod.CHANNEL_CREATE, params, RpcPrototypes.CHANNEL_CREATE.resultType());
	}

	public static RpcCall<Void, Void> deleteChannel() {
		return new RpcCall<>(RpcMethod.CHANNEL_DELETE, null, RpcPrototypes.CHANNEL_DELETE.resultType());
	}

	public static RpcCall<InviteTicket, ChannelInfo> joinChannel(InviteTicket ticket) {
		return new RpcCall<>(RpcMethod.CHANNEL_JOIN, ticket, RpcPrototypes.CHANNEL_JOIN.resultType());
	}

	public static RpcCall<Void, Void> leaveChannel() {
		return new RpcCall<>(RpcMethod.CHANNEL_LEAVE, null, RpcPrototypes.CHANNEL_LEAVE.resultType());
	}

	public static RpcCall<Id, Void> transferChannelOwnership(Id newOwner) {
		return new RpcCall<>(RpcMethod.CHANNEL_TRANSFER_OWNERSHIP, newOwner, RpcPrototypes.CHANNEL_TRANSFER_OWNERSHIP.resultType());
	}

	public static RpcCall<ChannelSessionKeyRotation, Void> rotateChannelSessionKey(ChannelSessionKeyRotation params) {
		return new RpcCall<>(RpcMethod.CHANNEL_ROTATE_SESSION_KEY, params, RpcPrototypes.CHANNEL_ROTATE_SESSION_KEY.resultType());
	}

	public static RpcCall<JsonNode, Void> updateChannelInfo(JsonNode changes) {
		return new RpcCall<>(RpcMethod.CHANNEL_UPDATE_INFO, changes, RpcPrototypes.CHANNEL_UPDATE_INFO.resultType());
	}

	public static RpcCall<ChannelMembersRole, List<Id>> updateChannelMembersRole(ChannelMembersRole params) {
		return new RpcCall<>(RpcMethod.CHANNEL_UPDATE_MEMBERS_ROLE, params, RpcPrototypes.CHANNEL_UPDATE_MEMBERS_ROLE.resultType());
	}

	public static RpcCall<List<Id>, List<Id>> banChannelMembers(List<Id> memberIds) {
		return new RpcCall<>(RpcMethod.CHANNEL_BAN_MEMBERS, memberIds, RpcPrototypes.CHANNEL_BAN_MEMBERS.resultType());
	}

	public static RpcCall<List<Id>, List<Id>> unbanChannelMembers(List<Id> memberIds) {
		return new RpcCall<>(RpcMethod.CHANNEL_UNBAN_MEMBERS, memberIds, RpcPrototypes.CHANNEL_UNBAN_MEMBERS.resultType());
	}

	public static RpcCall<List<Id>, List<Id>> removeChannelMembers(List<Id> memberIds) {
		return new RpcCall<>(RpcMethod.CHANNEL_REMOVE_MEMBERS, memberIds, RpcPrototypes.CHANNEL_REMOVE_MEMBERS.resultType());
	}

	public static RpcCall<Void, ChannelInfo> getChannelInfo() {
		return new RpcCall<>(RpcMethod.CHANNEL_INFO, null, RpcPrototypes.CHANNEL_INFO.resultType());
	}
}