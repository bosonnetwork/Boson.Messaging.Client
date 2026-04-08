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
import io.bosonnetwork.photonmessaging.FriendRequest;

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
	Future<Void> putMessage(MessageImpl<DefaultContent<?>> message);

	/**
	 * Updates the sent time of the specified message in the repository.
	 *
	 * @param message the message whose sent time needs to be updated
	 * @return a Future that completes when the message's sent time is updated
	 */
	Future<Void> updateMessageSentTime(MessageImpl<DefaultContent<?>> message);

	/**
	 * Retrieves messages for a specific conversation within a time range.
	 *
	 * @param conversationId the ID of the conversation
	 * @param begin the start timestamp (inclusive)
	 * @param end the end timestamp (exclusive)
	 * @return a Future with the list of messages found
	 */
	Future<List<MessageImpl<DefaultContent<?>>>> getMessages(Id conversationId, long begin, long end);

	/**
	 * Retrieves messages for a specific conversation with pagination.
	 *
	 * @param conversationId the ID of the conversation
	 * @param since the timestamp to start from
	 * @param limit the maximum number of messages to return
	 * @param offset the number of messages to skip
	 * @return a Future with the list of messages found
	 */
	Future<List<MessageImpl<DefaultContent<?>>>> getMessages(Id conversationId, long since, int limit, int offset);

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
	 * Removes multiple messages from the repository by their repository IDs.
	 *
	 * @param rids the collection of message rids to remove
	 * @return a Future that completes when the messages are removed
	 */
	Future<Boolean> removeMessages(Collection<Long> rids);

	/**
	 * Removes all messages associated with a specific conversation.
	 *
	 * @param conversationId the ID of the conversation
	 * @return a Future that completes when the messages are removed
	 */
	Future<Boolean> removeMessages(Id conversationId);

	/**
	 * Clears all messages from the system or designated storage.
	 *
	 * @return a Future representing the completion of the clear operation.
	 */
	Future<Void> clearMessages();

	/**
	 * Sends a friend request to the repository.
	 *
	 * @param friendRequest the friend request to send
	 * @return a Future that completes when the friend request is successfully stored
	 */
	Future<Void> putFriendRequest(FriendRequest friendRequest);

	/**
	 * Retrieves the friend request for the specified user ID.
	 *
	 * @param userId the unique identifier of the user whose friend request is to be retrieved
	 * @return a Future containing the friend request associated with the given user ID,
	 *         or a completed Future with no result if no request is found
	 */
	Future<FriendRequest> getFriendRequest(Id userId);

	/**
	 * Retrieves the list of friend requests for the current user.
	 *
	 * @return a Future containing a List of FriendRequest objects representing the pending friend requests.
	 */
	Future<List<FriendRequest>> getFriendRequests();

	/**
	 * Removes the friend request for a specific user.
	 *
	 * @param userId the unique identifier of the user from whom the friend request is to be removed
	 * @return a future containing a boolean value, where true indicates the friend request was successfully removed,
	 *         and false indicates the removal was unsuccessful
	 */
	Future<Boolean> removeFriendRequest(Id userId);

	/**
	 * Removes friend requests for the specified collection of user IDs.
	 *
	 * @param userIds A collection of user IDs whose friend requests should be removed.
	 * @return A Future containing a Boolean value. Returns true if the friend requests
	 *         were successfully removed, or false if the operation failed.
	 */
	Future<Boolean> removeFriendRequests(Collection<Id> userIds);

	/**
	 * Clears all pending friend requests for the current user.
	 * <p>
	 * This method removes all friend request entries from the system or database
	 * that are associated with the user. It ensures that there are no remaining
	 * pending friend requests to process.
	 *
	 * @return a Future representing the asynchronous operation, completing with
	 *         {@code null} when all friend requests have been successfully cleared.
	 */
	Future<Void> clearFriendRequests();

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
	Future<Boolean> removeConversation(Id conversationId);

	/**
	 * Removes multiple conversations and their associated data.
	 *
	 * @param conversationIds the collection of conversation IDs to remove
	 * @return a Future that completes when the conversations are removed
	 */
	Future<Boolean> removeConversations(Collection<Id> conversationIds);

	/**
	 * Stores the given contact in the local storage asynchronously.
	 * If the contact already exists, it is updated. Otherwise, it is added.
	 * <p>
	 * This method will not affect the contactsRevision.
	 *
	 * @param contact the contact object to be stored locally
	 * @return a Future representing the pending completion of the operation
	 */
	Future<Void> putContactLocally(Contact contact);

	/**
	 * Removes a contact from the local storage based on the provided contact ID.
	 * <p>
	 * This method will not affect the contactsRevision.
	 *
	 * @param contactId The unique identifier of the contact to be removed.
	 * @return A Future representing the result of the operation, which resolves to
	 *         true if the contact was successfully removed, or false otherwise.
	 */
	Future<Boolean> removeContactLocally(Id contactId);

	/**
	 * Returns the current local revision of the contact list.
	 *
	 * @return a Future with the current revision number
	 */
	Future<Integer> getContactsRevision();

	/**
	 * Adds or updates contact and updates the local contacts revision atomically.
	 *
	 * @param revision the revision number of the contact being updated or inserted
	 * @param contact the {@code Contact} object containing the details of the contact
	 * @return a {@code Future<Void>} indicating the completion of the operation
	 */
	Future<Void> putContact(int revision, Contact contact);

	/**
	 * Adds or updates contacts and updates the local contacts revision atomically.
	 *
	 * @param revision the new revision number
	 * @param updated the collection of contacts to add or update
	 * @return a Future that completes when the update is finished
	 */
	Future<Void> putContacts(int revision, Collection<Contact> updated);

	/**
	 * Removes a contact and updates the local contacts revision atomically.
	 *
	 * @param revision  the new revision number
	 * @param contactId the unique identifier of the contact to be removed
	 * @return a Future containing a Boolean indicating whether the contact was successfully removed
	 */
	Future<Boolean> removeContacts(int revision, Id contactId);

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
	Future<Void> clearContacts(int revision);

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
	 * @param contactIds the collection of contact IDs
	 * @return a Future with the list of found contacts
	 */
	Future<List<Contact>> getContacts(Collection<Id> contactIds);

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

	/**
	 * Updates the ownership of a specified channel by transferring it from the current owner to a new owner.
	 * <p>
	 * The implementation should ensure make the following changes atomically:
	 * <ul>
	 *   <li>update the channel owner column</li>
	 *   <li>update the older owner's role to Channel.Role.MEMBER</li>
	 *   <li>update the new owner's role to Channel.Role.OWNER</li>
	 * </ul
	 *
	 * @param channelId   the unique identifier of the channel whose ownership is being updated
	 * @param oldOwnerId  the unique identifier of the current owner of the channel
	 * @param newOwnerId  the unique identifier of the new owner to whom the ownership is being transferred
	 * @return a Future representing the result of the ownership update operation; completes with void if successful
	 */
	Future<Void> updateChannelOwnership(Id channelId, Id oldOwnerId, Id newOwnerId);

	/**
	 * Adds or updates a single member in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param member the member to add or update
	 * @return a Future that completes when the operation is finished
	 */
	default Future<Void> putChannelMember(Id channelId, Channel.Member member) {
		return putChannelMembers(channelId, List.of(member));
	}

	/**
	 * Adds or updates multiple members in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param members the collection of members to add or update
	 * @return a Future that completes when the operation is finished
	 */
	Future<Void> putChannelMembers(Id channelId, Collection<Channel.Member> members);

	/**
	 * Replaces all members in a channel with the provided collection.
	 *
	 * @param channelId the ID of the channel
	 * @param members the new collection of members
	 * @return a Future that completes when the operation is finished
	 */
	Future<Void> refillChannelMembers(Id channelId, Collection<Channel.Member> members);

	/**
	 * Retrieves a specific member of a channel based on the provided channel and member identifiers.
	 *
	 * @param channelId the unique identifier of the channel
	 * @param memberId the unique identifier of the member within the channel
	 * @return a Future representing the asynchronous operation, which resolves to the Channel.Member object if found
	 */
	Future<Channel.Member> getChannelMember(Id channelId, Id memberId);

	/**
	 * Retrieves the list of members for a specific channel based on the provided channel ID and an optional list of member IDs.
	 *
	 * @param channelId The unique identifier of the channel whose members are to be fetched.
	 * @param memberIds A collection of unique identifiers for specific members. If provided, only members matching these IDs will be retrieved.
	 * @return A future representing the asynchronous computation of a list of members in the specified channel, filtered by the provided member IDs if applicable.
	 */
	Future<List<Channel.Member>> getChannelMembers(Id channelId, Collection<Id> memberIds);

	/**
	 * Retrieves the list of all members for a specific channel.
	 *
	 * @param channelId The unique identifier of the channel whose members are to be fetched.
	 * @return A future representing the asynchronous computation of a list of all members in the specified channel.
	 */
	Future<List<Channel.Member>> getAllChannelMembers(Id channelId);

	/**
	 * Sets the role for multiple members in a channel.
	 *
	 * @param channelId the ID of the channel
	 * @param memberIds the collection of member IDs
	 * @param role the new role to assign to all specified members
	 * @return a Future that completes when the roles are updated
	 */
	Future<Boolean> updateChannelMembersRole(Id channelId, Collection<Id> memberIds, Channel.Role role);

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