package io.bosonnetwork.messaging.impl;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Channel.Member;
import io.bosonnetwork.messaging.Channel.Role;
import io.bosonnetwork.messaging.ChannelListener;
import io.bosonnetwork.messaging.ConnectionListener;
import io.bosonnetwork.messaging.Contact;
import io.bosonnetwork.messaging.ContactListener;
import io.bosonnetwork.messaging.Conversation;
import io.bosonnetwork.messaging.DeviceProfile;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.MessageListener;
import io.bosonnetwork.messaging.MessagingPeerInfo;
import io.bosonnetwork.messaging.MessagingRepository;
import io.bosonnetwork.messaging.RepositoryException;
import io.bosonnetwork.messaging.UserAgent;
import io.bosonnetwork.messaging.UserProfile;
import io.bosonnetwork.utils.Json;
import io.vertx.core.Vertx;

public class DefaultUserAgent implements UserAgent {
	private final Vertx vertx;

	private UserProfileImpl user;
	private DeviceProfileImpl device;
	private MessagingPeerInfo peer;

	private MessagingRepository repository;

	private List<ConnectionListener> connectionListeners;
	private List<MessageListener> messageListeners;
	private List<ChannelListener> channelListeners;
	private List<ContactListener> contactListeners;

	private ConcurrentMap<Id, ConversationImpl> conversations;

	private boolean hardened;

	private static final Logger log = LoggerFactory.getLogger(DefaultUserAgent.class);

	protected DefaultUserAgent(Vertx vertx) {
		Objects.requireNonNull(vertx, "vertx");
		this.vertx = vertx;

		connectionListeners = Collections.emptyList();
		messageListeners = Collections.emptyList();
		channelListeners = Collections.emptyList();
		contactListeners = Collections.emptyList();

		conversations = new ConcurrentHashMap<>();

		hardened = false;
	}

	protected void harden() {
		hardened = true;
	}

	protected boolean isHardened() {
		return hardened;
	}

	protected void setUser(CryptoIdentity user, String name) {
		Objects.requireNonNull(user, "user");

		if (isHardened())
			throw new IllegalStateException("UserAgent is hardened");

		this.user = new UserProfileImpl(user, name);
		updateUserInfoConfig();
	}

	@Override
	public UserProfile getUser() {
		return user;
	}

	private boolean isMe(Id id) {
		return user.getId().equals(id);
	}

	protected void setDevice(Identity device, String name, String app) {
		Objects.requireNonNull(device, "device");

		if (isHardened())
			throw new IllegalStateException("UserAgent is hardened");

		this.device = new DeviceProfileImpl(device, name, app);

		updateDeviceInfoConfig();
	}

	@Override
	public DeviceProfile getDevice() {
		return device;
	}

	@Override
	public MessagingPeerInfo getMessagingPeerInfo() {
		return peer;
	}

	protected void setMessagingPeerInfo(MessagingPeerInfo peer) {
		Objects.requireNonNull(peer, "peer");
		if (peer.getPeerId() == null && peer.getApiURL() == null)
			throw new IllegalArgumentException("peerId or apiURL must be set");

		if (isHardened())
			throw new IllegalStateException("UserAgent is hardened");

		this.peer = peer;

		updateMessagingPeerInfo();
	}

	protected void updateMessagingPeerInfo(MessagingPeerInfo peer) {
		Objects.requireNonNull(peer, "peer");
		if (peer.getPeerId() == null && peer.getApiURL() == null)
			throw new IllegalArgumentException("peerId or apiURL must be set");

		if (this.peer.getPeerId() != null && !Objects.equals(this.peer.getPeerId(), peer.getPeerId()))
			throw new IllegalArgumentException("peerId not matched");

		if (this.peer.getApiURL() != null && !Objects.equals(this.peer.getApiURL(), peer.getApiURL()))
			throw new IllegalArgumentException("apiURL not matched");

		/*
		if (isHardened())
			throw new IllegalStateException("UserAgent is hardened");
		*/

		MessagingPeerInfo old = this.peer;
		this.peer = peer;

		if (!peer.equals(old))
			updateMessagingPeerInfo();
	}

