package io.bosonnetwork.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.crypto.Signature.PrivateKey;
import io.bosonnetwork.messaging.impl.ChannelImpl;
import io.bosonnetwork.messaging.impl.ContactImpl;
import io.bosonnetwork.utils.Json;

public class ContactTests {
	private static Map<Id, PrivateKey> keys = new HashMap<>();
	private static Map<Id, Contact> contacts = new HashMap<>();

	private static boolean contactEquals(Contact c1, Contact c2) {
		if (c1 == c2)
			return true;

		return Objects.equals(c1.getId(), c2.getId()) &&
				c1.getType() == c2.getType() &&
				Objects.equals(c1.getName(), c2.getName()) &&
				Objects.equals(c1.getAvatar(), c2.getAvatar()) &&
				Objects.equals(c1.getRemark(), c2.getRemark()) &&
				Objects.equals(c1.getTags(), c2.getTags()) &&
				c1.isMuted() == c2.isMuted() &&
				c1.isBlocked() == c1.isBlocked() &&
				c1.getCreated() == c2.getCreated() &&
				c1.getLastModified() == c2.getLastModified();
	}

	private static boolean channelEquals(Channel c1, Channel c2) {
		if (c1 == c2)
			return true;

		return Objects.equals(c1.getId(), c2.getId()) &&
				c1.getType() == c2.getType() &&
				Objects.equals(c1.getPrivateKey(), c2.getPrivateKey()) &&
				Objects.equals(c1.getName(), c2.getName()) &&
				Objects.equals(c1.getAvatar(), c2.getAvatar()) &&
				Objects.equals(c1.getNotice(), c2.getNotice()) &&
				Objects.equals(c1.getOwner(), c2.getOwner()) &&
				Objects.equals(c1.getPermission(), c2.getPermission()) &&
				Objects.equals(c1.getRemark(), c2.getRemark()) &&
				Objects.equals(c1.getTags(), c2.getTags()) &&
				c1.isMuted() == c2.isMuted() &&
				c1.isBlocked() == c1.isBlocked() &&
				c1.getCreated() == c2.getCreated() &&
				c1.getLastModified() == c2.getLastModified();
	}

	private static Profile createProfile(Id id, Id homePeerId, String name, boolean avatar) {
		return createProfile(id, homePeerId, name, avatar, null);
	}

	private static Profile createProfile(Id id, Id homePeerId, String name, boolean avatar, String notice) {
		MessageDigest md = Hash.sha256();
		md.update(id.bytes());
		md.update(homePeerId.bytes());
		byte[] homePeerSig = keys.get(homePeerId).sign(md.digest());

		md.reset();
		md.update(id.bytes());
		md.update(homePeerId.bytes());
		md.update(homePeerSig);
		if (name != null)
			md.update(name.getBytes(UTF_8));
		md.update(avatar ? (byte)1 : (byte)0);
		if (notice != null)
			md.update(notice.getBytes(UTF_8));
		byte[] sig = keys.get(id).sign(md.digest());

		return new Profile(id, homePeerId, homePeerSig, name, avatar, notice, sig);
	}

