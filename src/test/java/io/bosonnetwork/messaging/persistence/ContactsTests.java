package io.bosonnetwork.messaging.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.crypto.Signature.PrivateKey;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Channel.Permission;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;
import io.bosonnetwork.messaging.Profile;
import io.bosonnetwork.messaging.impl.ChannelImpl;
import io.bosonnetwork.messaging.impl.ContactImpl;
import net.datafaker.Faker;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContactsTests {
	private static TestDatabase db;
	private static List<AbstractContact> contacts;
	private static List<Channel> channels;
	private static Map<Id, PrivateKey> keys;
	private static List<Id> peers;
	private static Map<Id, List<Channel.Member>> channelMembers;
	private static Faker faker = new Faker();

	@BeforeAll
	static void setup() throws IOException {
		db = TestDatabase.open("contacts.db");
		contacts = new ArrayList<>();
		keys = new HashMap<>();
		peers = new ArrayList<>();
		channels = new ArrayList<>();
		channelMembers = new HashMap<>();
	}

	@AfterAll
	static void teardown() throws IOException {
		db.close();
	}

	private static <T> T nullOr(Supplier<T> supplier) {
		return faker.bool().bool() ? supplier.get() : null;
	}

	private static String tags() {
		Stream<String> tags = faker.stream(() -> faker.hobby().activity()).len(1, 6).generate();
		return tags.collect(Collectors.joining(","));
	}

	private static AbstractContact createContact() {
		KeyPair user = KeyPair.random();
		Id userId = Id.of(user.publicKey().bytes());
		keys.put(userId, user.privateKey());

		KeyPair peer = KeyPair.random();
		Id peerId = Id.of(peer.publicKey().bytes());
		keys.put(peerId, peer.privateKey());
		peers.add(peerId);

		return createContact(userId, peerId);
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

	private static Profile createProfile(Id userId, Id homePeerId, String name, boolean avatar) {
		return createProfile(userId, homePeerId, name, avatar, null);
	}

	private static Profile createProfile(Id userId, Id homePeerId, String name, boolean avatar, String notice) {
		MessageDigest md = Hash.sha256();
		md.update(userId.bytes());
		md.update(homePeerId.bytes());
		byte[] homePeerSig = keys.get(homePeerId).sign(md.digest());

		md.reset();
		md.update(userId.bytes());
		md.update(homePeerId.bytes());
		//md.update(homePeerSig);
		if (name != null)
			md.update(name.getBytes(UTF_8));
		md.update(avatar ? (byte)1 : (byte)0);
		if (notice != null)
			md.update(notice.getBytes(UTF_8));
		byte[] sig = keys.get(userId).sign(md.digest());

		return new Profile(userId, homePeerId, name, avatar, notice, homePeerSig, sig);
	}

	private static Channel createChannel() {
		KeyPair channelKey = KeyPair.random();
		Id channelId = Id.of(channelKey.publicKey().bytes());
		keys.put(channelId, channelKey.privateKey());

		KeyPair peer = KeyPair.random();
		Id peerId = Id.of(peer.publicKey().bytes());
		keys.put(peerId, peer.privateKey());
		peers.add(peerId);

		KeyPair sessionKeyPair = KeyPair.random();

		return new ChannelImpl(channelId, peerId, false,
				sessionKeyPair.privateKey().bytes(),
				nullOr(() -> faker.name().fullName()), faker.bool().bool(),
				nullOr(() -> faker.lorem().paragraph()),
				Id.random(), Permission.MODERATOR_INVITE,
				nullOr(() -> faker.name().fullName()),
				nullOr(() -> tags()),
				faker.bool().bool(),
				System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis(),
				false, faker.number().numberBetween(0, 100), faker.bool().bool());
	}

	private static Channel.Member createChannelMember(Channel channel) {
		KeyPair user = KeyPair.random();
		Id userId = Id.of(user.publicKey().bytes());
		keys.put(userId, user.privateKey());

		Id peerId = peers.get(Random.random().nextInt(peers.size()));

		Channel.Member member = new Channel.Member(userId, Channel.Role.MEMBER, System.currentTimeMillis());
		member.setContact(createContact(member.getId(), peerId));
		return member;
	}

	private static List<Channel.Member> createChannelMembers(Channel channel) {
		List<Channel.Member> members = new ArrayList<>();

		Channel.Member member = new Channel.Member(channel.getOwner(), Channel.Role.OWNER, System.currentTimeMillis());
		member.setContact(createContact(member.getId(), channel.getHomePeerId()));
		members.add(member);

		int n = Random.random().nextInt(16, 64);
		for (int i = 0; i < n; i++)
			members.add(createChannelMember(channel));

		return members;
	}

	@Test
	@Order(1)
	void testAddContact() {
		var n = Random.random().nextInt(256, 2048);

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (int j = 0; j < n; j++) {
				var contact = createContact();
				int rc = dao.putContact(contact);
				assertEquals(1, rc);
				contacts.add(contact);
			}
		});

		contacts.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CONTACT);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});
	}

	@Test
	@Order(2)
	void testAddContacts() {
		var newContacts = IntStream.range(0, 128 + Random.random().nextInt(4, 64))
				.mapToObj(i -> createContact()).collect(Collectors.toList());

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			int[] rcs = dao.putContacts(newContacts);
			assertEquals(newContacts.size(), IntStream.of(rcs).sum());
		});

		contacts.addAll(newContacts);
		contacts.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CONTACT);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});
	}

	@Test
	@Order(3)
	void testUpdateContact() {
		List<AbstractContact> lst;
		do {
			lst = contacts.stream().filter(c -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		List<AbstractContact> toBeUpdated = lst;
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (AbstractContact contact : toBeUpdated) {
				Profile profile = createProfile(contact.getId(), contact.getHomePeerId(),
						nullOr(() -> faker.name().firstName()), faker.bool().bool());
				contact.update(profile);
				contact.setRemark(nullOr(() -> faker.name().fullName()));
				contact.setTags(nullOr(() -> tags()));
				contact.setBlocked(faker.bool().bool());
				contact.setMuted(faker.bool().bool());
				int rc = dao.putContact(contact);
				assertEquals(1, rc);
			}
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CONTACT);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});
	}

	@Test
	@Order(4)
	void testUpdateContacts() {
		List<AbstractContact> lst;
		do {
			lst = contacts.stream().filter(c -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		for (AbstractContact contact : lst) {
			Profile profile = createProfile(contact.getId(), contact.getHomePeerId(),
					nullOr(() -> faker.name().firstName()), faker.bool().bool());
			contact.update(profile);
			contact.setRemark(nullOr(() -> faker.name().fullName()));
			contact.setTags(nullOr(() -> tags()));
			contact.setBlocked(faker.bool().bool());
			contact.setMuted(faker.bool().bool());
		}

		List<AbstractContact> toBeUpdated = lst;
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			int[] rcs = dao.putContacts(toBeUpdated);
			assertEquals(toBeUpdated.size(), IntStream.of(rcs).sum());
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CONTACT);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});
	}

	@Test
	@Order(51)
	void testAddChannel() {
		var n = Random.random().nextInt(64, 512);

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (int j = 0; j < n; j++) {
				var channel = createChannel();
				int rc = dao.putChannel(channel);
				assertEquals(1, rc);

				var members = createChannelMembers(channel);
				int[] rcs = dao.putChannelMembers(channel.getId(), members);
				assertEquals(members.size(), IntStream.of(rcs).sum());

				members.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));
				channelMembers.put(channel.getId(), members);
				channels.add(channel);
				contacts.add(channel);

				List<AbstractContact> newContacts = members.stream().map(m -> m.getContact()).collect(Collectors.toList());
				rcs = dao.putContacts(newContacts);
				assertEquals(newContacts.size(), IntStream.of(rcs).sum());
				contacts.addAll(newContacts);
			}
		});

		channels.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
		contacts.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			//assertEquals(contacts, result);
			for (int i = 0; i < contacts.size(); i++)
				if (!Objects.equals(contacts.get(i), result.get(i))) {
					var c1 = contacts.get(i);
					var c2 = result.get(i);
					System.out.println(c1);
					System.out.println(c2);
					fail();
				}
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CHANNEL);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(channels, result);
		});

		for (Id id : channelMembers.keySet()) {
			db.getJdbi().useHandle((handle) -> {
				var dao = handle.attach(Contacts.class);
				var result = dao.getAllChannelMembers(id);
				result.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));
				assertEquals(channelMembers.get(id), result);
			});
		}
	}

	@Test
	@Order(52)
	void testAddChannels() {
		var newChannels = IntStream.range(0, Random.random().nextInt(4, 64))
				.mapToObj(i -> createChannel()).collect(Collectors.toList());


		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			int[] rcs = dao.putChannels(newChannels);
			assertEquals(newChannels.size(), IntStream.of(rcs).sum());

			contacts.addAll(newChannels);
			channels.addAll(newChannels);

			for (var channel : newChannels) {
				var members = createChannelMembers(channel);
				rcs = dao.putChannelMembers(channel.getId(), members);
				assertEquals(members.size(), IntStream.of(rcs).sum());

				members.sort((m1, m2) -> m1.getContact().getId().compareTo(m2.getContact().getId()));
				channelMembers.put(channel.getId(), members);

				List<AbstractContact> newContacts = members.stream().map(m -> m.getContact()).collect(Collectors.toList());
				rcs = dao.putContacts(newContacts);
				assertEquals(newContacts.size(), IntStream.of(rcs).sum());
				contacts.addAll(newContacts);
			}
		});

		channels.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
		contacts.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CHANNEL);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(channels, result);
		});

		for (Id id : channelMembers.keySet()) {
			db.getJdbi().useHandle((handle) -> {
				var dao = handle.attach(Contacts.class);
				var result = dao.getAllChannelMembers(id);
				result.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));
				assertEquals(channelMembers.get(id), result);
			});
		}
	}

	@Test
	@Order(53)
	void testUpdateChannel() {
		List<Channel> lst;
		do {
			lst = channels.stream().filter(c -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		List<Channel> toBeUpdated = lst;
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (var channel : toBeUpdated) {
				Profile profile = createProfile(channel.getId(), channel.getHomePeerId(),
						nullOr(() -> faker.name().firstName()), faker.bool().bool(),
						nullOr(() -> faker.lorem().paragraph()));

				channel.update(profile);

				channel.setRemark(nullOr(() ->faker.name().fullName()));
				channel.setTags(nullOr(() -> tags()));
				channel.setMuted(faker.bool().bool());

				int rc = dao.putChannel(channel);
				assertEquals(1, rc);

				var members = channelMembers.get(channel.getId());
				if (members.size() > 4) {
					List<Channel.Member> toBeRemoved = null;
					do {
						toBeRemoved = members.stream().filter(m -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
					} while (toBeRemoved.isEmpty());

					var ids = toBeRemoved.stream().map(Channel.Member::getId).collect(Collectors.toList());

					for (var memberId : ids) {
						rc = dao.removeChannelMember(channel.getId(), memberId);
						assertEquals(1, rc);
					}

					members.removeAll(toBeRemoved);
				}

				var toBeAdded = IntStream.range(0, Random.random().nextInt(2, 32)).mapToObj(i -> createChannelMember(channel)).collect(Collectors.toList());
				for (var m : toBeAdded) {
					rc = dao.putChannelMember(channel.getId(), m);
					assertEquals(1, rc);
				}

				members.addAll(toBeAdded);
				members.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));

				List<AbstractContact> newContacts = toBeAdded.stream().map(m -> m.getContact()).collect(Collectors.toList());
				int[] rcs = dao.putContacts(newContacts);
				assertEquals(newContacts.size(), IntStream.of(rcs).sum());
				contacts.addAll(newContacts);

				contacts.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			}
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CHANNEL);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(channels, result);
		});

		for (Id id : channelMembers.keySet()) {
			db.getJdbi().useHandle((handle) -> {
				var dao = handle.attach(Contacts.class);
				var result = dao.getAllChannelMembers(id);
				result.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));
				assertEquals(channelMembers.get(id), result);
			});
		}
	}

	@Test
	@Order(54)
	void testUpdateChannels() {
		List<Channel> lst;
		do {
			lst = channels.stream().filter(g -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		List<Channel> toBeUpdated = lst;
		db.getJdbi().useHandle(handle -> {
			var dao = handle.attach(Contacts.class);

			for (var channel : toBeUpdated) {
				Profile profile = createProfile(channel.getId(), channel.getHomePeerId(),
						nullOr(() -> faker.name().firstName()), faker.bool().bool(),
						nullOr(() -> faker.lorem().paragraph()));

				channel.update(profile);

				channel.setRemark(nullOr(() ->faker.name().fullName()));
				channel.setTags(nullOr(() -> tags()));
				channel.setMuted(faker.bool().bool());

				var members = channelMembers.get(channel.getId());
				if (members.size() > 4) {
					List<Channel.Member> toBeRemoved = null;
					do {
						toBeRemoved = members.stream().filter(m -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
					} while (toBeRemoved.isEmpty());

					var ids = toBeRemoved.stream().map(Channel.Member::getId).collect(Collectors.toList());

					int rc = dao.removeChannelMembers(channel.getId(), ids);
					assertEquals(toBeRemoved.size(), rc);

					members.removeAll(toBeRemoved);
				}

				var toBeAdded = IntStream.range(0, Random.random().nextInt(2, 32)).mapToObj(i -> createChannelMember(channel)).collect(Collectors.toList());
				int[] rcs = dao.putChannelMembers(channel.getId(), toBeAdded);
				assertEquals(toBeAdded.size(), IntStream.of(rcs).sum());

				members.addAll(toBeAdded);
				members.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));

				List<AbstractContact> newContacts = toBeAdded.stream().map(m -> m.getContact()).collect(Collectors.toList());
				rcs = dao.putContacts(newContacts);
				assertEquals(newContacts.size(), IntStream.of(rcs).sum());
				contacts.addAll(newContacts);

				contacts.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			}

			int[] rcs = dao.putChannels(toBeUpdated);
			assertEquals(toBeUpdated.size(), IntStream.of(rcs).sum());
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts(AbstractContact.Types.CHANNEL);
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(channels, result);
		});

		for (Id id : channelMembers.keySet()) {
			db.getJdbi().useHandle((handle) -> {
				var dao = handle.attach(Contacts.class);
				var result = dao.getAllChannelMembers(id);
				result.sort((m1, m2) -> m1.getId().compareTo(m2.getId()));
				assertEquals(channelMembers.get(id), result);
			});
		}
	}


	@Test
	@Order(102)
	void testExistContact() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (AbstractContact contact : contacts) {
				boolean result = dao.existsContact(contact.getId());
				assertTrue(result);

				result = dao.existsContact(Id.random());
				assertFalse(result);
			}
		});
	}

	@Test
	@Order(102)
	void testGetContact() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (AbstractContact contact : contacts) {
				AbstractContact result = dao.getContact(contact.getId());
				assertEquals(contact, result);
			}
		});
	}


	@Test
	@Order(103)
	void testRemoveContact() {
		List<AbstractContact> lst;
		do {
			lst = contacts.stream().filter(c -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		contacts.removeAll(lst);

		List<AbstractContact> toBeRemoved = lst;
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);

			for (AbstractContact contact : toBeRemoved) {
				int rc = dao.removeContact(contact.getId());
				assertEquals(1, rc);
			}
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});
	}

	@Test
	@Order(104)
	void testRemoveContacts() {
		List<AbstractContact> lst;
		do {
			lst = contacts.stream().filter(c -> Random.random().nextInt(4) == 0).collect(Collectors.toList());
		} while (lst.isEmpty());

		contacts.removeAll(lst);

		List<AbstractContact> toBeRemoved = lst;
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			int rc = dao.removeContacts(toBeRemoved.stream().map(AbstractContact::getId).collect(Collectors.toList()));
			assertEquals(toBeRemoved.size(), rc);
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			var result = dao.getAllContacts();
			result.sort((c1, c2) -> c1.getId().compareTo(c2.getId()));
			assertEquals(contacts, result);
		});
	}

	@Test
	@Order(201)
	void TestVersion() {
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			assertNull(dao.getVersion());
		});

		String version = "rrqs7mbj4jxfrp83ft9f97hm0000gn";
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			dao.putVersion(version, System.currentTimeMillis());
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			String result = dao.getVersion();
			assertEquals(version, result);
		});

		String newVersion = "arvb7mbj4jxfrp83ft9f97hm5678ba";
		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			dao.putVersion(newVersion, System.currentTimeMillis());
		});

		db.getJdbi().useHandle((handle) -> {
			var dao = handle.attach(Contacts.class);
			String result = dao.getVersion();
			assertEquals(newVersion, result);
		});
	}


}