	@Override
	public Map<String, Object> getProperties(String key) throws RepositoryException {
		Objects.requireNonNull(key);

		if (key.startsWith("."))
			throw new IllegalArgumentException("invalid key");

		return repository.getConfig(key, Json.MAP_TYPE);
	}

	@Override
	public void putProperties(String key, Map<String, Object> properties) throws RepositoryException {
		Objects.requireNonNull(key);

		if (key.startsWith("."))
			throw new IllegalArgumentException("invalid key");

		repository.putConfig(key, properties);
	}

	protected void setMessagingRepository(MessagingRepository repository) throws RepositoryException {
		Objects.requireNonNull(repository, "repository");

		if (isHardened())
			throw new IllegalStateException("UserAgent is hardened");

		this.repository = repository;

		loadConfig();

		// try to load the existing conversations
		conversations.clear();
		repository.getAllConversaions().forEach((c) -> conversations.put(c.getId(), (ConversationImpl)c));
	}

	protected MessagingRepository getRepository() {
		if (repository == null)
			throw new IllegalStateException("repository not configured");

		return repository;
	}

	private void updateUserInfoConfig() {
		if (repository == null)
			throw new IllegalStateException("messaging repository not configured");

		Map<String, Object> config = new HashMap<>();
		config.put("privateKey", user.getIdentity().getKeyPair().privateKey().bytes());
		config.put("name", user.getName());
		config.put("avatar", user.hasAvatar());

		try {
			repository.putConfig(".user", config);
		} catch (RepositoryException e) {
			log.error("Save user profile failed: ", e.getMessage(), e);
		}
	}

	private void updateDeviceInfoConfig() {
		if (repository == null)
			throw new IllegalStateException("messaging repository not configured");

		byte[] key = device.getIdentity() instanceof CryptoIdentity id ?
				id.getKeyPair().privateKey().bytes() : null;

		Map<String, Object> config = new HashMap<>();
		config.put("privateKey", key);
		config.put("name", device.getName());
		config.put("app", device.getAppName());

		try {
			repository.putConfig(".device", config);
		} catch (RepositoryException e) {
			log.error("Save device profile failed: ", e.getMessage(), e);
		}
	}

	private void updateMessagingPeerInfo() {
		if (repository == null)
			throw new IllegalStateException("messaging repository not configured");

		Map<String, Object> config = new HashMap<>();
		config.put("peerId", peer.getPeerId().bytes());
		config.put("nodeId", peer.getNodeId().bytes());
		config.put("apiURL", peer.getApiURL().toString());

		try {
			repository.putConfig(".peer", config);
		} catch (RepositoryException e) {
			log.error("Save messaging peer info failed: ", e.getMessage(), e);
		}
	}

