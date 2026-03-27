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

package io.bosonnetwork.photonmessaging.impl.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The RPC methods supported by the photon messaging service.
 */
public enum RpcMethod {
	// Service RPC methods, range 0x00 - 0x4F
	/**
	 * Lists all active user sessions.
	 */
	SESSION_LIST(0x01),

	/**
	 * Revokes a user session.
	 */
	SESSION_REVOKE(0x02),

	/**
	 * Update the contact mutations.
	 */
	CONTACT_MUTATE(0x11),

	/**
	 * Creates a channel.
	 */
	CHANNEL_CREATE(0x21),

	// Channel RPC methods, range @code 0x50 - 0x6F
	// Privileged channel RPC methods, range 0x50 - 0x5F
	/**
	 * Deletes the channel.
	 */
	CHANNEL_DELETE(0x51),

	/**
	 * Transfers channel ownership.
	 */
	CHANNEL_TRANSFER_OWNERSHIP(0x52),

	/**
	 * Rotates the channel session key.
	 */
	CHANNEL_ROTATE_SESSION_KEY(0x53),

	/**
	 * Updates the channel info.
	 */
	CHANNEL_UPDATE_INFO(0x54),

	/**
	 * Updates channel members roles.
	 */
	CHANNEL_UPDATE_MEMBERS_ROLE(0x61),

	/**
	 * Bans channel members.
	 */
	CHANNEL_BAN_MEMBERS(0x62),

	/**
	 * Unbans channel members.
	 */
	CHANNEL_UNBAN_MEMBERS(0x63),

	/**
	 * Removes channel members.
	 */
	CHANNEL_REMOVE_MEMBERS(0x64),

	// Unprivileged channel RPC methods, range 0x60 - 0x6F
	/**
	 * Joins the channel.
	 */
	CHANNEL_JOIN(0x71),

	/**
	 * Leaves the channel.
	 */
	CHANNEL_LEAVE(0x72),

	/**
	 * Retrieves channel information.
	 */
	CHANNEL_INFO(0x73),

	/**
	 * Retrieves channel members.
	 */
	CHANNEL_MEMBERS(0x74);

	private final int value;

	/**
	 * Creates an RPC method with its serialized integer value.
	 *
	 * @param value the integer value of the RPC method
	 */
	RpcMethod(int value) {
		this.value = value;
	}

	/**
	 * Returns the serialized integer value of this RPC method.
	 *
	 * @return the integer value associated with this method
	 */
	@JsonValue
	public int value() {
		return value;
	}

	/**
	 * Resolves an RPC method from its serialized integer value.
	 *
	 * @param value the integer value to resolve
	 * @return the corresponding RPC method
	 * @throws IllegalArgumentException if the value does not map to any RPC method
	 */
	@JsonCreator
	public static RpcMethod valueOf(int value) {
		return switch (value) {
			case 0x01 -> SESSION_LIST;
			case 0x02 -> SESSION_REVOKE;
			case 0x11 -> CONTACT_MUTATE;
			case 0x21 -> CHANNEL_CREATE;
			case 0x51 -> CHANNEL_DELETE;
			case 0x52 -> CHANNEL_TRANSFER_OWNERSHIP;
			case 0x53 -> CHANNEL_ROTATE_SESSION_KEY;
			case 0x54 -> CHANNEL_UPDATE_INFO;
			case 0x61 -> CHANNEL_UPDATE_MEMBERS_ROLE;
			case 0x62 -> CHANNEL_BAN_MEMBERS;
			case 0x63 -> CHANNEL_UNBAN_MEMBERS;
			case 0x64 -> CHANNEL_REMOVE_MEMBERS;
			case 0x71 -> CHANNEL_JOIN;
			case 0x72 -> CHANNEL_LEAVE;
			case 0x73 -> CHANNEL_INFO;
			case 0x74 -> CHANNEL_MEMBERS;
			default -> throw new IllegalArgumentException("Invalid method: " + value);
		};
	}

	/**
	 * Returns whether this method is a service RPC method.
	 *
	 * @return {@code true} if this method is in the service RPC range; {@code false} otherwise
	 */
	public boolean isServiceRpc() {
		return value < 0x50;
	}

	/**
	 * Returns whether this method is a channel RPC method.
	 *
	 * @return {@code true} if this method is in the channel RPC range; {@code false} otherwise
	 */
	public boolean isChannelRpc() {
		return value >= 0x50 && value < 0x80;
	}

	/**
	 * Determines whether the current RPC method is an owner-privileged channel RPC.
	 *
	 * @return {@code true} if the method is in the owner-privileged channel RPC range;
	 *         {@code false} otherwise
	 */
	public boolean isOwnerPrivilegedChannelRpc() {
		return value >= 0x50 && value < 0x60;
	}

	/**
	 * Determines whether the current RPC method is a moderator-privileged channel RPC.
	 *
	 * @return {@code true} if the method is in the moderator-privileged channel RPC range;
	 *         {@code false} otherwise
	 */
	public boolean isModeratorPrivilegedChannelRpc() {
		return value >= 0x60 && value < 0x70;
	}

	/**
	 * Returns whether this method is an unprivileged channel RPC method.
	 *
	 * @return {@code true} if this method is in the unprivileged channel RPC range; {@code false} otherwise
	 */
	public boolean isUnprivilegedChannelRpc() {
		return value >= 0x70 && value < 0x80;
	}
}