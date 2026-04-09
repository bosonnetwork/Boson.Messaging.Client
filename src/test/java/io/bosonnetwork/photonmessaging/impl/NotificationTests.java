package io.bosonnetwork.photonmessaging.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;

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
	void testSerializationAndParsing() {
		Id deviceId = Id.random();
		Id source = Id.random();
		long timestamp = System.currentTimeMillis();
		Id notificationId = Notification.generateId(deviceId, timestamp);

		// Test Id
		Id contentId = Id.random();
		Notification<Id> nId = new Notification<>(notificationId, Notification.Event.SESSION_NEW, source, timestamp, contentId);
		byte[] data = nId.serialize();
		Notification.GenericNotification parsed = Notification.parse(data);
		assertEquals(contentId, parsed.getContentAs(Id.class));

		// Test List<Id>
		List<Id> contentList = List.of(Id.random(), Id.random());
		Notification<List<Id>> nList = new Notification<>(notificationId, Notification.Event.CONTACT_SYNC, source, timestamp, contentList);
		data = nList.serialize();
		parsed = Notification.parse(data);
		assertEquals(contentList, parsed.getContentAsListOf(Id.class));

		// Test String
		String contentStr = "Hello Boson";
		Notification<String> nStr = new Notification<>(notificationId, Notification.Event.FRIEND_REQUEST, source, timestamp, contentStr);
		data = nStr.serialize();
		parsed = Notification.parse(data);
		assertEquals(contentStr, parsed.getContentAs(String.class));

		// Test Boolean
		Notification<Boolean> nBool = new Notification<>(notificationId, Notification.Event.CHANNEL_JOIN, source, timestamp, true);
		data = nBool.serialize();
		parsed = Notification.parse(data);
		assertTrue(parsed.getContentAs(Boolean.class));

		// Test Map<String, Object>
		Map<String, Object> contentMap = Map.of("key1", "value1", "key2", 123);
		Notification<Map<String, Object>> nMap = new Notification<>(notificationId, Notification.Event.CHANNEL_UPDATE_INFO, source, timestamp, contentMap);
		data = nMap.serialize();
		parsed = Notification.parse(data);
		Map<?, ?> parsedMap = parsed.getContentAs(Map.class);
		assertEquals(contentMap.get("key1"), parsedMap.get("key1"));
		assertEquals(contentMap.get("key2"), parsedMap.get("key2"));
	}

	@Test
	void testIdGenerationAndAssociation() {
		Id deviceId = Id.random();
		long timestamp = System.currentTimeMillis();
		Id notificationId = Notification.generateId(deviceId, timestamp);

		Notification<String> notification = new Notification<>(notificationId, Notification.Event.SESSION_NEW, Id.random(), timestamp, "test");

		assertTrue(notification.isAssociated(deviceId));
		assertFalse(notification.isAssociated(Id.random()));

		// Test with wrong timestamp
		Notification<String> wrongTimestamp = new Notification<>(notificationId, Notification.Event.SESSION_NEW, Id.random(), timestamp + 1, "test");
		assertFalse(wrongTimestamp.isAssociated(deviceId));
	}
}