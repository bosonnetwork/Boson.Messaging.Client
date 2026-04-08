package io.bosonnetwork.photonmessaging.impl.rpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.json.Json;

public class RpcRequestTests {
	private record SampleParams(@JsonProperty("name") String name, @JsonProperty("age") int age) {
	}

	@Test
	void testNoParams() throws Exception {
		RpcRequest<Void> request = new RpcRequest<>(1001L, RpcMethod.SESSION_LIST);

		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());

		assertEquals(1001L, parsed.getId());
		assertEquals(RpcMethod.SESSION_LIST, parsed.getMethod());
		assertNull(parsed.getParams());
		assertNull(parsed.getCookie());
	}

	@Test
	void testScalarParams() throws Exception {
		RpcRequest<Integer> request = new RpcRequest<>(1002L, RpcMethod.SESSION_REVOKE, 42);

		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());

		assertEquals(42, parsed.getParamsAs(Integer.class));
	}

	@Test
	void testObjectParams() throws Exception {
		SampleParams params = new SampleParams("Alice", 18);
		RpcRequest<SampleParams> request = new RpcRequest<>(1003L, RpcMethod.CONTACT_MUTATE, params);

		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());
		SampleParams mapped = parsed.getParamsAs(SampleParams.class);

		assertEquals(params, mapped);
	}

	@Test
	void testListParams() throws Exception {
		List<String> params = List.of("a", "b", "c");
		RpcRequest<List<String>> request = new RpcRequest<>(1004L, RpcMethod.CONTACT_MUTATE, params);

		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());
		List<String> mapped = parsed.getParamsAs(new TypeReference<List<String>>() {});

		assertEquals(params, mapped);
	}

	@Test
	void testWithCookie() throws Exception {
		byte[] cookie = new byte[] { 1, 2, 3, 4 };
		RpcRequest<String> request = new RpcRequest<>(1005L, RpcMethod.CHANNEL_JOIN, "room-1", cookie);

		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());

		assertEquals("room-1", parsed.getParamsAs(String.class));
		assertArrayEquals(cookie, parsed.getCookie());
	}

	@Test
	void testMalformedBytes() {
		byte[] malformed = new byte[] { 0x01, 0x02, 0x03 };

		assertThrows(MalformedRpcRequestException.class, () -> GenericRpcRequest.parse(malformed));
	}

	@Test
	void testInvalidMethodValue() throws Exception {
		byte[] bytes = Json.cborMapper().writeValueAsBytes(
				java.util.Map.of(
						"i", 1006L,
						"m", 999,
						"p", "x"
				));

		assertThrows(MalformedRpcRequestException.class, () -> GenericRpcRequest.parse(bytes));
	}

	@Test
	void testPayloadConversionFailure() throws Exception {
		RpcRequest<SampleParams> request = new RpcRequest<>(1007L, RpcMethod.CONTACT_MUTATE, new SampleParams("bob", 20));

		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());

		assertThrows(InvalidRpcParametersException.class, () -> parsed.getParamsAs(Integer.class));
	}

	@Test
	void testNullFieldsAreOmitted() throws Exception {
		RpcRequest<Void> request = new RpcRequest<>(1008L, RpcMethod.SESSION_LIST);

		JsonNode tree = Json.cborMapper().readTree(request.serialize());

		assertTrue(tree.has("i"));
		assertTrue(tree.has("m"));
		assertFalse(tree.has("p"));
		assertFalse(tree.has("c"));
	}

	@Test
	void testEmptyFieldsAreNotOmitted() throws Exception {
		RpcRequest<List<String>> request = new RpcRequest<>(1009L, RpcMethod.CONTACT_MUTATE, List.of(), new byte[0]);
 
		JsonNode tree = Json.cborMapper().readTree(request.serialize());
 
		assertTrue(tree.has("p"));
		assertTrue(tree.get("p").isArray());
		assertEquals(0, tree.get("p").size());
 
		assertTrue(tree.has("c"));
		assertTrue(tree.get("c").isBinary() || tree.get("c").isTextual());
	}
 
	@Test
	void testNegativeAndMaxIds() throws Exception {
		RpcRequest<Void> req1 = new RpcRequest<>(-1L, RpcMethod.SESSION_LIST);
		GenericRpcRequest parsed1 = GenericRpcRequest.parse(req1.serialize());
		assertEquals(-1L, parsed1.getId());
 
		RpcRequest<Void> req2 = new RpcRequest<>(Long.MAX_VALUE, RpcMethod.SESSION_LIST);
		GenericRpcRequest parsed2 = GenericRpcRequest.parse(req2.serialize());
		assertEquals(Long.MAX_VALUE, parsed2.getId());
	}
 
	@Test
	void testDeeplyNestedParams() throws Exception {
		var nested = java.util.Map.of("a", java.util.Map.of("b", java.util.Map.of("c", "d")));
		RpcRequest<Object> request = new RpcRequest<>(1010L, RpcMethod.CONTACT_MUTATE, nested);
 
		GenericRpcRequest parsed = GenericRpcRequest.parse(request.serialize());
		Object mapped = parsed.getParamsAs(Object.class);
 
		assertEquals(nested, mapped);
	}
 
	@Test
	void testEqualsAndHashCode() {
		byte[] cookie = new byte[] { 1, 2 };
		RpcRequest<String> req1 = new RpcRequest<>(1L, RpcMethod.SESSION_LIST, "p", cookie);
		RpcRequest<String> req2 = new RpcRequest<>(1L, RpcMethod.SESSION_LIST, "p", cookie);
		RpcRequest<String> req3 = new RpcRequest<>(2L, RpcMethod.SESSION_LIST, "p", cookie);
 
		assertEquals(req1, req2);
		assertEquals(req1.hashCode(), req2.hashCode());
		assertFalse(req1.equals(req3));
	}
}