	private void loadConfig() {
		if (repository == null)
			throw new IllegalStateException("messaging repository not configured");

		try {
			Map<String, Object> userInfo = repository.getConfig(".user", Json.MAP_TYPE);
			if (userInfo != null && !userInfo.isEmpty()) {
				byte[] privateKey = (byte[])userInfo.get("privateKey");
				CryptoIdentity id = new CryptoIdentity(privateKey);
				String name = (String)userInfo.get("name");
				boolean avatar = (Boolean)userInfo.get("avatar");
				this.user = new UserProfileImpl(id, name, avatar);
			}
		} catch (Exception e) {
			log.error("Load user profile failed: {}", e.getMessage(), e);
			throw new IllegalStateException("config: invalid user profile", e);
		}

		try {
			Map<String, Object> deviceInfo = repository.getConfig(".device", Json.MAP_TYPE);
			if (deviceInfo != null && !deviceInfo.isEmpty()) {
				byte[] privateKey = (byte[])deviceInfo.get("privateKey");
				CryptoIdentity id = privateKey == null ? null : new CryptoIdentity(privateKey);
				String name = (String)deviceInfo.get("name");
				String app = (String)deviceInfo.get("app");
				this.device = new DeviceProfileImpl(id, name, app);
			}
		} catch (Exception e) {
			log.error("Load device profile failed: {}", e.getMessage(), e);
			throw new IllegalStateException("config: invalid device profile", e);
		}

		try {
			Map<String, Object> peerInfo = repository.getConfig(".peer", Json.MAP_TYPE);
			if (peerInfo != null && !peerInfo.isEmpty()) {
				byte[] id = (byte[])peerInfo.get("peerId");
				Id peerId = Id.of(id);
				id = (byte[])peerInfo.get("nodeId");
				Id nodeId = Id.of(id);
				String url = (String)peerInfo.get("apiURL");
				URL	apiURL = new URL(url);

				this.peer = MessagingPeerInfo.of(peerId, nodeId, apiURL);
			}
		} catch (Exception e) {
			log.error("Load messaging peer info failed: {}", e.getMessage(), e);
			throw new IllegalStateException("config: invalid messaging peer info", e);
		}

		/*
		try {
			Map<String, Object> apiClientInfo = repository.getConfig(".api", Json.MAP_TYPE);
			if (apiClientInfo != null && !apiClientInfo.isEmpty()) {
				this.accessToken = (String)apiClientInfo.get("accessToken");
			}
		} catch (Exception e) {
			log.error("Load API client config failed: {}", e.getMessage(), e);
			throw new IllegalStateException("config: invalid API client config", e);
		}
		*/
	}

	@Override
	public boolean isConfigured() {
		return user != null && device != null && peer != null && repository != null &&
				peer.getPeerId() != null && peer.getNodeId() != null && peer.getApiURL() != null;
	}


	// Not thread-safe
	// Modify the listeners using copy-on-write to ensure safe modification and notification of listeners.
	// The consumer will be called with the new list of listeners only if the list has changed.
	private static <L> boolean addListener(List<L> listeners, L listener, Consumer<List<L>> consumer) {
		if (listeners.contains(listener))
			return false; // no change

		List<L> newListeners = new ArrayList<>(listeners.size() + 1);
		newListeners.addAll(listeners);
		newListeners.add(listener);
		consumer.accept(newListeners);
		return true;
	}

	private static <L> boolean removeListener(List<L> listeners, L listener, Consumer<List<L>> consumer) {
		int index = listeners.indexOf(listener);
		if (index < 0)
			return false; // no change

		List<L> newListeners;
		if (listeners.size() == 1) {
			newListeners = Collections.emptyList();
		} else {
			newListeners = new ArrayList<>(listeners.size() - 1);
			newListeners.addAll(listeners.subList(0, index));
			newListeners.addAll(listeners.subList(index + 1, listeners.size()));
		}

		consumer.accept(newListeners);
		return true;
	}

	@Override
	public boolean addConnectionListener(ConnectionListener connectionListener) {
		Objects.requireNonNull(connectionListener, "connectionListener");
		return addListener(connectionListeners, connectionListener, (newListeners) -> {
			this.connectionListeners = newListeners;
		});
	}

	@Override
	public boolean removeConnectionListener(ConnectionListener connectionListener) {
		Objects.requireNonNull(connectionListener, "connectionListener");
		return removeListener(connectionListeners, connectionListener, (newListeners) -> {
			this.connectionListeners = newListeners;
		});
	}

	protected void setConnectionListeners(List<ConnectionListener> listeners) {
		if (listeners == null || listeners.isEmpty())
			this.connectionListeners = Collections.emptyList();
		else
			this.connectionListeners = new ArrayList<>(listeners);
	}

