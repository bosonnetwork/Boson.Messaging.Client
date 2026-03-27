package io.bosonnetwork.messaging.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import io.vertx.core.Vertx;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.jdbi.v3.core.statement.Slf4JSqlLogger;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Channel.Member;
import io.bosonnetwork.messaging.Channel.Role;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;
import io.bosonnetwork.messaging.Conversation;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.MessagingRepository;
import io.bosonnetwork.messaging.RepositoryException;
import io.bosonnetwork.messaging.impl.ChannelImpl;
import io.bosonnetwork.messaging.impl.ConversationImpl;
import io.bosonnetwork.messaging.impl.MessageImpl;
import io.bosonnetwork.utils.jdbi.BosonPlugin;
import io.bosonnetwork.vertx.VertxCaffeine;

public class Database implements MessagingRepository {
	private static final String MEMORY = ":memory:";

	private final Jdbi jdbi;

	private Cache<Id, AbstractContact> contactCache;

	private static final Logger log = LoggerFactory.getLogger(Database.class);

	private Database(Vertx vertx, Jdbi jdbi) {
		this.jdbi = jdbi;

		contactCache = VertxCaffeine.newBuilder(vertx)
				.recordStats()
				.initialCapacity(64)
				.maximumSize(1024)
				.expireAfterAccess(15, TimeUnit.MINUTES)
				.build();
	}

