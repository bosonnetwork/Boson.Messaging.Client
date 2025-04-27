package io.bosonnetwork.messaging;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel.Member;
import io.bosonnetwork.messaging.Channel.Role;
import io.bosonnetwork.utils.Json;

public interface MessagingRepository {
	// Configuration ///////////////////////////////////////////////////////////

	public void putConfig(String key, byte[] value) throws RepositoryException;

	public byte[] getConfig(String key) throws RepositoryException;

	public default void putConfig(String key, Object value) throws RepositoryException {
		putConfig(key, value == null ? null : Json.toBytes(value));
	}

	public default <T> T getConfig(String key, Class<T> type) throws RepositoryException {
		byte[] config = getConfig(key);
		return config != null ? Json.parse(config, type) : null;
	}

	public default <T> T getConfig(String key, TypeReference<T> type) throws RepositoryException {
		byte[] config = getConfig(key);
		return config != null ? Json.parse(config, type) : null;
	}

	// Messages ////////////////////////////////////////////////////////////////

	public default void putMessage(Message message) throws RepositoryException {
		putMessages(Arrays.asList(message));
	}

	public void putMessages(Collection<Message> messages) throws RepositoryException;

	public void updateMessageCompletetdTimestamp(Message message) throws RepositoryException;

	public default void removeMessage(long rid) throws RepositoryException {
		removeMessages(Arrays.asList(rid));
	}

	public void removeMessages(Collection<Long> rids) throws RepositoryException;

	public void removeMessages(Id conversationId) throws RepositoryException;

	public List<Message> getMessages(Id conversationId, long begin, long end) throws RepositoryException;

	public List<Message> getMessages(Id conversationId, long since, int limit, int offset) throws RepositoryException;

	// Conversations ///////////////////////////////////////////////////////////

	public Conversation getConversaion(Id conversationId) throws RepositoryException;

	public List<Conversation> getAllConversaions() throws RepositoryException;

	public default void removeConversation(Id conversationId) throws RepositoryException {
		removeConversations(Arrays.asList(conversationId));
	}

	public void removeConversations(Collection<Id> conversationIds) throws RepositoryException;

	// Contacts ////////////////////////////////////////////////////////////////

	public String getContactsVersion() throws RepositoryException;

	public void putContactsUpdate(String versionId, Collection<Contact> updated) throws RepositoryException;

	public default void putContact(Contact contact) throws RepositoryException {
		putContacts(Arrays.asList(contact));

	}

	public void putContacts(Collection<Contact> contacts) throws RepositoryException;

	public Contact getContact(Id contactId) throws RepositoryException;

	public List<Contact> getContacts(List<Id> contactIds) throws RepositoryException;

	public List<Contact> getAllContacts() throws RepositoryException;

	public List<Contact> getAllUserContacts() throws RepositoryException;

	public List<Contact> getAllContacts(int type) throws RepositoryException;

	public default boolean existsContact(Id contactId) throws RepositoryException {
		return getContact(contactId) != null;
	}

	public default void removeContact(Id contactId) throws RepositoryException {
		removeContacts(Arrays.asList(contactId));
	}

	public void removeContacts(Collection<Id> contactIds) throws RepositoryException;

	public void removeAllContacts() throws RepositoryException;

	public void removeAllUserContacts() throws RepositoryException;

	// Channels ////////////////////////////////////////////////////////////////

	public default Channel getChannel(Id channelId) throws RepositoryException {
		Contact contact = getContact(channelId);
		if (contact != null && contact instanceof Channel ch)
			return ch;
		else
			return null;
	}

	public default List<Channel> getAllChannels() throws RepositoryException {
		List<Contact> contacts = getAllContacts(Contact.Types.CHANNEL);
		return contacts.stream().map(c -> (Channel) c).toList();
	}

	public default void putChannelMember(Channel channel, Member member) throws RepositoryException {
		putChannelMembers(channel, Arrays.asList(member));
	}

	public void putChannelMembers(Channel channel, Collection<Member> members) throws RepositoryException;

	public void refillChannelMembers(Channel channel, Collection<Member> members) throws RepositoryException;

	public default void removeChannelMember(Channel channel, Id memberId) throws RepositoryException {
		removeChannelMembers(channel, Arrays.asList(memberId));
	}

	public void removeChannelMembers(Channel channel, Collection<Id> memberIds) throws RepositoryException;

	public void removeAllChannelMembers(Channel channel) throws RepositoryException;

	public default void setChannelMemberRole(Channel channel, Id memberId, Role role) throws RepositoryException {
		setChannelMembersRole(channel, Arrays.asList(memberId), role);
	}

	public void setChannelMembersRole(Channel channel, List<Id> memberIds, Role role) throws RepositoryException;
}
