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

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.crypto.Signature.PrivateKey;
import io.bosonnetwork.messaging.impl.ChannelImpl;
import io.bosonnetwork.messaging.impl.ContactImpl;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;

public class ContactTests {
	private static Map<Id, PrivateKey> keys = new HashMap<>();
	private static Map<Id, AbstractContact> contacts = new HashMap<>();
	private static Base64.Encoder b64Encoder = Base64.getUrlEncoder().withoutPadding();

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
		//md.update(homePeerSig);
		if (name != null)
			md.update(name.getBytes(UTF_8));
		md.update(avatar ? (byte)1 : (byte)0);
		if (notice != null)
			md.update(notice.getBytes(UTF_8));
		byte[] sig = keys.get(id).sign(md.digest());

		return new Profile(id, homePeerId, name, avatar, notice, homePeerSig, sig);
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

		var sessionKey = keyPair.privateKey().bytes();

		// new contact with id
		var alice = ContactImpl.create(contactId, null, sessionKey, null, false);
		alice.setSynced();
		contacts.put(contactId, alice);

		assertEquals(1, alice.getRevision());
		assertEquals(contactId, alice.getId());
		assertNull(alice.getHomePeerId());
		assertEquals(AbstractContact.Types.CONTACT, alice.getType());
		assertArrayEquals(sessionKey, alice.getSessionKey());
		assertNull(alice.getName());
		assertFalse(alice.hasAvatar());
		assertNull(alice.getRemark());
		assertNull(alice.getTags());
		assertFalse(alice.isBlocked());
		assertFalse(alice.isMuted());
		assertTrue(alice.getCreated() == alice.getLastModified());
		assertTrue(contactId.toBase58String().startsWith(alice.getDisplayName().substring(0, 4)));
		assertEquals(mapper.writerFor(AbstractContact.class).writeValueAsString(alice), mapper.writeValueAsString(alice));

