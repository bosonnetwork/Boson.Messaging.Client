package io.bosonnetwork.photonmessaging.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.json.Json;

public class MessageContentTests {
	@Test
	public void testTextContent() {
		String text = "Hello Photon";

		MessageContent content = MessageContent.text(text);
		System.out.println(Json.toString(content));
		assertEquals(MessageContent.Format.TEXT, content.getFormat());
		assertTrue(content.getHeaders().isEmpty());
		assertEquals(text, content.getBody());
		assertEquals(text, content.asText());

		MessageContent content2 = MessageContent.parse(content.serialize());
		assertEquals(MessageContent.Format.TEXT, content2.getFormat());
		assertTrue(content2.getHeaders().isEmpty());
		assertEquals(text, content2.getBody());
		assertEquals(text, content2.asText());

		MessageContent contentWithHeaders = MessageContent.text(Map.of("Custom-Header", "Value"), text);
		System.out.println(Json.toString(contentWithHeaders));
		assertEquals(MessageContent.Format.TEXT, contentWithHeaders.getFormat());
		assertEquals("Value", contentWithHeaders.getHeaders().get("Custom-Header"));
		assertEquals(text, contentWithHeaders.getBody());
		assertEquals(text, contentWithHeaders.asText());

		MessageContent contentWithHeaders2 = MessageContent.parse(contentWithHeaders.serialize());
		assertEquals(MessageContent.Format.TEXT, contentWithHeaders2.getFormat());
		assertEquals("Value", contentWithHeaders2.getHeaders().get("Custom-Header"));
		assertEquals(text, contentWithHeaders2.getBody());
		assertEquals(text, contentWithHeaders2.asText());
	}

	@Test
	public void testBinaryContent() {
		String text = "Hello Photon";
		byte[] data = text.getBytes(StandardCharsets.UTF_8);

		MessageContent content = MessageContent.binary(data);
		System.out.println(Json.toString(content));
		assertEquals(MessageContent.Format.BINARY, content.getFormat());
		assertTrue(content.getHeaders().isEmpty());
		assertArrayEquals(data, content.getBody());
		assertArrayEquals(data, content.asBinary());
		assertEquals(text, content.asText());

		MessageContent content2 = MessageContent.parse(content.serialize());
		assertEquals(MessageContent.Format.BINARY, content2.getFormat());
		assertTrue(content2.getHeaders().isEmpty());
		assertArrayEquals(data, content.getBody());
		assertArrayEquals(data, content2.asBinary());
		assertEquals(text, content2.asText());

		MessageContent contentWithHeaders = MessageContent.binary(Map.of("k", 123, "foo", "bar"), data);
		System.out.println(Json.toString(contentWithHeaders));
		assertEquals(MessageContent.Format.BINARY, contentWithHeaders.getFormat());
		assertEquals(123, contentWithHeaders.getHeaders().get("k"));
		assertEquals("bar", contentWithHeaders.getHeaders().get("foo"));
		assertArrayEquals(data, content.getBody());
		assertArrayEquals(data, contentWithHeaders.asBinary());
		assertEquals(text, contentWithHeaders.asText());

		MessageContent contentWithHeaders2 = MessageContent.parse(contentWithHeaders.serialize());
		assertEquals(MessageContent.Format.BINARY, contentWithHeaders2.getFormat());
		assertEquals(123, contentWithHeaders2.getHeaders().get("k"));
		assertEquals("bar", contentWithHeaders2.getHeaders().get("foo"));
		assertArrayEquals(data, content.getBody());
		assertArrayEquals(data, contentWithHeaders2.asBinary());
		assertEquals(text, contentWithHeaders2.asText());
	}

	@Test
	public void testStringListContent() {
		List<String> list = List.of("a", "b", "c");

		MessageContent content = MessageContent.object(list);
		System.out.println(Json.toString(content));
		assertEquals(MessageContent.Format.OBJECT, content.getFormat());
		assertTrue(content.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content.getBody());
		assertEquals(list, content.asList(String.class));
		assertSame(list, content.asList(String.class));

		MessageContent content2 = MessageContent.parse(content.serialize());
		assertEquals(MessageContent.Format.OBJECT, content2.getFormat());
		assertTrue(content2.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content2.getBody());
		assertEquals(list, content2.asList(String.class));
		assertNotSame(list, content2.asList(String.class));

		MessageContent contentWithHeaders = MessageContent.object(Map.of("k", true), list);
		System.out.println(Json.toString(contentWithHeaders));
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders.getFormat());
		assertEquals(true, contentWithHeaders.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders.getBody());
		assertEquals(list, contentWithHeaders.asList(String.class));
		assertSame(list, contentWithHeaders.asList(String.class));

