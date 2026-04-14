package io.bosonnetwork.photonmessaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Contact;

public class ContactMutationTests {
	private static Stream<Arguments> mutationProvider() {
		List<Arguments> mutations = new ArrayList<>();

		// ADD
		Friend friend = new Friend(Id.random(), Random.randomBytes(PhotonContact.ENCRYPTED_SESSION_KEY_BYTES), "John Doe");
		mutations.add(Arguments.of(ContactMutation.Op.ADD, ContactMutation.add(1, friend)));

		// UPDATE
		JsonNode update = Json.objectMapper().createObjectNode()
				.put("id", Id.random().bytes())
				.put("n", "Jane Doe")
				.put("r", "Updated Remark");
		mutations.add(Arguments.of(ContactMutation.Op.UPDATE, ContactMutation.update(2, update)));

		// REMOVE
		List<Id> ids = List.of(Id.random(), Id.random());
		mutations.add(Arguments.of(ContactMutation.Op.REMOVE, ContactMutation.remove(3, ids)));

		// CLEAR
		mutations.add(Arguments.of(ContactMutation.Op.CLEAR, ContactMutation.clear(4)));

		return mutations.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("mutationProvider")
	void testSerialization(ContactMutation.Op op, ContactMutation mutation) throws Exception {
		assertEquals(op, mutation.getOp()); // make sure the test data is correct
		System.out.println(Json.toString(mutation));

		byte[] data = Json.cborMapper().writeValueAsBytes(mutation);
		ContactMutation parsed = Json.cborMapper().readValue(data, ContactMutation.class);

		assertEquals(mutation.getRevision(), parsed.getRevision());
		assertEquals(mutation.getOp(), parsed.getOp());

		Object expectedData = mutation.getData();
		Object actualData = parsed.getData();

		switch (mutation.getOp()) {
			case ADD -> assertInstanceOf(Contact.class, actualData);
			case UPDATE -> assertInstanceOf(JsonNode.class, actualData);
			case REMOVE -> assertInstanceOf(List.class, actualData);
			case CLEAR -> assertNull(actualData);
		}

		assertThat(actualData)
				.usingRecursiveComparison()
				.withComparatorForType(Id::compare, Id.class)
				.isEqualTo(expectedData);
	}
}