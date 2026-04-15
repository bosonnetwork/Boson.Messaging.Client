package io.bosonnetwork.photonmessaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;

public class HandshakeTests {
	@Test
	void testHandshakeTypes() {
		Set<Handshake.Type> types = EnumSet.allOf(Handshake.Type.class);

		for (Handshake.Type type : types) {
			Handshake.Type t = Handshake.Type.valueOf(type.value());
			assertSame(type, t);
		}
	}

	private static Stream<Arguments> handshakeProvider() {
		List<Arguments> handshakes = new ArrayList<>();

		handshakes.add(Arguments.of(Handshake.Type.FRIEND_REQUEST,
				Handshake.friendRequest("Hello from test", System.currentTimeMillis())));
		handshakes.add(Arguments.of(Handshake.Type.FRIEND_REQUEST_ACCEPT,
				Handshake.friendRequestAccept(Random.randomBytes(64), System.currentTimeMillis())));

		return handshakes.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("handshakeProvider")
	void testSerialization(Handshake.Type type, Handshake hs) {
		assertEquals(type, hs.getType()); // make sure the test data is correct
		System.out.println(Json.toString(hs));

		Handshake parsed = Handshake.parse(hs.serialize());

		assertEquals(hs.getTimestamp(), parsed.getTimestamp());
		assertEquals(type, parsed.getType());

		Object body = hs.getBody();
		Object parsedBody = parsed.getBody();
		assertThat(parsedBody)
				.usingRecursiveComparison()
				//.withComparatorForType(Id::compare, Id.class)
				.isEqualTo(body);
	}
}