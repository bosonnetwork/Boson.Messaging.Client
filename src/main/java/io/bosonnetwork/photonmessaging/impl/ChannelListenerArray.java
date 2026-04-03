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

import java.util.ArrayList;
import java.util.List;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.ChannelListener;

public class ChannelListenerArray extends ArrayList<ChannelListener> implements ChannelListener {
	private static final long serialVersionUID = -6049409118596156775L;

	public ChannelListenerArray(ChannelListener existing, ChannelListener newListener) {
		super();
		add(existing);
		add(newListener);
	}

	@Override
	public void onJoinedChannel(Channel channel) {
		for (ChannelListener listener : this)
			listener.onJoinedChannel(channel);
	}

	@Override
	public void onLeftChannel(Channel channel) {
		for (ChannelListener listener : this)
			listener.onLeftChannel(channel);
	}

	@Override
	public void onChannelDeleted(Channel channel) {
		for (ChannelListener listener : this)
			listener.onChannelDeleted(channel);
	}

	@Override
	public void onChannelOwnershipTransferred(Channel channel, Id oldOwner, Id newOwner) {
		for (ChannelListener listener : this)
			listener.onChannelOwnershipTransferred(channel, oldOwner, newOwner);
	}

	@Override
	public void onChannelSessionKeyRotated(Channel channel) {
		for (ChannelListener listener : this)
			listener.onChannelSessionKeyRotated(channel);
	}

	@Override
	public void onChannelUpdated(Channel channel) {
		for (ChannelListener listener : this)
			listener.onChannelUpdated(channel);
	}

	@Override
	public void onChannelMembers(Channel channel, List<Channel.Member> members) {
		for (ChannelListener listener : this)
			listener.onChannelMembers(channel, members);
	}

	@Override
	public void onChannelMemberJoined(Channel channel, Channel.Member member) {
		for (ChannelListener listener : this)
			listener.onChannelMemberJoined(channel, member);
	}

	@Override
	public void onChannelMemberLeft(Channel channel, Channel.Member member) {
		for (ChannelListener listener : this)
			listener.onChannelMemberLeft(channel, member);
	}

	@Override
	public void onChannelMembersRemoved(Channel channel, List<Channel.Member> members) {
		for (ChannelListener listener : this)
			listener.onChannelMembersRemoved(channel, members);
	}

	@Override
	public void onChannelMembersBanned(Channel channel, List<Channel.Member> banned) {
		for (ChannelListener listener : this)
			listener.onChannelMembersBanned(channel, banned);
	}

	@Override
	public void onChannelMembersUnbanned(Channel channel, List<Channel.Member> unbanned) {
		for (ChannelListener listener : this)
			listener.onChannelMembersUnbanned(channel, unbanned);
	}

	@Override
	public void onChannelMembersRoleChanged(Channel channel, List<Channel.Member> changed, Channel.Role role) {
		for (ChannelListener listener : this)
			listener.onChannelMembersRoleChanged(channel, changed, role);
	}
}