	@Override
	public boolean addMessageListener(MessageListener messageListener) {
		Objects.requireNonNull(messageListener, "messageListener");
		return addListener(messageListeners, messageListener, (newListeners) -> {
			this.messageListeners = newListeners;
		});
	}

	@Override
	public boolean removeMessageListener(MessageListener messageListener) {
		Objects.requireNonNull(messageListener, "messageListener");
		return removeListener(messageListeners, messageListener, (newListeners) -> {
			this.messageListeners = newListeners;
		});
	}

	protected void setMessageListeners(List<MessageListener> listeners) {
		if (listeners == null || listeners.isEmpty())
			this.messageListeners = Collections.emptyList();
		else
			this.messageListeners = new ArrayList<>(listeners);
	}

	@Override
	public boolean addChannelListener(ChannelListener channelListener) {
		Objects.requireNonNull(channelListener, "channelListener");
		return addListener(channelListeners, channelListener, (newListeners) -> {
			this.channelListeners = newListeners;
		});
	}

	@Override
	public boolean removeChannelListener(ChannelListener channelListener) {
		Objects.requireNonNull(channelListener, "channelListener");
		return removeListener(channelListeners, channelListener, (newListeners) -> {
			this.channelListeners = newListeners;
		});
	}

	protected void setChannelListeners(List<ChannelListener> listeners) {
		if (listeners == null || listeners.isEmpty())
			this.channelListeners = Collections.emptyList();
		else
			this.channelListeners = new ArrayList<>(listeners);
	}

	@Override
	public boolean addContactListener(ContactListener contactListener) {
		Objects.requireNonNull(contactListener, "contactListener");
		return addListener(contactListeners, contactListener, (newListeners) -> {
			this.contactListeners = newListeners;
		});
	}

	@Override
	public boolean removeContactListener(ContactListener contactListener) {
		Objects.requireNonNull(contactListener, "contactListener");
		return removeListener(contactListeners, contactListener, (newListeners) -> {
			this.contactListeners = newListeners;
		});
	}

	protected void setContactListeners(List<ContactListener> listeners) {
		if (listeners == null || listeners.isEmpty())
			this.contactListeners = Collections.emptyList();
		else
			this.contactListeners = new ArrayList<>(listeners);
	}

	////////////////////////////////////////////////////////////////////////////
	// ConnectionListener

	@Override
	public void onConnecting() {
		connectionListeners.forEach(l -> l.onConnecting());
	}

	@Override
	public void onConnected() {
		connectionListeners.forEach(l -> l.onConnected());
	}

	@Override
	public void onDisconnected() {
		connectionListeners.forEach(l -> l.onDisconnected());
	}

	////////////////////////////////////////////////////////////////////////////
	// Profile
	@Override
	public void onUserProfileAcquired(UserProfile profile) {
		Objects.requireNonNull(profile, "profile");

		if (!(profile instanceof UserProfileImpl))
			throw new IllegalArgumentException("Unaccepted profile type");

		UserProfileImpl newProfile = (UserProfileImpl)profile;
		if (user != null && !user.getIdentity().equals(newProfile.getIdentity()))
			throw new IllegalStateException("Can not update the user identity");

		this.user = new UserProfileImpl(newProfile.getIdentity(), profile.getName(), profile.hasAvatar());

		updateUserInfoConfig();
	}

	@Override
	public void onUserProfileChanged(String name, boolean avatar) {
		CryptoIdentity identity = user.getIdentity(); // use the existing identity
		this.user = new UserProfileImpl(identity, name, avatar);

		updateUserInfoConfig();
	}

	////////////////////////////////////////////////////////////////////////////
	// Messages

	// Consumes the checked exception, and logs it
	private void putMessage(Message message) {
		try {
			repository.putMessage(message);
		} catch (Exception e) {
			log.error("Failed to save the message to the repository.", e);
		}
	}

