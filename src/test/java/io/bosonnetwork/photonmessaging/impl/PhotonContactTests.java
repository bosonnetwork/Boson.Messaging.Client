package io.bosonnetwork.photonmessaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Contact;

public class PhotonContactTests {
	private static Stream<Arguments> contactProvider() {
		List<Arguments> contacts = new ArrayList<>();

		// Friend
		contacts.add(Arguments.of("Friend",
				new Friend(Id.random(), Random.randomBytes(PhotonContact.ENCRYPTED_SESSION_KEY_BYTES), "Friend Remark")));

		// PhotonChannel
		contacts.add(Arguments.of("Channel",
				new PhotonChannel(Id.random(), Random.randomBytes(PhotonContact.ENCRYPTED_SESSION_KEY_BYTES), Id.random(),
						Channel.Permission.PUBLIC, "Group Name", "Group Notice",
						true, System.currentTimeMillis(), 0)));

		// AutoContact
		contacts.add(Arguments.of("AutoContact",
				new AutoContact(Id.random(), "Auto Name", null, null,
						null, false, false, System.currentTimeMillis(), 0)));

		return contacts.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("contactProvider")
	void testSerialization(String name, PhotonContact contact) throws Exception {
		System.out.println(Json.toString(contact));
		byte[] data = Json.cborMapper().writeValueAsBytes(contact);
		PhotonContact parsed = Json.cborMapper().readValue(data, PhotonContact.class);

		assertThat(parsed)
				.usingRecursiveComparison()
				.withComparatorForType(Id::compare, Id.class)
				.isEqualTo(contact);
	}

	@Test
	void testFriendPropertiesAndEditing() {
		Id id = Id.random();
		byte[] sk = Random.randomBytes(PhotonContact.ENCRYPTED_SESSION_KEY_BYTES);
		String remark = "My Friend";
		Friend friend = new Friend(id, sk, remark);

		assertEquals(id, friend.getId());
		assertEquals(sk, friend.getSessionKey());
		assertEquals(remark, friend.getRemark());
		assertEquals(Contact.Type.FRIEND, friend.getType());
		assertFalse(friend.isMuted());
		assertFalse(friend.isBlocked());

		// Edit
		String newName = "New Name";
		String newRemark = "New Remark";
		Friend modified = (Friend) friend.edit()
				.setName(newName)
				.setRemark(newRemark)
				.setMuted(true)
				.build();

		assertEquals(id, modified.getId());
		assertEquals(newName, modified.getName());
		assertEquals(newRemark, modified.getRemark());
		assertTrue(modified.isMuted());
		assertFalse(modified.isBlocked());
		assertTrue(modified.getUpdatedAt() >= friend.getUpdatedAt());

		// No changes
		Friend notModified = (Friend) friend.edit().build();
		assertThat(notModified).isSameAs(friend);
	}

	@Test
	void testPhotonChannelPropertiesAndEditing() {
		Id id = Id.random();
		byte[] sk = Random.randomBytes(PhotonContact.ENCRYPTED_SESSION_KEY_BYTES);
		Id owner = Id.random();
		String name = "Test Channel";
		String notice = "Test Notice";
		PhotonChannel channel = new PhotonChannel(id, sk, owner, Channel.Permission.PUBLIC, name, notice,
				true, System.currentTimeMillis(), 0);

		assertEquals(id, channel.getId());
		assertEquals(sk, channel.getSessionKey());
		assertEquals(owner, channel.getOwnerId());
		assertEquals(Channel.Permission.PUBLIC, channel.getPermission());
		assertEquals(name, channel.getName());
		assertEquals(notice, channel.getNotice());
		assertTrue(channel.isAnnounce());
		assertEquals(Contact.Type.CHANNEL, channel.getType());

		// Edit Channel properties
		String newName = "New Name";
		String newNotice = "New Notice";
		PhotonChannel modified = channel.editChannel()
				.setName(newName)
				.setNotice(newNotice)
				.setPermission(Channel.Permission.MEMBER_INVITE)
				.setAnnounce(false)
				.build();

		assertEquals(id, modified.getId());
		assertEquals(newName, modified.getName());
		assertEquals(newNotice, modified.getNotice());
		assertEquals(Channel.Permission.MEMBER_INVITE, modified.getPermission());
		assertFalse(modified.isAnnounce());
		assertTrue(modified.getUpdatedAt() >= channel.getUpdatedAt());

		// Edit generic properties
		PhotonChannel modifiedCommon = (PhotonChannel) channel.edit()
				.setRemark("Channel Remark")
				.setBlocked(true)
				.build();
		assertEquals("Channel Remark", modifiedCommon.getRemark());
		assertTrue(modifiedCommon.isBlocked());

		// No changes
		PhotonChannel notModified = channel.editChannel().build();
		assertThat(notModified).isSameAs(channel);
	}

	@Test
	void testAutoContactPropertiesAndEditing() {
		Id id = Id.random();
		String name = "Auto Bot";
		AutoContact auto = new AutoContact(id, name, null, null,
				null, false, false, System.currentTimeMillis(), 0);

		assertEquals(id, auto.getId());
		assertNull(auto.getSessionKey());
		assertEquals(name, auto.getName());
		assertEquals(Contact.Type.AUTO, auto.getType());
		assertEquals(-1, auto.getRevision());

		// Edit
		String newName = "Manual Bot";
		String newRemark = "Bot Remark";
		AutoContact modified = (AutoContact) auto.edit()
				.setName(newName)
				.setRemark(newRemark)
				.setBlocked(true).build();

		assertEquals(id, modified.getId());
		assertEquals(newName, modified.getName());
		assertEquals(newRemark, modified.getRemark());
		assertTrue(modified.isBlocked());
	}
}