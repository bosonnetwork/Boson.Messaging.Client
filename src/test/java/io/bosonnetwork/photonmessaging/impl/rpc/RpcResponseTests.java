package io.bosonnetwork.photonmessaging.impl.rpc;

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

public class RpcResponseTests {
	private record SampleResult(@JsonProperty("value") String value, @JsonProperty("count") int count) {
	}

	@Test
	void testSuccessResponse() throws Exception {
		SampleResult result = new SampleResult("ok", 2);
		RpcResponse<SampleResult> response = new RpcResponse<>(2001L, result);

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());
		SampleResult mapped = parsed.getResultAs(SampleResult.class);

		assertEquals(2001L, parsed.getId());
		assertTrue(parsed.succeeded());
		assertFalse(parsed.failed());
		assertNull(parsed.getError());
		assertEquals(result, mapped);
	}

	@Test
	void testErrorResponse() throws Exception {
		RpcResponse<Void> response = new RpcResponse<>(2002L, RpcError.InvalidParameters);

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());

		assertEquals(2002L, parsed.getId());
		assertFalse(parsed.succeeded());
		assertTrue(parsed.failed());
		assertNull(parsed.getResult());
		assertEquals(RpcError.InvalidParameters.getCode(), parsed.getError().getCode());
		assertEquals(RpcError.InvalidParameters.getMessage(), parsed.getError().getMessage());
	}

	@Test
	void testNullResultSuccessBehavior() throws Exception {
		RpcResponse<Void> response = new RpcResponse<>(2003L, (Void) null);

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());

		assertEquals(2003L, parsed.getId());
		assertTrue(parsed.succeeded());
		assertFalse(parsed.failed());
		assertNull(parsed.getResult());
		assertNull(parsed.getError());
	}

	@Test
	void testMalformedBytes() {
		byte[] malformed = new byte[]{0x11, 0x22, 0x33};

		assertThrows(MalformedRpcResponseException.class, () -> GenericRpcResponse.parse(malformed));
	}

	@Test
	void testRejectBothResultAndError() {
		assertThrows(IllegalArgumentException.class,
				() -> new RpcResponse<>(2004L, "ok", RpcError.Timeout));
	}

	@Test
	void testAllowNeitherResultNorError() throws Exception {
		RpcResponse<Void> response = new RpcResponse<>(2005L, (Void) null);

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());

		assertEquals(2005L, parsed.getId());
		assertNull(parsed.getResult());
		assertNull(parsed.getError());
		assertTrue(parsed.succeeded());
		assertFalse(parsed.failed());
	}

	@Test
	void testResultConversionWithClass() throws Exception {
		RpcResponse<SampleResult> response = new RpcResponse<>(2006L, new SampleResult("done", 7));

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());
		SampleResult mapped = parsed.getResultAs(SampleResult.class);

		assertEquals(new SampleResult("done", 7), mapped);
	}

	@Test
	void testResultConversionWithTypeReference() throws Exception {
		List<String> result = List.of("x", "y", "z");
		RpcResponse<List<String>> response = new RpcResponse<>(2007L, result);

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());
		List<String> mapped = parsed.getResultAs(new TypeReference<List<String>>() {
		});

		assertEquals(result, mapped);
	}

	@Test
	void testResultConversionFailure() throws Exception {
		RpcResponse<SampleResult> response = new RpcResponse<>(2008L, new SampleResult("bad", 1));

		GenericRpcResponse parsed = GenericRpcResponse.parse(response.serialize());

		assertThrows(InvalidRpcResultException.class, () -> parsed.getResultAs(Integer.class));
	}

	@Test
	void testNullFieldsAreOmitted() throws Exception {
		RpcResponse<Void> response = new RpcResponse<>(2009L, (Void) null);

		JsonNode tree = Json.cborMapper().readTree(response.serialize());

		assertTrue(tree.has("i"));
		assertFalse(tree.has("r"));
		assertFalse(tree.has("e"));
	}

	@Test
	void testEmptyResultIsNotOmitted() throws Exception {
		RpcResponse<List<String>> response = new RpcResponse<>(2010L, List.of());
 
		JsonNode tree = Json.cborMapper().readTree(response.serialize());
 
		assertTrue(tree.has("r"));
		assertTrue(tree.get("r").isArray());
		assertEquals(0, tree.get("r").size());
	}
 
	@Test
	void testEqualsAndHashCode() {
		RpcResponse<String> res1 = new RpcResponse<>(1L, "ok");
		RpcResponse<String> res2 = new RpcResponse<>(1L, "ok");
		RpcResponse<String> res3 = new RpcResponse<>(1L, RpcError.Timeout);
 
		assertEquals(res1, res2);
		assertEquals(res1.hashCode(), res2.hashCode());
		assertFalse(res1.equals(res3));
 
		RpcError err1 = new RpcError(-1, "msg", "data");
		RpcError err2 = new RpcError(-1, "msg", "data");
		assertEquals(err1, err2);
		assertEquals(err1.hashCode(), err2.hashCode());
	}
 
	@Test
	void testNullNodeBehavior() throws Exception {
		// Test behavior when Jackson deserializes a literal null into a NullNode
		byte[] bytes = Json.cborMapper().writeValueAsBytes(java.util.Map.of("i", 2011L, "r", com.fasterxml.jackson.databind.node.NullNode.getInstance()));
		GenericRpcResponse parsed = GenericRpcResponse.parse(bytes);
		
		assertNull(parsed.getResultAs(String.class));
	}
}