package io.bosonnetwork.photonmessaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.SessionInfo;

public class NotificationTests {
	@Test
	void testNotificationEvents() {
		Set<Notification.Event> events = EnumSet.allOf(Notification.Event.class);

		for (Notification.Event event : events) {
			Notification.Event e = Notification.Event.valueOf(event.value());
			assertSame(event, e);
		}
	}

	@Test
	void testNotificationSerialization() {
		SessionInfo sessionInfo = new SessionInfo(Id.random(), true, System.currentTimeMillis());
		testSerialization(Notification.Event.SESSION_NEW, sessionInfo);

		ContactSync contactSync = new ContactSync(10, ContactSync.Type.UP_TO_DATE, null, null);
		testSerialization(Notification.Event.CONTACT_SYNC, contactSync);

		ContactMutation mutation = ContactMutation.remove(9, List.of(Id.random(), Id.random()));
		testSerialization(Notification.Event.CONTACT_MUTATE, mutation);

		ChannelInfo channelInfo = new ChannelInfo(Id.random(), Id.random(),
				Id.random(), null, Channel.Permission.MEMBER_INVITE, "Test Channel", null,
				false, System.currentTimeMillis(), System.currentTimeMillis(), null);
		testSerialization(Notification.Event.CHANNEL_CREATE, channelInfo);

		testSerialization(Notification.Event.CHANNEL_DELETE, null);
		testSerialization(Notification.Event.CHANNEL_JOIN, channelInfo);
		testSerialization(Notification.Event.CHANNEL_LEAVE, null);
		testSerialization(Notification.Event.CHANNEL_OWNERSHIP_TRANSFER, Id.random());
		testSerialization(Notification.Event.CHANNEL_SESSION_KEY_ROTATE, Random.randomBytes(64));

		JsonNode changes = Json.cborMapper().createObjectNode().put("test", "value");
		testSerialization(Notification.Event.CHANNEL_INFO_UPDATE, changes);

		ChannelMembersRole membersRole = new ChannelMembersRole(
				List.of(Id.random(), Id.random()), Channel.Role.MODERATOR);
		testSerialization(Notification.Event.CHANNEL_MEMBERS_ROLE_UPDATE, membersRole);

		testSerialization(Notification.Event.CHANNEL_MEMBERS_BAN, List.of(Id.random(), Id.random()));
		testSerialization(Notification.Event.CHANNEL_MEMBERS_UNBAN, List.of(Id.random()));
		testSerialization(Notification.Event.CHANNEL_MEMBERS_REMOVE, List.of(Id.random(), Id.random()));
		testSerialization(Notification.Event.CHANNEL_MEMBER_JOIN, new ChannelMember(Id.random(), Channel.Role.MEMBER, System.currentTimeMillis()));
		testSerialization(Notification.Event.CHANNEL_MEMBER_LEAVE, Id.random());
		testSerialization(Notification.Event.FRIEND_REQUEST, "Hello from test");
		testSerialization(Notification.Event.FRIEND_REQUEST_ACCEPT, Random.randomBytes(64));
	}

	private void testSerialization(Notification.Event event, Object body) {
		Notification notif = new Notification(Id.random(), Id.random(), System.currentTimeMillis(), event, body);
		System.out.println(Json.toString(notif));

		Notification parsed = Notification.parse(notif.serialize());

		assertEquals(notif.getId(), parsed.getId());
		assertEquals(notif.getSource(), parsed.getSource());
		assertEquals(notif.getTimestamp(), parsed.getTimestamp());
		assertEquals(event, parsed.getEvent());

		Object parsedBody = parsed.getBody();
		if (body instanceof byte[]) {
			assertArrayEquals((byte[]) body, (byte[]) parsedBody);
		} else if (body instanceof JsonNode) {
			assertEquals(body.toString(), parsedBody.toString());
		} else if (body instanceof ContactSync) {
			assertThat(parsedBody)
					.usingRecursiveComparison()
					.isEqualTo(body);
		} else if (body instanceof ContactMutation) {
			assertThat(parsedBody)
					.usingRecursiveComparison()
					.ignoringFieldsMatchingRegexes(".*b58")
					.isEqualTo(body);
		} else {
			assertEquals(body, parsedBody);
		}
	}

	@Test
	void testFriendRequest() {
		Id userId = Id.random();
		Id deviceId = Id.random();

		String hello = "Hello from test";
		Notification notif = Notification.friendRequest(userId, deviceId, hello);
		assertTrue(notif.isAssociated(deviceId));
		assertFalse(notif.isAssociated(Id.random()));

		Notification parsed = Notification.parse(notif.serialize());

		assertEquals(notif.getId(), parsed.getId());
		assertEquals(notif.getSource(), parsed.getSource());
		assertEquals(notif.getTimestamp(), parsed.getTimestamp());
		assertEquals(Notification.Event.FRIEND_REQUEST, parsed.getEvent());
		assertEquals(hello, parsed.getBody());
	}

	@Test
	void testFriendRequestAccept() {
		Id userId = Id.random();
		Id deviceId = Id.random();

		byte[] sessionKey = Random.randomBytes(64);
		Notification notif = Notification.friendRequestAccept(userId, deviceId, sessionKey);
		assertTrue(notif.isAssociated(deviceId));
		assertFalse(notif.isAssociated(Id.random()));

		Notification parsed = Notification.parse(notif.serialize());

		assertEquals(notif.getId(), parsed.getId());
		assertEquals(notif.getSource(), parsed.getSource());
		assertEquals(notif.getTimestamp(), parsed.getTimestamp());
		assertEquals(Notification.Event.FRIEND_REQUEST_ACCEPT, parsed.getEvent());
		assertArrayEquals(sessionKey, parsed.getBody());
	}
}