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

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.vertx.core.Future;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.Conversation;
import io.bosonnetwork.photonmessaging.Message;

/**
 * Interface for a persistent storage repository of messaging data, including messages,
 * conversations, contacts, and channel members.
 */
interface MessagingRepository {
	/**
	 * Saves a single message to the repository.
	 *
	 * @param message the message to save
	 * @return a Future that completes when the message is saved
	 */
	default Future<Void> putMessage(Message message) {
		return putMessages(List.of(message));
	}

	/**
	 * Saves a collection of messages to the repository.
	 *
	 * @param messages the messages to save
	 * @return a Future that completes when the messages are saved
	 */
	Future<Void> putMessages(Collection<Message> messages);

	/**
	 * Retrieves messages for a specific conversation within a time range.
	 *
	 * @param conversationId the ID of the conversation
	 * @param begin the start timestamp (inclusive)
	 * @param end the end timestamp (exclusive)
	 * @return a Future with the list of messages found
	 */
	Future<List<Message>> getMessages(Id conversationId, long begin, long end);

	/**
	 * Retrieves messages for a specific conversation with pagination.
	 *
	 * @param conversationId the ID of the conversation
	 * @param since the timestamp to start from
	 * @param limit the maximum number of messages to return
	 * @param offset the number of messages to skip
	 * @return a Future with the list of messages found
	 */
	Future<List<Message>> getMessages(Id conversationId, long since, int limit, int offset);

	/**
	 * Removes a single message from the repository by its ID.
	 *
	 * @param id the unique ID of the message
	 * @return a Future that completes when the message is removed
	 */
	default Future<Boolean> removeMessage(long id) {
		return removeMessages(List.of(id));
	}

	/**
	 * Removes multiple messages from the repository by their IDs.
	 *
	 * @param ids the collection of message IDs to remove
	 * @return a Future that completes when the messages are removed
	 */
	Future<Boolean> removeMessages(Collection<Long> ids);

	/**
	 * Removes all messages associated with a specific conversation.
	 *
	 * @param conversationId the ID of the conversation
	 * @return a Future that completes when the messages are removed
	 */
	Future<Boolean> removeMessages(Id conversationId);

	/**
	 * Retrieves a conversation by its ID.
	 *
	 * @param conversationId the ID of the conversation
	 * @return a Future with the conversation, or null if not found
	 */
	Future<Conversation> getConversation(Id conversationId);

	/**
	 * Retrieves all conversations from the repository.
	 *
	 * @return a Future with the list of all conversations
	 */
	Future<List<Conversation>> getAllConversations();

	/**
	 * Removes a single conversation and its associated data.
	 *
	 * @param conversationId the ID of the conversation to remove
	 * @return a Future that completes when the conversation is removed
	 */
	default Future<Boolean> removeConversation(Id conversationId) {
		return removeConversations(List.of(conversationId));
	}

	/**
	 * Removes multiple conversations and their associated data.
	 *
	 * @param conversationIds the collection of conversation IDs to remove
	 * @return a Future that completes when the conversations are removed
	 */
	Future<Boolean> removeConversations(Collection<Id> conversationIds);

	Future<Boolean> putContactLocally(Contact contact);

	Future<Boolean> removeContactLocally(Id contactId);

	/**
	 * Returns the current local revision of the contact list.
	 *
	 * @return a Future with the current revision number
	 */
	Future<Integer> getContactsRevision();

	/**
	 * Adds or updates contacts and updates the local contacts revision atomically.
	 *
	 * @param revision the new revision number
	 * @param updated the collection of contacts to add or update
	 * @return a Future that completes when the update is finished
	 */
	Future<Boolean> putContacts(int revision, Collection<Contact> updated);

	/**
	 * Removes contacts and updates the local contacts revision atomically.
	 *
	 * @param revision the new revision number
	 * @param contactIds the IDs of the contacts to remove
	 * @return a Future that completes when the removal is finished
	 */
	Future<Boolean> removeContacts(int revision, Collection<Id> contactIds);

	/**
	 * Clears all contacts and updates the local contacts revision atomically.
	 *
	 * @param revision the new revision number
	 * @return a Future that completes when the operation is finished
	 */
	Future<Boolean> clearContacts(int revision);

	/**
	 * Retrieves a contact by its ID.
	 *
	 * @param contactId the ID of the contact
	 * @return a Future with the contact, or null if not found
	 */
	Future<Contact> getContact(Id contactId);

	/**
	 * Retrieves multiple contacts by their IDs.
	 *
	 * @param contactIds the list of contact IDs
	 * @return a Future with the list of found contacts
	 */
	Future<List<Contact>> getContacts(List<Id> contactIds);

	/**
	 * Retrieves all contacts from the repository.
	 *
	 * @return a Future with the list of all contacts
	 */
	Future<List<Contact>> getAllContacts();

	/**
	 * Checks if a contact exists in the repository.
	 *
	 * @param contactId the ID of the contact
	 * @return a Future returning true if the contact exists, false otherwise
	 */
	default Future<Boolean> existsContact(Id contactId) {
		return getContact(contactId).map(Objects::nonNull);
	}

	Future<Boolean> updateChannelOwnership(Id channelId, Id oldOwnerId, Id newOwnerId);

	/**
	 * Adds or updates a single member in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param member the member to add or update
	 * @return a Future that completes when the operation is finished
	 */
	default Future<Boolean> putChannelMember(Id channelId, Channel.Member member) {
		return putChannelMembers(channelId, List.of(member));
	}

	/**
	 * Adds or updates multiple members in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param members the collection of members to add or update
	 * @return a Future that completes when the operation is finished
	 */
	Future<Boolean> putChannelMembers(Id channelId, Collection<Channel.Member> members);

	/**
	 * Replaces all members in a channel with the provided collection.
	 *
	 * @param channelId the ID of the channel
	 * @param members the new collection of members
	 * @return a Future that completes when the operation is finished
	 */
	Future<Boolean> refillChannelMembers(Id channelId, Collection<Channel.Member> members);

	default Future<Channel.Member> getChannelMember(Id channelId, Id memberId) {
		return getChannelMembers(channelId, List.of(memberId)).map(list -> list.isEmpty() ? null : list.get(0));
	}

	Future<List<Channel.Member>> getChannelMembers(Id channelId, List<Id> memberId);

	/**
	 * Sets the role of a single member in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param memberId the ID of the member
	 * @param role the new role to assign
	 * @return a Future that completes when the role is updated
	 */
	default Future<Boolean> updateChannelMemberRole(Id channelId, Id memberId, Channel.Role role) {
		return updateChannelMembersRole(channelId, List.of(memberId), role);
	}

	/**
	 * Sets the role for multiple members in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param memberIds the list of member IDs
	 * @param role the new role to assign to all specified members
	 * @return a Future that completes when the roles are updated
	 */
	Future<Boolean> updateChannelMembersRole(Id channelId, List<Id> memberIds, Channel.Role role);

	/**
	 * Removes a single member from a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param memberId the ID of the member to remove
	 * @return a Future that completes when the member is removed
	 */
	default Future<Boolean> removeChannelMember(Id channelId, Id memberId) {
		return removeChannelMembers(channelId, List.of(memberId));
	}

	/**
	 * Removes multiple members from a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param memberIds the collection of member IDs to remove
	 * @return a Future that completes when the members are removed
	 */
	Future<Boolean> removeChannelMembers(Id channelId, Collection<Id> memberIds);
}