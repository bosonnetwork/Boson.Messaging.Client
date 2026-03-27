package io.bosonnetwork.messaging;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;

public interface UserAgent
		extends ConnectionListener, ProfileListener, MessageListener, ChannelListener, ContactListener {
	public UserProfile getUser();

	public DeviceProfile getDevice();

	public MessagingPeerInfo getMessagingPeerInfo();

	public Map<String, Object> getProperties(String key) throws RepositoryException;

	public void putProperties(String key, Map<String, Object> properties) throws RepositoryException;

	public boolean isConfigured();

	public boolean addConnectionListener(ConnectionListener connectionListener);
	public boolean removeConnectionListener(ConnectionListener connectionListener);

	public boolean addProfileListener(ProfileListener profileListener);
	public boolean removeProfileListener(ProfileListener profileListener);

	public boolean addMessageListener(MessageListener messageListener);
	public boolean removeMessageListener(MessageListener messageListener);

	public boolean addChannelListener(ChannelListener channelListener);
	public boolean removeChannelListener(ChannelListener channelListener);

	public boolean addContactListener(ContactListener contactListener);
	public boolean removeContactListener(ContactListener contactListener);

	public List<Conversation> getConversations();

	public Conversation getConversation(Id conversationId);

	public default void removeConversation(Id conversationId) throws RepositoryException {
		removeConversations(Arrays.asList(conversationId));
	}

	public void removeConversations(Collection<Id> conversationIds) throws RepositoryException;

	public default List<Message> getMessages(Id conversationId) throws RepositoryException {
		return getMessages(conversationId, System.currentTimeMillis(), 100, 0);
	}

	public List<Message> getMessages(Id conversationId, long begin, long end) throws RepositoryException;

	public List<Message> getMessages(Id conversationId, long since, int limit, int offset) throws RepositoryException;

	public default void removeMessage(long messageId) throws RepositoryException {
		removeMessages(Arrays.asList(messageId));
	}

	public void removeMessages(Collection<Long> messageIds) throws RepositoryException;

	public void removeMessages(Id conversionId) throws RepositoryException;

	public List<Channel> getChannels() throws RepositoryException;

	public Channel getChannel(Id channel) throws RepositoryException;

	public String getContactsVersion() throws RepositoryException;

	public void putContactsUpdate(String versionId, Collection<AbstractContact> updated) throws RepositoryException;

	public List<AbstractContact> getContacts(List<Id> ids) throws RepositoryException;

	// !!! get all user contacts(!deleted && !auto)
	public List<AbstractContact> getUserContacts() throws RepositoryException;

	public AbstractContact getContact(Id contactId) throws RepositoryException;

	public default void removeContact(Id contactId) throws RepositoryException {
		removeContacts(Arrays.asList(contactId));
	}

	public void removeContacts(Collection<Id> contactIds) throws RepositoryException;

	public void removeUserContacts() throws RepositoryException;
}