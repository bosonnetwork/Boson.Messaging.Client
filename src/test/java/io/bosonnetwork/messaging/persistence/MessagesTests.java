package io.bosonnetwork.messaging.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;
import io.bosonnetwork.messaging.Conversation;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.Message.ContentDispositions;
import io.bosonnetwork.messaging.Message.ContentTypes;
import io.bosonnetwork.messaging.impl.ContactImpl;
import io.bosonnetwork.messaging.impl.ConversationImpl;
import io.bosonnetwork.messaging.impl.MessageImpl;
import io.bosonnetwork.messaging.impl.UnknownContact;
import net.datafaker.Faker;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessagesTests {
	private static TestDatabase db;
	private static Map<Id, AbstractContact> contacts;
	private static List<Conversation> conversations;
	private static Map<Id, List<Message>> messages;

	private static Id me = Id.random();
	private static long idx = 0;
	private static final long baseTimestamp = 1577836800000L; // UTC - 1/1/2020 00:00:00 GMT+0000
	private static long current = baseTimestamp;

	private static Id bulkPeer = Id.random();
	private static int messageCount = Random.random().nextInt(1024, 2048);

	private static Faker faker = new Faker();

	@BeforeAll
	static void setup() throws IOException {
		db = TestDatabase.open("messages.db");
		contacts = new HashMap<>();
		conversations = new ArrayList<>();
		messages = new HashMap<>();
	}

	@AfterAll
	static void teardown() throws IOException {
		db.close();
	}

	private static MessageImpl createMessage(Id from, Id to) {
		current += Random.random().nextInt(1000, 10000);

		int type = faker.options().option(Message.Types.MESSAGE, Message.Types.CALL);
		String contentType = faker.options().option(ContentTypes.TEXT, ContentTypes.JSON,
				ContentTypes.IMAGE_JPEG, ContentTypes.IMAGE_PNG, ContentTypes.IMAGE_WEBP,
				ContentTypes.AUDIO_AAC, ContentTypes.AUDIO_MP3, ContentTypes.AUDIO_WEBM,
				ContentTypes.VIDEO_MP4, ContentTypes.VIDEO_WEBM, ContentTypes.BINARY, null);
		String contentDisposition = faker.options().option(
				ContentDispositions.INLINE, ContentDispositions.ATTACHMENT, null);

		Map<String, Object> props = faker.bool().bool() ? null :
			IntStream.range(0, Random.random().nextInt(4, 8))
	            .boxed()
	            .collect(Collectors.toMap(
	                i -> faker.lorem().characters(),
	                i -> faker.job().title()
	            ));

		return new MessageImpl(-1L, null, Message.VERSION,
				from, to, idx++, current, type, props, contentType, contentDisposition,
				faker.lorem().paragraph().getBytes(), current + Random.random().nextInt(100000), false);
	}

	private static <T> T nullOr(Supplier<T> supplier) {
		return faker.bool().bool() ? supplier.get() : null;
	}

	private static String tags() {
		Stream<String> tags = faker.stream(() -> faker.hobby().activity()).len(1, 6).generate();
		return tags.collect(Collectors.joining(","));
	}

	private static AbstractContact createContact(Id userId, Id peerId) {
		KeyPair sessionKeyPair = KeyPair.random();

		return new ContactImpl(userId, peerId, faker.bool().bool(),
				nullOr(() -> sessionKeyPair.privateKey().bytes()),
				nullOr(() -> faker.name().fullName()), faker.bool().bool(),
				nullOr(() -> faker.name().fullName()),
				nullOr(() -> tags()),
				faker.bool().bool(), faker.bool().bool(),
				System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(),
				false, faker.number().numberBetween(0, 100), faker.bool().bool());
	}

	@Test
	@Order(0)
	void testEmptyConversactions() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Conversations.class);
			var convs = dao.getAll();
			assertNotNull(convs);
			assertTrue(convs.isEmpty());
		});
	}

	@Test
	@Order(1)
	void testAddMessage() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			var contactsDao = handle.attach(Contacts.class);

			var s = Random.random().nextInt(8, 32);
			for (int i = 0; i < s; i++) {
				var contactId = Id.random();
				AbstractContact contact = createContact(contactId, Id.random());
				var rc = contactsDao.putContact(contact);
				assertEquals(1, rc);
				contacts.put(contactId, contact);

				List<Message> lst = new ArrayList<>();
				var n = Random.random().nextInt(16, 128);
				MessageImpl message = null;
				for (int j = 0; j < n; j++) {
					message = j % 2 == 0 ? createMessage(contactId, me) : createMessage(me, contactId);
					message.setConversationId(contactId);
					var rid = dao.put(message);
 					message.setRid(rid);
					lst.add(message);
				}

				Collections.reverse(lst);
				messages.put(contactId, lst);

				var conversation = new ConversationImpl(contact, message);
				conversations.add(conversation);
			}
		});

		conversations.sort(Conversation::compareTo);

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			var convoDao = handle.attach(Conversations.class);

			for (var conv : messages.entrySet()) {
				List<Message> ref = conv.getValue();
				List<Message> lst = dao.get(conv.getKey(), Long.MAX_VALUE, 0);
				assertEquals(ref.size(), lst.size());
				assertEquals(ref, lst);
			}

			var convos = convoDao.getAll();
			convos.sort(Conversation::compareTo);
			assertEquals(conversations, convos);

			for (var c : convos) {
				if (contacts.containsKey(c.getId()))
					assertEquals(contacts.get(c.getId()), c.getInterlocutor());
				else
					assertTrue(c.getInterlocutor() instanceof UnknownContact);
			}
		});
	}

	@Test
	@Order(2)
	void testAddMessages() throws Exception {
		var s = Random.random().nextInt(8, 32);
		for (int i = 0; i < s; i++) {
			var contactId = Id.random();

			AbstractContact contact = createContact(contactId, Id.random());
			contacts.put(contactId, contact);

			List<Message> lst = new ArrayList<>();
			var n = Random.random().nextInt(16, 128);
			MessageImpl message = null;
			for (int j = 0; j < n; j++) {
				message = j % 2 == 0 ? createMessage(contactId, me) : createMessage(me, contactId);
				message.setConversationId(contactId);
				lst.add(message);
			}

			var conversation = new ConversationImpl(contact, message);
			conversations.add(conversation);

			db.getJdbi().useHandle((handle) -> {
				var dao = handle.attach(Messages.class);

				var contactsDao = handle.attach(Contacts.class);
				var rc = contactsDao.putContact(contact);
				assertEquals(1, rc);

				@SuppressWarnings("unused")
				var rids = dao.putAll(lst);
				//int pos = 0;
				//for (var message : lst)
				//	message.setRid(rids[pos++]);
			});

			Collections.reverse(lst);
			messages.put(contactId, lst);
		}

		conversations.sort(Conversation::compareTo);

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			var convoDao = handle.attach(Conversations.class);

			for (var peer : messages.entrySet()) {
				List<Message> ref = peer.getValue();
				List<Message> lst = dao.get(peer.getKey(), Long.MAX_VALUE, 0);
				assertEquals(ref.size(), lst.size());
				assertEquals(ref, lst);
			}

			var convos = convoDao.getAll();
			convos.sort(Conversation::compareTo);
			assertEquals(conversations, convos);

			for (var c : convos) {
				if (contacts.containsKey(c.getId()))
					assertEquals(contacts.get(c.getId()), c.getInterlocutor());
				else
					assertTrue(c.getInterlocutor() instanceof UnknownContact);
			}
		});
	}

	@Test
	@Order(3)
	void testAddMessagesForPaginate() {
		List<Message> lst = new ArrayList<>();
		for (int i = 0; i < messageCount; i++) {
			var message = i % 2 == 0 ? createMessage(bulkPeer, me) : createMessage(me, bulkPeer);
			message.setConversationId(bulkPeer);
			lst.add(message);
		}

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			@SuppressWarnings("unused")
			var rids = dao.putAll(lst);
			//int pos = 0;
			//for (var message : lst)
			//	message.setRid(rids[pos++]);
		});

		Collections.reverse(lst);
		messages.put(bulkPeer, lst);
	}

	@Test
	@Order(4)
	void testGetMessageByRid() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);

			var random = Random.random();
			LongStream.range(1, idx).filter(v -> random.nextInt(1000) > 600).forEach(l -> {
				var message = dao.get(l);
				assertNotNull(message);
			});

			var message = dao.get(idx + 10);
			assertNull(message);
		});
	}

	@Test
	@Order(5)
	void testGetMessageByTimeRange() {
		long interval = 60_000L;

		List<Message> lst = db.getJdbi().withHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			List<Message> all = new ArrayList<>();

			for (long begin = current; begin > baseTimestamp; begin -= interval) {
				var result = dao.get(bulkPeer, begin, begin - interval);
				if (result.isEmpty())
					continue;
				else
					all.addAll(result);
			}

			return all;
		});

		var ref = messages.get(bulkPeer);
		assertEquals(ref.size(), lst.size());
		assertEquals(ref, lst);
	}

	@Test
	@Order(6)
	void testGetMessageByPagination() {
		List<Message> lst = db.getJdbi().withHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			List<Message> all = new ArrayList<>();

			int limit = 30;
			int offset = 0;
			while (true) {
				var result = dao.get(bulkPeer, current, limit, offset);
				if (result.isEmpty())
					break;

				all.addAll(result);
				offset += limit;
			}

			return all;
		});

		var ref = messages.get(bulkPeer);
		assertEquals(ref.size(), lst.size());
		assertEquals(ref, lst);
	}

	@Test
	@Order(7)
	void testGetConversation() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Conversations.class);

			for (Conversation c : conversations) {
				Conversation result = dao.get(c.getId());
				assertEquals(c, result);
			}
		});
	}

	@Test
	@Order(8)
	void testExistConversation() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Conversations.class);

			for (Conversation c : conversations) {
				boolean result = dao.exists(c.getId());
				assertTrue(result);

				result = dao.exists(Id.random());
				assertFalse(result);
			}
		});
	}

	@Test
	@Order(9)
	void testRemoveMessage() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);

			var random = Random.random();
			LongStream.range(1, idx).filter(v -> random.nextInt(1000) > 600).forEach(l -> {
				var message = dao.get(l);
				var lst = messages.get(message.getFrom().equals(me) ? message.getTo() : message.getFrom());
				var removed = lst.remove(message);
				assertTrue(removed);

				var rc = dao.remove(l);
				assertEquals(1, rc);
			});
		});
	}

	@Test
	@Order(10)
	void testRemoveMessages() {
		// Can not use the existing list in the messages due to the rid field not updated after insert
		var lst = db.getJdbi().withHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			return dao.get(bulkPeer, Long.MAX_VALUE, 0);
		});

		var random = Random.random();

		var toBeRemoved = lst.stream().filter(v -> random.nextInt(1000) > 600).collect(Collectors.toList());
		var rids = toBeRemoved.stream().map(m -> m.getRid()).collect(Collectors.toList());

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			var rc = dao.removeAll(rids);
			assertEquals(toBeRemoved.size(), rc);
		});

		lst = messages.get(bulkPeer);
		lst.removeAll(toBeRemoved);

		int remaining = db.getJdbi().withHandle((handle) -> {
			var dao = handle.attach(Messages.class);
			return dao.get(bulkPeer, Long.MAX_VALUE, 0).size();
		});

		assertEquals(lst.size(), remaining);
	}

	@Test
	@Order(11)
	void testRemoveAll() {
		for (var peer : messages.entrySet()) {
			db.getJdbi().useHandle((handle) -> {
				var dao = handle.attach(Messages.class);

				var ref = peer.getValue();
				var rc = dao.removeAll(peer.getKey());
				assertEquals(ref.size(), rc);

				var lst = dao.get(peer.getKey(), Long.MAX_VALUE, 0);
				assertTrue(lst.isEmpty());
			});
		}
	}

	@Test
	@Order(12)
	void testNonExistConversation() {
		List<Conversation> lst;
		do {
			lst = conversations.stream().filter(c -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		List<Conversation> toBeChecked = lst;
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Conversations.class);

			for (Conversation c : toBeChecked) {
				boolean result = dao.exists(c.getId());
				assertFalse(result);
			}
		});
	}
}