package io.bosonnetwork.messaging;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.impl.ClientBuilderImpl;

public interface MessagingClient {
	public Id getUserId();

	public UserAgent getUserAgent();

	public CompletableFuture<Void> connect();

	public CompletableFuture<Void> disconnect();

	public boolean isConnected();

	// The MessagingClient will releases all resources after close.
	// It cannot be used anymore.
	public CompletableFuture<Void> close();

	public CompletableFuture<List<ClientDevice>> listDevices();
	public CompletableFuture<Boolean> revokeDevice(Id deviceId);

	public default CompletableFuture<Channel> createChannel(String name) {
		return createChannel(Channel.Permission.OWNER_INVITE, name, null);
	}

	public default CompletableFuture<Channel> createChannel(String name, String notice) {
		return createChannel(Channel.Permission.OWNER_INVITE, name, notice);
	}

	public CompletableFuture<Channel> createChannel(Channel.Permission permission, String name, String notice);
	public CompletableFuture<Boolean> removeChannel(Id channeId);
	public CompletableFuture<Channel> joinChannel(InviteTicket ticket, byte[] memberPrivateKey);
	public CompletableFuture<Boolean> leaveChannel(Id channeId);

	public CompletableFuture<Boolean> setChannelOwner(Id channelId, Id newOwner);
	public CompletableFuture<Boolean> setChannelPermission(Id channelId, Channel.Permission permission);
	public CompletableFuture<Boolean> setChannelName(Id channelId, String name);
	public CompletableFuture<Boolean> setChannelNotice(Id channelId, String notice);

	public CompletableFuture<Boolean> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role);
	public CompletableFuture<Boolean> banChannelMembers(Id channelId, List<Id> members);
	public CompletableFuture<Boolean> unbanChannelMembers(Id channelId, List<Id> members);
	public CompletableFuture<Boolean> removeChannelMembers(Id channelId, List<Id> members);

	public CompletableFuture<Void> syncContact();

	public Message.Builder message();

	public static ClientBuilder builder() {
		return new ClientBuilderImpl();
	}
}