	@Override
	public void onMessage(Message message) {
		boolean isChannelMessage = !isMe(message.getTo());
		Id convId = isChannelMessage ? message.getTo() : message.getFrom();
		((MessageImpl)message).setConversationId(convId);
		putMessage(message);

		getOrCreateConversation(message.getConversationId()).update(message);
		messageListeners.forEach(l -> l.onMessage(message));
	}

	@Override
	public void onSending(Message message) {
		Id convId = message.getTo();
		((MessageImpl)message).setConversationId(convId);
		putMessage(message);

		getOrCreateConversation(convId).update(message);
		messageListeners.forEach(l -> l.onSending(message));
	}

	@Override
	public void onSent(Message message) {
		Id convId = message.getConversationId();
		if (convId == null) {
			// send by same device, already set the conversation id in onSending
			convId = message.getTo();
			((MessageImpl)message).setConversationId(convId);
		}

		putMessage(message);

		getOrCreateConversation(convId).update(message);
		messageListeners.forEach(l -> l.onSent(message));
	}

	@Override
	public void onBroadcast(Message message) {
		Id convId = message.getFrom();
		((MessageImpl)message).setConversationId(convId);
		putMessage(message);

		getOrCreateConversation(message.getConversationId()).update(message);
		messageListeners.forEach(l -> l.onBroadcast(message));
	}

	private ConversationImpl getOrCreateConversation(Id convId) {
		return conversations.compute(convId, (k, v) -> {
			ConversationImpl conv = null;

			if (v == null) {
				Contact contact = null;
				try {
					contact = getContact(convId);
				} catch (RepositoryException e) {
					log.error("Failed to get the contact: ", convId, e);
				}

				if (contact == null) {
					// TODO: to be removed
					contact = new UnknownContact(convId);
					log.warn("INTERNAL WARN: should never happen to get unknown contact {}", convId);
				}

				conv = new ConversationImpl(contact);
			} else {
				conv = v;
			}

			return conv;
		});
	}

	@Override
	public List<Conversation> getConversations() {
		return new ArrayList<>(conversations.values());
	}

	@Override
	public Conversation getConversation(Id conversationId) {
		Objects.requireNonNull(conversationId, "conversationId");
		return conversations.get(conversationId);
	}