	public static Database open(Vertx vertx, Path database) throws RepositoryException {
		Objects.requireNonNull(vertx, "vertx");

		String db;

		if (database == null || database.toString().equals(MEMORY)) {
			db = MEMORY;
		} else {
			try {
				database = database.normalize().toAbsolutePath();
				Path dir = database.getParent();
				if (!Files.exists(dir))
					Files.createDirectories(dir);

				db = database.toString();
			} catch (IOException e) {
				throw new RepositoryException(e);
			}
		}

		try {
			Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + db.toString());

			// for debugging
			// jdbi.configure(Extensions.class, s -> s.setAllowProxy(true));

			jdbi.installPlugin(new SqlObjectPlugin());
			jdbi.installPlugin(new BosonPlugin());
			jdbi.setSqlLogger(new Slf4JSqlLogger());

			jdbi.useHandle((handle) -> {
				Schema dao = handle.attach(Schema.class);
				dao.create();
			});

			return new Database(vertx, jdbi);
		} catch (JdbiException e) {
			throw new RepositoryException(e);
		}
	}

	public static Database open(Vertx vertx, String database) throws RepositoryException {
		return open(vertx, database != null ? Path.of(database) : null);
	}

	public static Database open(Vertx vertx, File database) throws RepositoryException {
		return open(vertx, database != null ? database.toPath() : null);
	}

	private <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws RepositoryException {
		try {
			return jdbi.withHandle(callback);
		} catch (Exception e) {
			log.error("Database error: {}", e.getMessage(), e);
			throw new RepositoryException(e);
		}
	}

	private <X extends Exception> void useHandle(final HandleConsumer<X> consumer) throws RepositoryException {
		try {
			jdbi.useHandle(consumer);
		} catch (Exception e) {
			log.error("Database error: {}", e.getMessage(), e);
			throw new RepositoryException(e);
		}
	}

	private <X extends Exception> void useTransaction(final HandleConsumer<X> consumer) throws RepositoryException {
		try {
			jdbi.useTransaction(consumer);
		} catch (Exception e) {
			log.error("Database error: {}", e.getMessage(), e);
			throw new RepositoryException(e);
		}
	}

	@Override
	public void putConfig(String key, byte[] value) throws RepositoryException {
		Objects.requireNonNull(key, "key");

		useHandle(handle -> {
			Configuration dao = handle.attach(Configuration.class);
			dao.put(key, value);
		});
	}

	@Override
	public byte[] getConfig(String key) throws RepositoryException {
		Objects.requireNonNull(key, "key");

		return withHandle(handle -> {
			Configuration dao = handle.attach(Configuration.class);
			return dao.get(key).orElse(null);
		});
	}

	@Override
	public void putMessage(Message message) throws RepositoryException {
		Objects.requireNonNull(message, "message");

		useHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			long rid = dao.put(message);
			((MessageImpl)message).setRid(rid);
		});
	}

	@Override
	public void putMessages(Collection<Message> messages) throws RepositoryException {
		Objects.requireNonNull(messages, "messages");
		if (messages.isEmpty())
			return;

		useHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			dao.putAll(messages);
		});
	}

	@Override
	public void updateMessageCompletetdTimestamp(Message message) throws RepositoryException {
		Objects.requireNonNull(message, "message");

		useHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			dao.updateCompletedTimestamp(message);
		});
	}

	@Override
	public void removeMessage(long rid) throws RepositoryException {
		useHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			dao.remove(rid);
		});
	}

	@Override
	public void removeMessages(Collection<Long> rids) throws RepositoryException {
		Objects.requireNonNull(rids, "rids");
		if (rids.isEmpty())
			return;

		useHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			dao.removeAll(rids);
		});
	}

	@Override
	public void removeMessages(Id conversationId) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");

		useHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			dao.removeAll(conversationId);
		});
	}

	// begin(inclusive), end(exclusive)
	@Override
	public List<Message> getMessages(Id conversationId, long begin, long end) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");

		return withHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			return dao.get(conversationId, begin, end);
		});
	}

	@Override
	public List<Message> getMessages(Id conversationId, long since, int limit, int offset) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");

		return withHandle(handle -> {
			Messages dao = handle.attach(Messages.class);
			return dao.get(conversationId, since, limit, offset);
		});
	}

	@Override
	public Conversation getConversaion(Id conversationId) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");

		Conversation conversation = withHandle(handle -> {
			Conversations dao = handle.attach(Conversations.class);
			return dao.get(conversationId);
		});

		if (conversation != null) {
			AbstractContact contact = contactCache.get(conversation.getId(), id -> {
				return conversation.getInterlocutor();
			});

			// same contact instance?
			if (contact != conversation.getInterlocutor())
				((ConversationImpl)conversation).updateInterlocutor(contact);
		}

		return conversation;
	}

	@Override
	public List<Conversation> getAllConversaions() throws RepositoryException {
		List<Conversation> conversations = withHandle(handle -> {
			Conversations dao = handle.attach(Conversations.class);
			return dao.getAll();
		});

		conversations.forEach((conversation) -> {
			AbstractContact contact = contactCache.get(conversation.getId(), id -> {
				return conversation.getInterlocutor();
			});

			// same contact instance?
			if (contact != conversation.getInterlocutor())
				((ConversationImpl)conversation).updateInterlocutor(contact);
		});

		return conversations;
	}

	@Override
	public void removeConversation(Id conversationId) throws RepositoryException {
		Objects.requireNonNull(conversationId, "conversationId");
		removeMessages(conversationId);
	}

	@Override
	public void removeConversations(Collection<Id> conversationIds) throws RepositoryException {
		Objects.requireNonNull(conversationIds, "conversationIds");
		if (conversationIds.isEmpty())
			return;

		useTransaction(handle -> {
			Messages dao = handle.attach(Messages.class);

			for (Id conversationId : conversationIds)
				dao.removeAll(conversationId);
		});
	}

	@Override
	public String getContactsVersion() throws RepositoryException {
		return withHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			return dao.getVersion();
		});
	}

	@Override
	public void putContactsUpdate(String versionId, Collection<AbstractContact> updated) throws RepositoryException {
		useTransaction(handle -> {
			Contacts dao = handle.attach(Contacts.class);

			for (AbstractContact contact : updated) {
				if (contact instanceof ChannelImpl ch) {
					dao.putChannel(ch);
					ch.setMembers(this::getChannelMembers);
				} else {
					dao.putContact(contact);
				}
			}

			dao.putVersion(versionId, System.currentTimeMillis());
		});

		for (AbstractContact contact : updated) {
			contactCache.asMap().compute(contact.getId(), (id, existing) -> {
				return existing == contact ? existing : contact;
			});
		}
	}

	@Override
	public void putContact(AbstractContact contact) throws RepositoryException {
		Objects.requireNonNull(contact, "contact");

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			if (contact instanceof ChannelImpl ch) {
				dao.putChannel(ch);
				ch.setMembers(this::getChannelMembers);
			} else {
				dao.putContact(contact);
			}
		});

		contactCache.asMap().compute(contact.getId(), (id, existing) -> {
			return existing == contact ? existing : contact;
		});
	}

	@Override
	public void putContacts(Collection<AbstractContact> contacts) throws RepositoryException {
		Objects.requireNonNull(contacts, "contacts");

		if (contacts.isEmpty())
			return;

		useTransaction(handle -> {
			Contacts dao = handle.attach(Contacts.class);

			for (AbstractContact contact : contacts) {
				if (contact instanceof ChannelImpl ch) {
					dao.putChannel(ch);
					ch.setMembers(this::getChannelMembers);
				} else {
					dao.putContact(contact);
				}
			}
		});

		for (AbstractContact contact : contacts) {
			contactCache.asMap().compute(contact.getId(), (id, existing) -> {
				return existing == contact ? existing : contact;
			});
		}
	}

	@Override
	public AbstractContact getContact(Id contactId) throws RepositoryException {
		AbstractContact existing = contactCache.getIfPresent(contactId);
		if (existing != null)
			return existing;

		AbstractContact contact = withHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			AbstractContact c = dao.getContact(contactId);
			if (c instanceof ChannelImpl ch)
				ch.setMembers(this::getChannelMembers);

			return c;
		});

		if (contact != null)
			return contactCache.get(contact.getId(), id -> contact);
		else
			return contact;
	}

	@Override
	public List<AbstractContact> getContacts(List<Id> contactIds) throws RepositoryException {
		return withHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			return dao.getContacts(contactIds);
		}).stream().map(c -> {
			if (c instanceof ChannelImpl ch)
				ch.setMembers(this::getChannelMembers);

			return contactCache.get(c.getId(), id -> c);
		}).collect(Collectors.toList());
	}

	@Override
	public List<AbstractContact> getAllContacts() throws RepositoryException {
		return withHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			return dao.getAllContacts();
		}).stream().map(c -> {
			if (c instanceof ChannelImpl ch)
				ch.setMembers(this::getChannelMembers);

			return contactCache.get(c.getId(), id -> c);
		}).collect(Collectors.toList());
	}

	@Override
	public List<AbstractContact> getAllUserContacts() throws RepositoryException {
		return withHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			return dao.getAllUserContacts();
		}).stream().map(c -> {
			if (c instanceof ChannelImpl ch)
				ch.setMembers(this::getChannelMembers);

			return contactCache.get(c.getId(), id -> c);
		}).collect(Collectors.toList());
	}

	@Override
	public List<AbstractContact> getAllContacts(int type) throws RepositoryException {
		return withHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			return dao.getAllContacts(type);
		}).stream().map(c -> {
			if (c instanceof ChannelImpl ch)
				ch.setMembers(this::getChannelMembers);

			return contactCache.get(c.getId(), id -> c);
		}).collect(Collectors.toList());
	}

	@Override
	public boolean existsContact(Id contactId) throws RepositoryException {
		Objects.requireNonNull(contactId, "contactId");
		return getContact(contactId) != null;
	}

	@Override
	public void removeContact(Id contactId) throws RepositoryException {
		Objects.requireNonNull(contactId, "contactId");

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeContact(contactId);
		});

		contactCache.invalidate(contactId);
	}

	@Override
	public void removeContacts(Collection<Id> contactIds) throws RepositoryException {
		Objects.requireNonNull(contactIds, "contactIds");

		if (contactIds.isEmpty())
			return;

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeContacts(contactIds);
		});

		contactCache.invalidateAll(contactIds);
	}

	@Override
	public void removeAllUserContacts() throws RepositoryException {
		useTransaction(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeAllUserContacts();
			dao.clearVersion();
		});

		contactCache.invalidateAll();
	}

	@Override
	public void removeAllContacts() throws RepositoryException {
		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeAllContacts();
		});

		contactCache.invalidateAll();
	}

	@Override
	public void putChannelMember(Channel channel, Member member) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(member, "member");

		useHandle((handle) -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.putChannelMember(channel.getId(), member);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	@Override
	public void putChannelMembers(Channel channel, Collection<Member> members) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(members, "members");

		if (members.isEmpty())
			return;

		useHandle((handle) -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.putChannelMembers(channel.getId(), members);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	@Override
	public void refillChannelMembers(Channel channel, Collection<Member> members) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(members, "members");

		if (members.isEmpty())
			return;

		useTransaction(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeAllChannelMembers(channel.getId());
			dao.putChannelMembers(channel.getId(), members);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	private List<Member> getChannelMembers(Id channelId) {
		try {
			return withHandle(handle -> {
				Contacts dao = handle.attach(Contacts.class);
				return dao.getAllChannelMembers(channelId);
			});
		} catch (RepositoryException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void removeChannelMember(Channel channel, Id memberId) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(memberId, "memberId");

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeChannelMember(channel.getId(), memberId);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	@Override
	public void removeChannelMembers(Channel channel, Collection<Id> memberIds) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(memberIds, "memberIds");

		if (memberIds.isEmpty())
			return;

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeChannelMembers(channel.getId(), memberIds);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	@Override
	public void removeAllChannelMembers(Channel channel) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.removeAllChannelMembers(channel.getId());
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	@Override
	public void setChannelMemberRole(Channel channel, Id memberId, Role role) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(memberId, "memberId");
		Objects.requireNonNull(role, "role");

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.updateChannelMemberRole(channel.getId(), memberId, role);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}

	@Override
	public void setChannelMembersRole(Channel channel, List<Id> memberIds, Role role) throws RepositoryException {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(memberIds, "memberIds");
		Objects.requireNonNull(role, "role");

		if (memberIds.isEmpty())
			return;

		useHandle(handle -> {
			Contacts dao = handle.attach(Contacts.class);
			dao.updateChannelMembersRole(channel.getId(), memberIds, role);
		});

		ChannelImpl cached = (ChannelImpl)contactCache.getIfPresent(channel.getId());
		if (cached != null && cached != channel)
			cached.invalidateMembers();
	}
}