		var json = mapper.writeValueAsString(alice);
		var map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(6, map.size());
		assertEquals(1, (Integer)map.get("v"));
		assertEquals(contactId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CONTACT, (Integer)map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
		assertEquals(alice.getCreated(), (Long)map.get("c"));
		assertEquals(alice.getLastModified(), (Long)map.get("m"));

		var parsed = mapper.readValue(json, AbstractContact.class);
		assertTrue(parsed instanceof ContactImpl);
		assertEquals(alice, parsed);

		// update profile
		Profile profile = createProfile(contactId, peerId, "Alice", true);

		alice.update(profile);
		assertEquals(1, alice.getRevision());
		assertEquals(contactId, alice.getId());
		assertEquals(peerId, alice.getHomePeerId());
		assertEquals(AbstractContact.Types.CONTACT, alice.getType());
		assertArrayEquals(sessionKey, alice.getSessionKey());
		assertEquals(profile.getName(), alice.getName());
		assertEquals(profile.hasAvatar(), alice.hasAvatar());
		assertNull(alice.getRemark());
		assertNull(alice.getTags());
		assertFalse(alice.isBlocked());
		assertFalse(alice.isMuted());
		assertTrue(alice.getCreated() == alice.getLastModified());
		assertTrue(alice.getLastModified() < alice.getLastUpdated());
		assertEquals(profile.getName(), alice.getDisplayName());
		assertEquals(mapper.writerFor(AbstractContact.class).writeValueAsString(alice), mapper.writeValueAsString(alice));

		json = mapper.writeValueAsString(alice);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(6, map.size());
		assertEquals(1, (Integer)map.get("v"));
		assertEquals(contactId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CONTACT, map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
		assertEquals(alice.getCreated(), map.get("c"));
		assertEquals(alice.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, AbstractContact.class);
		parsed.update(profile);
		assertTrue(parsed instanceof ContactImpl);
		assertEquals(alice, parsed);

		// update the local contact info
		alice.setMuted(true);
		alice.setBlocked(true);
		alice.setRemark("Alice Brown");
		alice.setTags("foo,bar");

		assertEquals(2, alice.getRevision());
		assertEquals(contactId, alice.getId());
		assertEquals(peerId, alice.getHomePeerId());
		assertEquals(AbstractContact.Types.CONTACT, alice.getType());
		assertEquals(profile.getName(), alice.getName());
		assertEquals(profile.hasAvatar(), alice.hasAvatar());
		assertEquals("Alice Brown", alice.getRemark());
		assertEquals("foo,bar", alice.getTags());
		assertTrue(alice.isBlocked());
		assertTrue(alice.isMuted());
		assertTrue(alice.getCreated() < alice.getLastModified());
		assertTrue(alice.getCreated() < alice.getLastUpdated());
		assertEquals("Alice Brown", alice.getDisplayName());
		assertEquals(mapper.writerFor(AbstractContact.class).writeValueAsString(alice), mapper.writeValueAsString(alice));

		json = mapper.writeValueAsString(alice);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(10, map.size());
		assertEquals(2, (Integer)map.get("v"));
		assertEquals(contactId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CONTACT, map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
		assertEquals("Alice Brown", map.get("r"));
		assertEquals("foo,bar", map.get("ts"));
		assertTrue((Boolean)map.get("d"));
		assertTrue((Boolean)map.get("b"));
		assertEquals(alice.getCreated(), map.get("c"));
		assertEquals(alice.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, AbstractContact.class);
		parsed.update(profile);
		assertTrue(parsed instanceof ContactImpl);
		assertEquals(alice, parsed);
	}

	@Test
	void testChannel() throws IOException {
		var mapper = Json.objectMapper();

		var owner = Id.random();
		var permission = Channel.Permission.MODERATOR_INVITE;

		var channelKeyPair = KeyPair.random();
		var channelId = Id.of(channelKeyPair.publicKey().bytes());
		keys.put(channelId, channelKeyPair.privateKey());

		var peerKeyPair = KeyPair.random();
		var peerId = Id.of(peerKeyPair.publicKey().bytes());
		keys.put(peerId, peerKeyPair.privateKey());

		var sessionKey = KeyPair.random().privateKey().bytes();

		// new group with id
		Channel team = ChannelImpl.create(channelId, peerId, sessionKey, null, false);

		// will update the last modified timestamp but will keep the revision until setSynced
		team.setOwner(owner);
		team.setPermission(permission);
		team.setSynced();

		assertEquals(1, team.getRevision());
		assertEquals(channelId, team.getId());
		assertEquals(AbstractContact.Types.CHANNEL, team.getType());
		assertArrayEquals(sessionKey, team.getSessionKey());
		assertNull(team.getName());
		assertFalse(team.hasAvatar());
		assertNull(team.getNotice());
		assertEquals(owner, team.getOwner());
		assertEquals(permission, team.getPermission());
		assertNull(team.getRemark());
		assertNull(team.getTags());
		assertFalse(team.isBlocked());
		assertFalse(team.isMuted());
		assertTrue(team.getCreated() < team.getLastModified());
		assertTrue(channelId.toBase58String().startsWith(team.getDisplayName().substring(0, 4)));
		assertNotEquals(mapper.writerFor(AbstractContact.class).writeValueAsString(team), mapper.writeValueAsString(team));

		var json = mapper.writeValueAsString(team);
		var map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(8, map.size());
		assertEquals(1, (Integer)map.get("v"));
		assertEquals(channelId.toBase58String(), map.get("id"));
		// assertEquals(peerId.toBase58String(), map.get("p"));
		assertEquals(AbstractContact.Types.CHANNEL, (Integer)map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
		assertEquals(owner.toBase58String(), map.get("o"));
		assertEquals(permission.value(), map.get("pm"));
		assertEquals(team.getCreated(), (Long)map.get("c"));
		assertEquals(team.getLastModified(), (Long)map.get("m"));

		var parsed = mapper.readValue(json, AbstractContact.class);
		parsed.setHomePeerId(peerId);
		assertTrue(parsed instanceof ChannelImpl);
		assertEquals(team, parsed);


		Profile profile = createProfile(channelId, peerId, "Boson dev team", true, "Boson dev collaboration");
		// fetch and update group info
		team.update(profile);
		assertEquals(1, team.getRevision());
		assertEquals(channelId, team.getId());
		assertEquals(peerId, team.getHomePeerId());
		assertEquals(AbstractContact.Types.CHANNEL, team.getType());
		assertArrayEquals(sessionKey, team.getSessionKey());
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
		assertNotEquals(mapper.writerFor(AbstractContact.class).writeValueAsString(team), mapper.writeValueAsString(team));

		json = mapper.writeValueAsString(team);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(8, map.size());
		assertEquals(1, (Integer)map.get("v"));
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
		assertEquals(owner.toBase58String(), map.get("o"));
		assertEquals(permission.value(), map.get("pm"));
		assertEquals(team.getCreated(), map.get("c"));
		assertEquals(team.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, AbstractContact.class);
		parsed.update(profile);
		assertTrue(parsed instanceof ChannelImpl);
		assertEquals(team, parsed);

		json = mapper.writerFor(AbstractContact.class).writeValueAsString(team);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(6, map.size());
		assertEquals(1, (Integer)map.get("v"));
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
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

		assertEquals(2, team.getRevision());
		assertEquals(channelId, team.getId());
		assertEquals(peerId, team.getHomePeerId());
		assertEquals(AbstractContact.Types.CHANNEL, team.getType());
		assertArrayEquals(sessionKey, team.getSessionKey());
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
		assertNotEquals(mapper.writerFor(AbstractContact.class).writeValueAsString(team), mapper.writeValueAsString(team));

		json = mapper.writeValueAsString(team);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(11, map.size());
		assertEquals(2, (Integer)map.get("v"));
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
		assertEquals(owner.toBase58String(), map.get("o"));
		assertEquals(permission.value(), map.get("pm"));
		assertEquals("BosonNetwork", map.get("r"));
		assertEquals("boson, dev", map.get("ts"));
		assertTrue((Boolean)map.get("d"));
		assertEquals(team.getCreated(), map.get("c"));
		assertEquals(team.getLastModified(), map.get("m"));

		parsed = mapper.readValue(json, AbstractContact.class);
		parsed.update(profile);
		assertTrue(parsed instanceof ChannelImpl);
		assertEquals(team, parsed);

		json = mapper.writerFor(AbstractContact.class).writeValueAsString(team);
		map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		assertEquals(9, map.size());
		assertEquals(2, (Integer)map.get("v"));
		assertEquals(channelId.toBase58String(), map.get("id"));
		assertEquals(AbstractContact.Types.CHANNEL, map.get("t"));
		assertEquals(b64Encoder.encodeToString(sessionKey), map.get("sk"));
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