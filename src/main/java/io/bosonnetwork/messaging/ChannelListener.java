package io.bosonnetwork.messaging;

import java.util.List;

import io.bosonnetwork.messaging.Channel.Member;
import io.bosonnetwork.messaging.Channel.Role;

public interface ChannelListener {
	// Myself joined a new channel
	// The channel object already include the member private key
	public void onJoinedChannel(Channel channel);

	public void onLeftChannel(Channel channel);

	// The channel was deleted by the owner
	public void onChannelDeleted(Channel channel);

	// The channel info was updated
	public void onChannelUpdated(Channel channel);

	// The channel members full list updated
	public void onChannelMembers(Channel channel, List<Member> members);

	// The channel has a new member joined.
	public void onChannelMemberJoined(Channel channel, Member member);

	// THe channel has a member left.
	public void onChannelMemberLeft(Channel channel, Member member);

	// THe channel has a member left.
	public void onChannelMembersRemoved(Channel channel, List<Member> members);

	// THe channel has a member left.
	public void onChannelMembersBanned(Channel channel, List<Member> banned);

	// THe channel has a member left.
	public void onChannelMembersUnbanned(Channel channel, List<Member> unbanned);

	public void onChannelMembersRoleChanged(Channel channel, List<Member> changed, Role role);
}