	@Test
	void testContact() throws IOException {
		var mapper = Json.objectMapper();

		var keyPair = KeyPair.random();
		var contactId = Id.of(keyPair.publicKey().bytes());
		keys.put(contactId, keyPair.privateKey());

		keyPair = KeyPair.random();
		var peerId = Id.of(keyPair.publicKey().bytes());
		keys.put(peerId, keyPair.privateKey());

		// new contact with id
		var alice = new ContactImpl(contactId, peerId, false);
		contacts.put(contactId, alice);

		assertEquals(contactId, alice.getId());
		assertEquals(peerId, alice.getHomePeerId());
		assertEquals(Contact.Types.CONTACT, alice.getType());
		assertNull(alice.getName());
		assertNull(alice.getAvatar());
		assertNull(alice.getRemark());
		assertNull(alice.getTags());
		assertFalse(alice.isBlocked());
		assertFalse(alice.isMuted());
		assertEquals(alice.getCreated(), alice.getLastModified());
		assertTrue(contactId.toBase58String().startsWith(alice.getDisplayName().substring(0, 4)));
		assertEquals(mapper.writerFor(Contact.class).writeValueAsString(alice), mapper.writeValueAsString(alice));

		var json = mapper.writeValueAsString(alice);
		var map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(5, map.size());
		assertEquals(contactId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CONTACT, (Integer)map.get("t"));
		assertEquals(alice.getCreated(), (Long)map.get("c"));
		assertEquals(alice.getLastModified(), (Long)map.get("m"));

		var parsed = mapper.readValue(json, Contact.class);
		assertTrue(parsed instanceof ContactImpl);
		assertEquals(alice, parsed);

		// update profile
		Profile profile = createProfile(contactId, peerId, "Alice", true);

		alice.update(profile);
		assertEquals(contactId, alice.getId());
		assertEquals(peerId, alice.getHomePeerId());
		assertEquals(Contact.Types.CONTACT, alice.getType());
		assertEquals(profile.getName(), alice.getName());
		assertEquals(profile.hasAvatar(), alice.hasAvatar());
		assertNull(alice.getRemark());
		assertNull(alice.getTags());
		assertFalse(alice.isBlocked());
		assertFalse(alice.isMuted());
		assertTrue(alice.getCreated() <= alice.getLastModified());
		assertTrue(alice.getCreated() < alice.getLastUpdated());
		assertEquals(profile.getName(), alice.getDisplayName());
		assertEquals(mapper.writerFor(Contact.class).writeValueAsString(alice), mapper.writeValueAsString(alice));

		json = mapper.writeValueAsString(alice);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(5, map.size());
		assertEquals(contactId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CONTACT, map.get("t"));
		//assertEquals(profile.getName(), map.get("n"));
		//assertEquals(profile.hasAvatar(), map.get("a"));
		assertEquals(alice.getCreated(), map.get("c"));
		assertEquals(alice.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, Contact.class);
		parsed.update(profile);
		assertTrue(parsed instanceof ContactImpl);
		assertEquals(alice, parsed);

		// update the local contact info
		alice.setMuted(true);
		alice.setBlocked(true);
		alice.setRemark("Alice Brown");
		alice.setTags("foo,bar");

		assertEquals(contactId, alice.getId());
		assertEquals(peerId, alice.getHomePeerId());
		assertEquals(Contact.Types.CONTACT, alice.getType());
		assertEquals(profile.getName(), alice.getName());
		assertEquals(profile.hasAvatar(), alice.hasAvatar());
		assertEquals("Alice Brown", alice.getRemark());
		assertEquals("foo,bar", alice.getTags());
		assertTrue(alice.isBlocked());
		assertTrue(alice.isMuted());
		assertTrue(alice.getCreated() < alice.getLastModified());
		assertTrue(alice.getCreated() < alice.getLastUpdated());
		assertEquals("Alice Brown", alice.getDisplayName());
		assertEquals(mapper.writerFor(Contact.class).writeValueAsString(alice), mapper.writeValueAsString(alice));

		json = mapper.writeValueAsString(alice);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(9, map.size());
		assertEquals(contactId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CONTACT, map.get("t"));
		//assertEquals(profile.getName(), map.get("n"));
		//assertEquals(profile.hasAvatar(), map.get("a"));
		assertEquals("Alice Brown", map.get("r"));
		assertEquals("foo,bar", map.get("ts"));
		assertTrue((Boolean)map.get("d"));
		assertTrue((Boolean)map.get("b"));
		assertEquals(alice.getCreated(), map.get("c"));
		assertEquals(alice.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, Contact.class);
		parsed.update(profile);
		assertTrue(parsed instanceof ContactImpl);
		assertEquals(alice, parsed);
	}

	@Test
	void testChannel() throws IOException {
		var b64Encoder = Base64.getUrlEncoder().withoutPadding();
		var mapper = Json.objectMapper();

		var owner = Id.random();
		var permission = Channel.Permission.MODERATOR_INVITE;

		var channelKeyPair = KeyPair.random();
		var channelId = Id.of(channelKeyPair.publicKey().bytes());
		keys.put(channelId, channelKeyPair.privateKey());

		var peerKeyPair = KeyPair.random();
		var peerId = Id.of(peerKeyPair.publicKey().bytes());
		keys.put(peerId, peerKeyPair.privateKey());

		var memberKeyPair = KeyPair.random();

		// new group with id
		Channel team = new ChannelImpl(channelId, peerId, memberKeyPair.privateKey().bytes(), false);
		team.setOwner(owner);
		team.setPermission(permission);

		assertEquals(channelId, team.getId());
		assertEquals(Contact.Types.CHANNEL, team.getType());
		assertArrayEquals(memberKeyPair.privateKey().bytes(), team.getPrivateKey());
		assertNull(team.getName());
		assertNull(team.getAvatar());
		assertNull(team.getNotice());
		assertEquals(owner, team.getOwner());
		assertEquals(permission, team.getPermission());
		assertNull(team.getRemark());
		assertNull(team.getTags());
		assertFalse(team.isBlocked());
		assertFalse(team.isMuted());
		assertTrue(team.getCreated() < team.getLastModified());
		assertTrue(channelId.toBase58String().startsWith(team.getDisplayName().substring(0, 4)));
		assertNotEquals(mapper.writerFor(Contact.class).writeValueAsString(team), mapper.writeValueAsString(team));

		var json = mapper.writeValueAsString(team);
		System.out.println(json);
		var map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(8, map.size());
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CHANNEL, (Integer)map.get("t"));
		assertEquals(b64Encoder.encodeToString(memberKeyPair.privateKey().bytes()), map.get("k"));
		assertEquals(owner.toBase58String(), map.get("o"));
		assertEquals(permission.value(), map.get("pm"));
		assertEquals(team.getCreated(), (Long)map.get("c"));
		assertEquals(team.getLastModified(), (Long)map.get("m"));

