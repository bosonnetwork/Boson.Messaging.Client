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
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.ChannelListener;

public class ChannelListenerArray extends CopyOnWriteArrayList<ChannelListener> implements ChannelListener {
	private static final long serialVersionUID = -6049409118596156775L;
	private static final Logger log = LoggerFactory.getLogger(ChannelListenerArray.class);

	public ChannelListenerArray(ChannelListener existing, ChannelListener newListener) {
		super();
		add(existing);
		add(newListener);
	}

	@Override
	public void onChannelCreated(Channel channel) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelCreated(channel);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelCreated to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelDeleted(Channel channel) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelDeleted(channel);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelDeleted to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onJoinedChannel(Channel channel) {
		for (ChannelListener listener : this) {
			try {
				listener.onJoinedChannel(channel);
			} catch (Throwable t) {
				log.error("Error dispatching onJoinedChannel to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onLeftChannel(Channel channel) {
		for (ChannelListener listener : this) {
			try {
				listener.onLeftChannel(channel);
			} catch (Throwable t) {
				log.error("Error dispatching onLeftChannel to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelOwnershipTransferred(Channel channel, Id oldOwner, Id newOwner) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelOwnershipTransferred(channel, oldOwner, newOwner);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelOwnershipTransferred to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelSessionKeyRotated(Channel channel) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelSessionKeyRotated(channel);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelSessionKeyRotated to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelUpdated(Channel channel) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelUpdated(channel);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelUpdated to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelMemberJoined(Channel channel, Channel.Member member) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelMemberJoined(channel, member);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelMemberJoined to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelMemberLeft(Channel channel, Channel.Member member) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelMemberLeft(channel, member);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelMemberLeft to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelMembersRemoved(Channel channel, List<Channel.Member> members) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelMembersRemoved(channel, members);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelMembersRemoved to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelMembersBanned(Channel channel, List<Channel.Member> banned) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelMembersBanned(channel, banned);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelMembersBanned to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelMembersUnbanned(Channel channel, List<Channel.Member> unbanned) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelMembersUnbanned(channel, unbanned);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelMembersUnbanned to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onChannelMembersRoleChanged(Channel channel, List<Channel.Member> changed, Channel.Role role) {
		for (ChannelListener listener : this) {
			try {
				listener.onChannelMembersRoleChanged(channel, changed, role);
			} catch (Throwable t) {
				log.error("Error dispatching onChannelMembersRoleChanged to listener: {}", listener, t);
			}
		}
	}
}