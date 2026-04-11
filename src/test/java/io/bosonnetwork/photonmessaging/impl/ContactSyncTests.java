package io.bosonnetwork.photonmessaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Channel;

public class ContactSyncTests {
	private static Stream<Arguments> syncProvider() {
		List<Arguments> syncs = new ArrayList<>();

		// UP_TO_DATE
		syncs.add(Arguments.of(ContactSync.Type.UP_TO_DATE,
				new ContactSync(10, ContactSync.Type.UP_TO_DATE, null, null)));

		// DELTA
		List<ContactMutation> mutations = List.of(
				ContactMutation.clear(10),
				ContactMutation.remove(11, List.of(Id.random()))
		);
		syncs.add(Arguments.of(ContactSync.Type.DELTA,
				new ContactSync(11, ContactSync.Type.DELTA, mutations, null)));

		// SNAPSHOT
		Friend friend = new Friend(Id.random(), Random.randomBytes(64), "Friend Remark");
		PhotonChannel channel = new PhotonChannel(Id.random(), Random.randomBytes(64), Id.random(),
				Channel.Permission.PUBLIC, "Group Name", "Group Notice", true, System.currentTimeMillis(), 0);
		AutoContact auto = new AutoContact(Id.random(), "Auto Name", null, null, null, false, false, System.currentTimeMillis(), 0);

		syncs.add(Arguments.of(ContactSync.Type.SNAPSHOT,
				new ContactSync(12, ContactSync.Type.SNAPSHOT, null, List.of(friend, channel, auto))));

		return syncs.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("syncProvider")
	void testSerialization(ContactSync.Type type, ContactSync sync) throws Exception {
		assertEquals(type, sync.getType()); // make sure the test data is correct
		System.out.println(Json.toString(sync));

		byte[] data = Json.cborMapper().writeValueAsBytes(sync);
		ContactSync parsed = Json.cborMapper().readValue(data, ContactSync.class);

		assertEquals(sync.getRevision(), parsed.getRevision());
		assertEquals(sync.getType(), parsed.getType());

		if (sync.getMutations() != null) {
			assertThat(parsed.getMutations())
					.usingRecursiveComparison()
					.withComparatorForType(Id::compare, Id.class)
					.isEqualTo(sync.getMutations());
		} else {
			assertNull(parsed.getMutations());
		}

		if (sync.getContacts() != null) {
			assertThat(parsed.getContacts())
					.usingRecursiveComparison()
					.withComparatorForType(Id::compare, Id.class)
					.isEqualTo(sync.getContacts());
		} else {
			assertNull(parsed.getContacts());
		}
	}
}