		var parsed = mapper.readValue(json, Contact.class);
		assertTrue(parsed instanceof ChannelImpl);
		assertEquals(team, parsed);

		Profile profile = createProfile(channelId, peerId, "Boson dev team", true, "Boson dev collaboration");
		// fetch and update group info
		team.update(profile);
		assertEquals(channelId, team.getId());
		assertEquals(peerId, team.getHomePeerId());
		assertEquals(Contact.Types.CHANNEL, team.getType());
		assertArrayEquals(memberKeyPair.privateKey().bytes(), team.getPrivateKey());
		assertEquals(profile.getName(), team.getName());
		assertEquals(profile.hasAvatar(), team.hasAvatar());
		assertEquals(profile.getNotice(), team.getNotice());
		assertEquals(owner, team.getOwner());
		assertEquals(permission, team.getPermission());
		assertNull(team.getRemark());
		assertNull(team.getTags());
		assertFalse(team.isBlocked());
		assertFalse(team.isMuted());
		assertTrue(team.getCreated() < team.getLastModified());
		assertEquals(profile.getName(), team.getDisplayName());
		assertNotEquals(mapper.writerFor(Contact.class).writeValueAsString(team), mapper.writeValueAsString(team));

		json = mapper.writeValueAsString(team);
		System.out.println(json);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(8, map.size());
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(memberKeyPair.privateKey().bytes()), map.get("k"));
		//assertEquals(profile.getName(), map.get("n"));
		//assertEquals(profile.hasAvatar(), map.get("a"));
		//assertEquals(profile.getNotice(), map.get("nt"));
		assertEquals(owner.toBase58String(), map.get("o"));
		assertEquals(permission.value(), map.get("pm"));
		assertEquals(team.getCreated(), map.get("c"));
		assertEquals(team.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, Contact.class);
		assertTrue(parsed instanceof ChannelImpl);
		assertEquals(team, parsed);

		json = mapper.writerFor(Contact.class).writeValueAsString(team);
		System.out.println(json);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(6, map.size());
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(memberKeyPair.privateKey().bytes()), map.get("k"));
		assertFalse(map.containsKey("n"));
		assertFalse(map.containsKey("a"));
		assertFalse(map.containsKey("nt"));
		assertFalse(map.containsKey("o"));
		assertFalse(map.containsKey("pm"));
		assertEquals(team.getCreated(), map.get("c"));
		assertEquals(team.getLastModified(), map.get("m"));

		// update the local contact info
		team.setMuted(true);
		team.setRemark("BosonNetwork");
		team.setTags("boson, dev");

		assertEquals(channelId, team.getId());
		assertEquals(peerId, team.getHomePeerId());
		assertEquals(Contact.Types.CHANNEL, team.getType());
		assertArrayEquals(memberKeyPair.privateKey().bytes(), team.getPrivateKey());
		assertEquals(profile.getName(), team.getName());
		assertEquals(profile.hasAvatar(), team.hasAvatar());
		assertEquals(profile.getNotice(), team.getNotice());
		assertEquals(owner, team.getOwner());
		assertEquals(permission, team.getPermission());
		assertEquals("BosonNetwork", team.getRemark());
		assertEquals("boson, dev", team.getTags());
		assertFalse(team.isBlocked());
		assertTrue(team.isMuted());
		assertTrue(team.getCreated() < team.getLastModified());
		assertEquals("BosonNetwork", team.getDisplayName());
		assertNotEquals(mapper.writerFor(Contact.class).writeValueAsString(team), mapper.writeValueAsString(team));

		json = mapper.writeValueAsString(team);
		System.out.println(json);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(11, map.size());
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(memberKeyPair.privateKey().bytes()), map.get("k"));
		// assertEquals(profile.getName(), map.get("n"));
		// assertEquals(profile.hasAvatar(), map.get("a"));
		// assertEquals(profile.getNotice(), map.get("nt"));
		assertEquals(owner.toBase58String(), map.get("o"));
		assertEquals(permission.value(), map.get("pm"));
		assertEquals("BosonNetwork", map.get("r"));
		assertEquals("boson, dev", map.get("ts"));
		assertTrue((Boolean)map.get("d"));
		assertEquals(team.getCreated(), map.get("c"));
		assertEquals(team.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, Contact.class);
		assertTrue(parsed instanceof ChannelImpl);
		assertEquals(team, parsed);

		json = mapper.writerFor(Contact.class).writeValueAsString(team);
		System.out.println(json);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(9, map.size());
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(Contact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(memberKeyPair.privateKey().bytes()), map.get("k"));
		assertFalse(map.containsKey("n"));
		assertFalse(map.containsKey("a"));
		assertFalse(map.containsKey("nt"));
		assertFalse(map.containsKey("o"));
		assertFalse(map.containsKey("pm"));
		assertEquals("BosonNetwork", map.get("r"));
		assertEquals("boson, dev", map.get("ts"));
		assertTrue((Boolean)map.get("d"));
		assertEquals(team.getCreated(), map.get("c"));
		assertEquals(team.getLastModified(), map.get("m"));
	}
}
