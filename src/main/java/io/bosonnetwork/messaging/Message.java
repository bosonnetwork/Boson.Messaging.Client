package io.bosonnetwork.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bosonnetwork.Id;
import io.bosonnetwork.utils.ThreadLocals;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.vertx.core.json.JsonObject;

public class Message {
	public static final int VERSION = 1;

	private static final Charset UTF8 = Charset.forName("UTF-8");

	@JsonProperty("v")
	private int version;
	@JsonProperty("f")
	private Id from;
	@JsonProperty("r") // alias: recipient
	private Id to;
	@JsonProperty("i")
	private long id;
	@JsonProperty("s")  // alias: stamp
	private long created;
	@JsonProperty("m")
	private int messageType;
	@JsonProperty("p")
	@JsonInclude(Include.NON_EMPTY)
	private Map<String, Object> properties;
	// Optional, default text/plain
	@JsonProperty("t")
	@JsonInclude(Include.NON_EMPTY)
	private String contentType;
	@JsonProperty("b")
	private byte[] body;

	// implementation details, related with the Vert.x MQTT library
	private int packetId;
	private CompletableFuture<?> future;

	private static final Logger log = LoggerFactory.getLogger(Message.class);

	public static class ContentTypes {
		public static final String TEXT = "text/plain";
		public static final String JSON = "application/json";
		public static final String BINARY = "application/octet-stream";

		public static final String DEFAULT = TEXT;

		private ContentTypes() {}
	}

	public static class Types {
		public static final int MESSAGE = 0;
		public static final int CALL = 1;
		public static final int NOTIFICATION = 2;

		public static final int DEFAULT = MESSAGE;

		private Types() {}
	}

	Message() {
	}

	public Message(Id from, Id to, long id, int messageType, String contentType, byte[] body) {
		this.version = VERSION;
		this.from = from;
		this.to = to;
		this.id = id;
		this.created = System.currentTimeMillis();
		this.messageType = messageType;
		this.contentType = contentType;
		this.body = body;
	}

	public Message(Id from, Id to, long id, String body) {
		this(from, to, id, Types.MESSAGE, null, body.getBytes(UTF8));
	}

	public Message(Id from, Id to, long id, Map<String, Object> body) throws IOException {
		this(from, to, id, Types.MESSAGE, ContentTypes.JSON, getObjectMapper().writeValueAsBytes(body));
	}

	public Message(Id from, Id to, long id, byte[] body) throws IOException {
		this(from, to, id, Types.MESSAGE, ContentTypes.BINARY, body);
	}


	public Id getFrom() {
		return from;
	}

	public Id getTo() {
		return to;
	}

	public long getId() {
		return id;
	}

	public long getCreated() {
		return created;
	}

	public int getMessageType() {
		return messageType;
	}

	public Map<String, Object> getProperties() {
		return properties == null || properties.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(properties);
	}

	public void addProperty(String name, Object value) {
		// normally should not override the existing properties
		if (properties == null)
			properties = new HashMap<>();

		properties.putIfAbsent(name, value);
	}

	public String getContentType() {
		return contentType == null || contentType.isEmpty() ? ContentTypes.DEFAULT : contentType;
	}

	public byte[] getBody() {
		return body;
	}

	public String getBodyAsText() {
		return body != null ? new String(body, UTF8) : null;
	}

	public Map<String, Object> getBodyAsMap() throws IOException {
		return body != null ?
				getObjectMapper().readValue(body, new TypeReference<HashMap<String, Object>>(){}) :
				null;
	}

	public JsonObject getBodyAsJson() throws IOException {
		return body != null ? new JsonObject(getBodyAsMap()) : null;
	}

	private static ObjectMapper getObjectMapper() {
		return ThreadLocals.CBORMapper();
	}

	public static Message parse(byte[] input) throws IOException {
		return getObjectMapper().readValue(input, Message.class);
	}

	public byte[] serialize() {
		try {
			return getObjectMapper().writeValueAsBytes(this);
		} catch (JsonProcessingException wnh) {
			// will never happen
			log.error("INTERNAL: message serialize failed", wnh);
			return null;
		}
	}

	public void serialize(ByteBuf output) throws IOException {
		getObjectMapper().writeValue((OutputStream)new ByteBufOutputStream(output), this);
	}

	void setPacketId(int packetId) {
		this.packetId = packetId;
	}

	int getPacketId() {
		return packetId;
	}

	<T> void attachFuture(CompletableFuture<T> future) {
		this.future = future;
	}

	<T> CompletableFuture<T> detachFuture() {
		@SuppressWarnings("unchecked")
		CompletableFuture<T> f = (CompletableFuture<T>) this.future;
		this.future = null;

		return f;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (!(o instanceof Message))
			return false;

		Message other = (Message)o;
		return other.version == this.version &&
				other.from.equals(this.from) &&
				other.to.equals(this.to) &&
				other.id == this.id &&
				other.created == this.created &&
				String.valueOf(other.contentType).equals(String.valueOf(this.contentType)) &&
				Arrays.equals(other.body, this.body);
	}
}