	@Override
	public void removeConversation(Id conversationId) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");
		conversations.remove(conversationId);
		repository.removeConversation(conversationId);
	}

	@Override
	public void removeConversations(Collection<Id> conversationIds) throws RepositoryException {
		Objects.requireNonNull(conversationIds, "conversationIds");
		if (conversationIds.isEmpty())
			return;

		conversations.keySet().removeAll(conversationIds);
		repository.removeConversations(conversationIds);
	}

	@Override
	public List<Message> getMessages(Id conversationId, long begin, long end) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");
		return repository.getMessages(conversationId, begin, end);
	}

	@Override
	public List<Message> getMessages(Id conversationId, long since, int limit, int offset) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");
		return repository.getMessages(conversationId, since, limit, offset);
	}

	@Override
	public void removeMessage(long messageId) throws RepositoryException {
		repository.removeMessage(messageId);
	}

	@Override
	public void removeMessages(Collection<Long> messageIds) throws RepositoryException {
		Objects.requireNonNull(messageIds, "messageIds");
		if (messageIds.isEmpty())
			return;

		repository.removeMessages(messageIds);
	}

	@Override
	public void removeMessages(Id conversionId) throws RepositoryException {
		Objects.requireNonNull(conversionId, "conversionId");
		repository.removeMessages(conversionId);
	}

	////////////////////////////////////////////////////////////////////////////
	// Channels

	@Override
	public void onJoinedChannel(Channel channel) {
		try {
			repository.putContact(channel);
		} catch (RepositoryException e) {
			log.error("Failed to save the channel to the repository.");
		}

		channelListeners.forEach(l -> l.onJoinedChannel(channel));
	}

	@Override
	public void onLeftChannel(Channel channel) {
		// Keep all the local channel information and messages
		// just notify the user agent that the user left the channel
		ChannelImpl ch = (ChannelImpl)channel;
		ch.removeMember(user.getId());

		try {
			// remove self from the channel
			repository.removeChannelMember(ch.getId(), user.getId());
		} catch (RepositoryException e) {
			log.error("Failed to remove the channel member from the repository", e);
		}

		channelListeners.forEach(l -> l.onLeftChannel(channel));
	}

	@Override
	public void onChannelDeleted(Channel channelId) {
		// Keep all the local channel information and messages
		// just notify the user agent that the channel is deleted
		channelListeners.forEach(l -> l.onChannelDeleted(channelId));
	}

	@Override
	public void onChannelUpdated(Channel channel) {
		try {
			repository.putContact(channel);
		} catch (RepositoryException e) {
			log.error("Failed to save the channel to the repository.");
		}

		channelListeners.forEach(l -> l.onChannelUpdated(channel));
	}

	@Override
	public void onChannelMembers(Channel channel, List<Member> members) {
		try {
			repository.refillChannelMembers(channel.getId(), members);
			((ChannelImpl)channel).invalidateMembers();
		} catch (RepositoryException e) {
			log.error("Failed to save the channel members to the repository", e);
		}
	}

	@Override
	public void onChannelMemberJoined(Channel channel, Member member) {
		try {
			((ChannelImpl)channel).addMember(member);
			repository.putChannelMember(channel.getId(), member);
		} catch (RepositoryException e) {
			log.error("Failed to save the channel member to the repository.");
		}

		channelListeners.forEach(l -> l.onChannelMemberJoined(channel, member));
	}

	@Override
	public void onChannelMemberLeft(Channel channel, Member member) {
		try {
			((ChannelImpl)channel).removeMember(member.getId());
			repository.removeChannelMember(channel.getId(), member.getId());
		} catch (RepositoryException e) {
			log.error("Failed to remove the channel member from the repository.");
		}

		channelListeners.forEach(l -> l.onChannelMemberLeft(channel, member));
	}

	@Override
	public void onChannelMembersBanned(Channel channel, List<Member> banned) {
		List<Id> ids = banned.stream().map(Member::getId).collect(Collectors.toList());
		List<Channel.Member> members = ((ChannelImpl)channel).setMembersRole(ids, Role.BANNED);

		try {
			repository.putChannelMembers(channel.getId(), members);
		} catch (RepositoryException e) {
			log.error("Failed to save the channel members to the repository.");
		}

		channelListeners.forEach(l -> l.onChannelMembersBanned(channel, members));
	}

	@Override
	public void onChannelMembersUnbanned(Channel channel, List<Member> unbanned) {
		List<Id> ids = unbanned.stream().map(Member::getId).collect(Collectors.toList());
		List<Channel.Member> members = ((ChannelImpl)channel).setMembersRole(ids, Role.MEMBER);

		try {
			repository.putChannelMembers(channel.getId(), members);
		} catch (RepositoryException e) {
			log.error("Failed to save the channel members to the repository.");
		}

		channelListeners.forEach(l -> l.onChannelMembersUnbanned(channel, members));
	}

	@Override
	public void onChannelMembersRoleChanged(Channel channel, List<Member> changed, Role role) {
		List<Id> ids = changed.stream().map(Member::getId).collect(Collectors.toList());
		List<Channel.Member> members = ((ChannelImpl)channel).setMembersRole(ids, role);

		try {
			repository.putChannelMembers(channel.getId(), members);
		} catch (RepositoryException e) {
			log.error("Failed to save the channel members to the repository.");
		}

		channelListeners.forEach(l -> l.onChannelMembersRoleChanged(channel, members, role));
	}

	@Override
	public void onChannelMembersRemoved(Channel channel, List<Member> removed) {
		List<Id> ids = removed.stream().map(Member::getId).collect(Collectors.toList());
		((ChannelImpl)channel).removeMembers(ids);

		try {
			repository.removeChannelMembers(channel.getId(), ids);
		} catch (RepositoryException e) {
			log.error("Failed to remove the channel member from the repository.");
		}

		channelListeners.forEach(l -> l.onChannelMembersRemoved(channel, removed));
	}

	@Override
	public List<Channel> getChannels() throws RepositoryException {
		return repository.getAllChannels();
	}

	@Override
	public Channel getChannel(Id channelId) throws RepositoryException {
		return repository.getChannel(channelId);
	}

	////////////////////////////////////////////////////////////////////////////
	// Contacts

	private List<Contact> mergeContactsUpdate(Collection<Contact> contacts) throws RepositoryException {
		List<Id> ids = contacts.stream().map(Contact::getId).collect(Collectors.toList());
		Map<Id, Contact> locals = repository.getContacts(ids).stream().collect(Collectors.toMap(Contact::getId, c -> c));

		List<Contact> merged = new ArrayList<>();
		for (Contact updated : contacts) {
			Contact local = locals.get(updated.getId());
			if (local == null) {
				merged.add(updated);
			} else if (updated.getRevision() >= local.getRevision()) {
				merged.add(updated);
			} else {
				log.warn("THIS SHOULD NOT HAPPEN: Contact revision {} is lower than the existing one {}", updated.getRevision(), local.getRevision());
				// ignore the updated
			}
		}

		return merged;
	}

	@Override
	public void onContactsUpdated(String baseVersionId, String newVersionId, List<Contact> contacts) {
		try {
			// TODO: check the baseVersion id, should be same with the current local version
			List<Contact> merged = mergeContactsUpdate(contacts);
			repository.putContactsUpdate(newVersionId, merged);
		} catch (RepositoryException e) {
			log.error("Failed to save the contacts to the repository.");
		}
		contactListeners.forEach(l -> l.onContactsUpdated(baseVersionId, newVersionId, contacts));
	}

	@Override
	public void onContactsCleared() {
		try {
			repository.removeAllUserContacts();
		} catch (RepositoryException e) {
			log.error("Failed to save the contacts from the repository.");
		}

		contactListeners.forEach(l -> l.onContactsCleared());
	}

	@Override
	public String getContactsVersion() throws RepositoryException {
		return repository.getContactsVersion();
	}

	@Override
	public void putContactsUpdate(String versionId, Collection<Contact> updated) throws RepositoryException {
		Objects.requireNonNull(versionId, "versionId");
		Objects.requireNonNull(updated, "updated");

		repository.putContactsUpdate(versionId, updated);
	}

	@Override
	public List<Contact> getContacts(List<Id> ids) throws RepositoryException {
		return repository.getContacts(ids);
	}

	@Override
	public List<Contact> getUserContacts() throws RepositoryException {
		return repository.getAllUserContacts();
	}

	@Override
	public Contact getContact(Id contactId) throws RepositoryException {
		return repository.getContact(contactId);
	}


	@Override
	public void putContact(Contact contact) throws RepositoryException {
		Objects.requireNonNull(contact, "contact");
		repository.putContact(contact);
	}

	@Override
	public void putContacts(Collection<Contact> contacts) throws RepositoryException {
		Objects.requireNonNull(contacts, "contacts");
		if (contacts.isEmpty())
			return;

		repository.putContacts(contacts);
	}

	@Override
	public void removeContact(Id contactId) throws RepositoryException {
		Objects.requireNonNull(contactId, "contactId");

		repository.removeContact(contactId);
	}

	@Override
	public void removeContacts(Collection<Id> contactIds) throws RepositoryException {
		Objects.requireNonNull(contactIds, "contactIds");
		if (contactIds.isEmpty())
			return;

		repository.removeContacts(contactIds);
	}

	@Override
	public void removeUserContacts() throws RepositoryException {
		repository.removeAllUserContacts();
	}
}
