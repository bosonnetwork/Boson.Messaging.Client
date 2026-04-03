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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;

public interface MessagingClient {
	static int DEFAULT_MESSAGES_LIMIT = 100;

	Id getUserId();

	Id getDeviceId();

	void addConnectionListener(ConnectionListener listener);
	void removeConnectionListener(ConnectionListener listener);

	void addMessageListener(MessageListener listener);
	void removeMessageListener(MessageListener listener);

	void addChannelListener(ChannelListener listener);
	void removeChannelListener(ChannelListener listener);

	void addContactListener(ContactListener listener);
	void removeContactListener(ContactListener listener);

	CompletableFuture<Void> start();

	// The MessagingClient will release all resources after stopped.
	// It cannot be used anymore.
	CompletableFuture<Void> stop();

	boolean isRunning();

	boolean isConnected();

	// Message and conversation APIs

	// Message send helper, builder pattern is better than long verbose parameters.
	default Message.Builder message() {
		return message(null);
	}

	Message.Builder message(Id recipient);

	CompletableFuture<Conversation> getConversation(Id conversationId);

	CompletableFuture<List<Conversation>> getConversations();

	default CompletableFuture<Boolean> removeConversation(Id conversationId) {
		return removeConversations(List.of(conversationId));
	}

	CompletableFuture<Boolean> removeConversations(Collection<Id> conversationIds);

	default CompletableFuture<List<Message>> getMessages(Id conversationId) {
		return getMessages(conversationId, System.currentTimeMillis(), DEFAULT_MESSAGES_LIMIT, 0);
	}

	CompletableFuture<List<Message>> getMessages(Id conversationId, long since, int limit, int offset);

	CompletableFuture<List<Message>> getMessages(Id conversationId, long begin, long end);

	default CompletableFuture<Boolean> removeMessage(long messageId) {
		return removeMessages(List.of(messageId));
	}

	CompletableFuture<Boolean> removeMessages(Collection<Long> messageIds);

	CompletableFuture<Boolean> removeMessages(Id conversionId);

	// Session APIs
	CompletableFuture<List<SessionInfo>> getSessions();

	CompletableFuture<Boolean> revokeSession(Id deviceId);

	// Friend APIs
	CompletableFuture<Void> friendRequest(Id id, String hello);

	default CompletableFuture<Contact> addFriend(Id id, byte[] sessionKey) {
		return addFriend(id, sessionKey, null);
	}

	CompletableFuture<Contact> addFriend(Id id, byte[] sessionKey, String remark);

	// channel APIs
	default CompletableFuture<Channel> createChannel(String name) {
		return createChannel(Channel.Permission.OWNER_INVITE, name, null, false);
	}

	default CompletableFuture<Channel> createChannel(String name, String notice) {
		return createChannel(Channel.Permission.OWNER_INVITE, name, notice, false);
	}

	CompletableFuture<Channel> createChannel(Channel.Permission permission, String name, String notice, boolean announce);

	CompletableFuture<Boolean> removeChannel(Id channelId);

	CompletableFuture<Channel> joinChannel(InviteTicket ticket);
	CompletableFuture<Boolean> leaveChannel(Id channelId);

	CompletableFuture<InviteTicket> createInviteTicket(Id channelId);
	CompletableFuture<InviteTicket> createInviteTicket(Id channelId, Id invitee);

	CompletableFuture<Boolean> transferChannelOwnership(Id channelId, Id newOwner);

	default CompletableFuture<Boolean> rotateChannelSessionKey(Id channelId) {
		return rotateChannelSessionKey(channelId, Signature.KeyPair.random());
	}

	CompletableFuture<Boolean> rotateChannelSessionKey(Id channelId, Signature.KeyPair sessionKey);

	CompletableFuture<Boolean> updateChannelInfo(Channel channel);

	CompletableFuture<Boolean> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role);
	CompletableFuture<Boolean> banChannelMembers(Id channelId, List<Id> members);
	CompletableFuture<Boolean> unbanChannelMembers(Id channelId, List<Id> members);
	CompletableFuture<Boolean> removeChannelMembers(Id channelId, List<Id> members);

	// Generic contact APIs
	CompletableFuture<Contact> getContact(Id id);

	CompletableFuture<List<Contact>> getContacts();

	CompletableFuture<Boolean> updateContact(Contact contact);

	default CompletableFuture<Boolean> removeContact(Id id) {
		return removeContacts(List.of(id));
	}

	CompletableFuture<Boolean> removeContacts(List<Id> ids);

	CompletableFuture<Boolean> clearContacts();
}