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

package io.bosonnetwork.photonmessaging;

import java.util.List;

import io.bosonnetwork.Id;

/**
 * Listener for channel-related events in the messaging system.
 * Implementations of this interface can be used to monitor channel lifecycle,
 * membership changes, and property updates.
 */
public interface ChannelListener {
	/**
	 * Called when the current user has joined a new channel.
	 * The channel object already includes the member's private key.
	 *
	 * @param channel the channel that was joined
	 */
	void onJoinedChannel(Channel channel);

	/**
	 * Called when the current user has left a channel.
	 *
	 * @param channel the channel that was left
	 */
	void onLeftChannel(Channel channel);

	/**
	 * Called when a channel was deleted by its owner.
	 *
	 * @param channel the channel that was deleted
	 */
	void onChannelDeleted(Channel channel);

	/**
	 * Called when the ownership of a channel has been transferred from one user to another.
	 *
	 * @param channel  the channel where ownership was transferred
	 * @param oldOwner the ID of the previous owner of the channel
	 * @param newOwner the ID of the new owner of the channel
	 */
	void onChannelOwnershipTransferred(Channel channel, Id oldOwner, Id newOwner);

	/**
	 * Called when the session key for a channel has been rotated.
	 * This indicates that a new session key has been established for the
	 * specified channel, which could be due to security reasons or routine updates.
	 *
	 * @param channel the channel for which the session key was rotated
	 */
	void onChannelSessionKeyRotated(Channel channel);

	/**
	 * Called when the channel information (such as name or description) was updated.
	 *
	 * @param channel the updated channel
	 */
	void onChannelUpdated(Channel channel);

	/**
	 * Called when a new member has joined the channel.
	 *
	 * @param channel the channel joined
	 * @param member  the member who joined
	 */
	void onChannelMemberJoined(Channel channel, Channel.Member member);

	/**
	 * Called when a member has left the channel.
	 *
	 * @param channel the channel left
	 * @param member  the member who left
	 */
	void onChannelMemberLeft(Channel channel, Channel.Member member);

	/**
	 * Called when members have been removed from the channel by an administrator.
	 *
	 * @param channel the channel from which members were removed
	 * @param members the list of members removed
	 */
	void onChannelMembersRemoved(Channel channel, List<Channel.Member> members);

	/**
	 * Called when members have been banned from the channel.
	 *
	 * @param channel the channel where members were banned
	 * @param banned  the list of banned members
	 */
	void onChannelMembersBanned(Channel channel, List<Channel.Member> banned);

	/**
	 * Called when members have been unbanned from the channel.
	 *
	 * @param channel  the channel where members were unbanned
	 * @param unbanned the list of unbanned members
	 */
	void onChannelMembersUnbanned(Channel channel, List<Channel.Member> unbanned);

	/**
	 * Called when the role of one or more members in the channel has changed.
	 *
	 * @param channel the channel where the role change occurred
	 * @param changed the list of members whose roles were updated
	 * @param role    the new role assigned to these members
	 */
	void onChannelMembersRoleChanged(Channel channel, List<Channel.Member> changed, Channel.Role role);
}