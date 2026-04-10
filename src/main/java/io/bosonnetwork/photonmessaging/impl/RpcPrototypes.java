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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
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
	public static final JavaType TYPE_VOID = Json.cborMapper().getTypeFactory().constructType(Void.class);
	public static final JavaType TYPE_INTEGER = Json.cborMapper().getTypeFactory().constructType(Integer.class);
	public static final JavaType TYPE_ID = Json.cborMapper().getTypeFactory().constructType(Id.class);
	public static final JavaType TYPE_IDS = Json.cborMapper().getTypeFactory().constructCollectionType(List.class, Id.class);
	public static final JavaType TYPE_SESSIONS = Json.cborMapper().getTypeFactory().constructCollectionType(List.class, SessionInfo.class);
	public static final JavaType TYPE_CONTACT_MUTATION = Json.cborMapper().getTypeFactory().constructParametricType(ContactMutation.class, Object.class);
	public static final JavaType TYPE_CREATE_CHANNEL_PARAMS = Json.cborMapper().getTypeFactory().constructType(NewChannelInfo.class);
	public static final JavaType TYPE_CHANNEL_INFO = Json.cborMapper().getTypeFactory().constructType(ChannelInfo.class);
	public static final JavaType TYPE_CHANNEL_SESSION_KEY_ROTATION_PARAMS = Json.cborMapper().getTypeFactory().constructType(ChannelSessionKeyRotation.class);
	public static final JavaType TYPE_CHANNEL_MEMBERS_ROLE_PARAMS = Json.cborMapper().getTypeFactory().constructType(ChannelMembersRole.class);
	public static final JavaType TYPE_INVITE_TICKET = Json.cborMapper().getTypeFactory().constructType(InviteTicket.class);
	public static final JavaType TYPE_JSON_NODE = Json.cborMapper().getTypeFactory().constructType(JsonNode.class);

	public static final RpcMethodPrototype SESSION_LIST =
			new RpcMethodPrototype(RpcMethod.SESSION_LIST, TYPE_VOID, TYPE_SESSIONS);

	public static final RpcMethodPrototype SESSION_REVOKE =
			new RpcMethodPrototype(RpcMethod.SESSION_REVOKE, TYPE_ID, TYPE_VOID);

	public static final RpcMethodPrototype CONTACT_MUTATE =
			new RpcMethodPrototype(RpcMethod.CONTACT_MUTATE, TYPE_CONTACT_MUTATION, TYPE_INTEGER);

	public static final RpcMethodPrototype CHANNEL_CREATE =
			new RpcMethodPrototype(RpcMethod.CHANNEL_CREATE, TYPE_CREATE_CHANNEL_PARAMS, TYPE_CHANNEL_INFO);

	public static final RpcMethodPrototype CHANNEL_DELETE =
			new RpcMethodPrototype(RpcMethod.CHANNEL_DELETE, TYPE_VOID, TYPE_VOID);

	public static final RpcMethodPrototype CHANNEL_TRANSFER_OWNERSHIP =
			new RpcMethodPrototype(RpcMethod.CHANNEL_TRANSFER_OWNERSHIP, TYPE_ID, TYPE_VOID);

	public static final RpcMethodPrototype CHANNEL_ROTATE_SESSION_KEY =
			new RpcMethodPrototype(RpcMethod.CHANNEL_ROTATE_SESSION_KEY, TYPE_CHANNEL_SESSION_KEY_ROTATION_PARAMS, TYPE_VOID);

	public static final RpcMethodPrototype CHANNEL_UPDATE_INFO =
			new RpcMethodPrototype(RpcMethod.CHANNEL_UPDATE_INFO, TYPE_JSON_NODE, TYPE_VOID);

	public static final RpcMethodPrototype CHANNEL_UPDATE_MEMBERS_ROLE =
			new RpcMethodPrototype(RpcMethod.CHANNEL_UPDATE_MEMBERS_ROLE, TYPE_CHANNEL_MEMBERS_ROLE_PARAMS, TYPE_IDS);

	public static final RpcMethodPrototype CHANNEL_BAN_MEMBERS =
			new RpcMethodPrototype(RpcMethod.CHANNEL_BAN_MEMBERS, TYPE_IDS, TYPE_IDS);

	public static final RpcMethodPrototype CHANNEL_UNBAN_MEMBERS =
			new RpcMethodPrototype(RpcMethod.CHANNEL_UNBAN_MEMBERS, TYPE_IDS, TYPE_IDS);

	public static final RpcMethodPrototype CHANNEL_REMOVE_MEMBERS =
			new RpcMethodPrototype(RpcMethod.CHANNEL_REMOVE_MEMBERS, TYPE_IDS, TYPE_IDS);

	public static final RpcMethodPrototype CHANNEL_JOIN =
			new RpcMethodPrototype(RpcMethod.CHANNEL_JOIN, TYPE_INVITE_TICKET, TYPE_CHANNEL_INFO);

	public static final RpcMethodPrototype CHANNEL_LEAVE =
			new RpcMethodPrototype(RpcMethod.CHANNEL_LEAVE, TYPE_VOID, TYPE_VOID);

	public static final RpcMethodPrototype CHANNEL_INFO =
			new RpcMethodPrototype(RpcMethod.CHANNEL_INFO, TYPE_VOID, TYPE_CHANNEL_INFO);

}