		MessageContent contentWithHeaders2 = MessageContent.parse(contentWithHeaders.serialize());
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders2.getFormat());
		assertEquals(true, contentWithHeaders2.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders2.getBody());
		assertEquals(list, contentWithHeaders2.asList(String.class));
		assertNotSame(list, contentWithHeaders2.asList(String.class));
	}

	@Test
	public void testMapContent() {
		Map<String, Object> map = Map.of(
				"foo", 12345,
				"bar", "baz",
				"hello", true,
				"zoo", "Tiger");

		MessageContent content = MessageContent.object(map);
		System.out.println(Json.toString(content));
		assertEquals(MessageContent.Format.OBJECT, content.getFormat());
		assertTrue(content.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content.getBody());
		assertEquals(map, content.asMap(String.class, Object.class));
		assertSame(map, content.asMap(String.class, Object.class));

		MessageContent content2 = MessageContent.parse(content.serialize());
		assertEquals(MessageContent.Format.OBJECT, content2.getFormat());
		assertTrue(content2.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content2.getBody());
		assertEquals(map, content2.asMap(String.class, Object.class));
		assertNotSame(map, content2.asMap(String.class, Object.class));

		MessageContent contentWithHeaders = MessageContent.object(Map.of("k", true), map);
		System.out.println(Json.toString(contentWithHeaders));
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders.getFormat());
		assertEquals(true, contentWithHeaders.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders.getBody());
		assertEquals(map, contentWithHeaders.asMap(String.class, Object.class));
		assertSame(map, contentWithHeaders.asMap(String.class, Object.class));

		MessageContent contentWithHeaders2 = MessageContent.parse(contentWithHeaders.serialize());
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders2.getFormat());
		assertEquals(true, contentWithHeaders2.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders2.getBody());
		assertEquals(map, contentWithHeaders2.asMap(String.class, Object.class));
	}

	@Test
	public void testPojoContent() {
		Pojo pojo = new Pojo("name", 123);

		MessageContent content = MessageContent.object(pojo);
		System.out.println(Json.toString(content));
		assertEquals(MessageContent.Format.OBJECT, content.getFormat());
		assertTrue(content.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content.getBody());
		assertEquals(pojo, content.asObject(Pojo.class));
		assertSame(pojo, content.asObject(Pojo.class));

		MessageContent content2 = MessageContent.parse(content.serialize());
		assertEquals(MessageContent.Format.OBJECT, content2.getFormat());
		assertTrue(content2.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content2.getBody());
		assertEquals(pojo, content2.asObject(Pojo.class));
		assertNotSame(pojo, content2.asObject(Pojo.class));

		MessageContent contentWithHeaders = MessageContent.object(Map.of("k", true), pojo);
		System.out.println(Json.toString(contentWithHeaders));
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders.getFormat());
		assertEquals(true, contentWithHeaders.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders.getBody());
		assertEquals(pojo, contentWithHeaders.asObject(Pojo.class));
		assertSame(pojo, contentWithHeaders.asObject(Pojo.class));

		MessageContent contentWithHeaders2 = MessageContent.parse(contentWithHeaders.serialize());
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders2.getFormat());
		assertEquals(true, contentWithHeaders2.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders2.getBody());
		assertEquals(pojo, contentWithHeaders2.asObject(Pojo.class));
		assertNotSame(pojo, contentWithHeaders2.asObject(Pojo.class));
	}

	@Test
	public void testPojoListContent() {
		List<Pojo> list = List.of(new Pojo("Alice", 1),
				new Pojo("Bob", 2),
				new Pojo("Carol", 3));

		MessageContent content = MessageContent.object(list);
		System.out.println(Json.toString(content));
		assertEquals(MessageContent.Format.OBJECT, content.getFormat());
		assertTrue(content.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content.getBody());
		assertEquals(list, content.asList(Pojo.class));
		assertSame(list, content.asList(Pojo.class));

		MessageContent content2 = MessageContent.parse(content.serialize());
		assertEquals(MessageContent.Format.OBJECT, content2.getFormat());
		assertTrue(content2.getHeaders().isEmpty());
		assertInstanceOf(JsonNode.class, content2.getBody());
		assertEquals(list, content2.asList(Pojo.class));
		assertNotSame(list, content2.asList(Pojo.class));

		MessageContent contentWithHeaders = MessageContent.object(Map.of("k", true), list);
		System.out.println(Json.toString(contentWithHeaders));
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders.getFormat());
		assertEquals(true, contentWithHeaders.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders.getBody());
		assertEquals(list, contentWithHeaders.asList(Pojo.class));
		assertSame(list, contentWithHeaders.asList(Pojo.class));

		MessageContent contentWithHeaders2 = MessageContent.parse(contentWithHeaders.serialize());
		assertEquals(MessageContent.Format.OBJECT, contentWithHeaders2.getFormat());
		assertEquals(true, contentWithHeaders2.getHeaders().get("k"));
		assertInstanceOf(JsonNode.class, contentWithHeaders2.getBody());
		assertEquals(list, contentWithHeaders2.asList(Pojo.class));
		assertNotSame(list, contentWithHeaders2.asList(Pojo.class));
	}

	@Test
	public void testAsMethodsCrossFormat() {
		// Text as Binary
		MessageContent textContent = MessageContent.text("abc");
		assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), textContent.asBinary());

		// Binary as Text
		byte[] data = new byte[]{65, 66, 67}; // "ABC"
		MessageContent binaryContent = MessageContent.binary(data);
		assertEquals("ABC", binaryContent.asText());

		// Object as Binary/Text
		Pojo pojo = new Pojo("name", 123);

		String json = Json.toString(pojo);
		byte[] cbor = Json.toBytes(pojo);

		MessageContent objContent = MessageContent.object(pojo);
		assertArrayEquals(cbor, objContent.asBinary());
		assertEquals(json, objContent.asText());
		assertEquals(pojo, objContent.asObject(Pojo.class));
	}

	record Pojo(@JsonProperty("name") String name,
				@JsonProperty("value") int value) {
	}
}