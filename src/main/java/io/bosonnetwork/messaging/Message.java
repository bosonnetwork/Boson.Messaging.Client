package io.bosonnetwork.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bosonnetwork.Id;
import io.bosonnetwork.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.vertx.core.json.JsonObject;

public abstract class Message {
	public static final int VERSION = 1;

	@JsonProperty("v")
	private int version;
	@JsonProperty("f")
	private Id from;
	@JsonProperty("r") // alias: recipient
	private Id to;
	@JsonProperty("s")
	private long serialNumber;
	@JsonProperty("c")
	private long created;
	@JsonProperty("t")
	private int messageType;

	@JsonProperty("p")
	@JsonInclude(Include.NON_EMPTY)
	private Map<String, Object> properties;

	// Optional, default null[means: text/plain]
	@JsonProperty("m")	// alias: mime type
	@JsonInclude(Include.NON_EMPTY)
	private String contentType;
	// Optional, default INLINE
	@JsonProperty("d")
	@JsonInclude(Include.NON_EMPTY)
	private String contentDisposition;

	@JsonProperty("b")
	@JsonInclude(Include.NON_DEFAULT)
	private byte[] body;

	private Id conversationId;
	private long timestamp; // local sent or received timestamp

	public static class Types {
		public static final int MESSAGE = 0;
		public static final int CALL = 1;
		public static final int NOTIFICATION = 2;

		public static final int DEFAULT = MESSAGE;

		private Types() {}
	}

	public static class ContentTypes {
		public static final String TEXT = "text/plain";
		public static final String JSON = "application/json";

		public static final String IMAGE_JPEG = "image/jpeg";
		public static final String IMAGE_PNG = "image/png";
		public static final String IMAGE_WEBP = "image/webp";

		public static final String AUDIO_AAC = "audio/aac";
		public static final String AUDIO_MP3 = "audio/mpeg";
		public static final String AUDIO_WEBM = "audio/webm";

		public static final String VIDEO_MP4 = "video/mp4";
		public static final String VIDEO_WEBM = "video/webm";

		public static final String BINARY = "application/octet-stream";

		public static final String DEFAULT = TEXT;

		private ContentTypes() {}
	}

	public static class ContentDispositions {
		public static final String INLINE = "inline";
		public static final String ATTACHMENT = "attachment";

		public static final String DEFAULT = INLINE;

		private ContentDispositions() {
		}

		public static String inline() {
			return INLINE;
		}

		public static String attachment() {
			return ATTACHMENT;
		}

		public static String attachment(String filename) {
			return ATTACHMENT + "; filename=\"" + filename + "\"";
		}
	}

	protected Message() {
		this.conversationId = null;
		this.timestamp = -1;
	}

	protected Message(Id from, long serialNumber, int messageType) {
		this.conversationId = null;
		this.version = VERSION;
		this.from = from;
		this.serialNumber = serialNumber;
		this.messageType = messageType;
		this.created = System.currentTimeMillis();
		this.timestamp = -1;
	}

	/*
	protected Message(Id from, Id to, long serialNumber, long created, int messageType,
			Map<String, Object> properties, String contentType, String contentDisposition,
			byte[] body) {
		this(null, VERSION, from, to, serialNumber, created, messageType, properties,
				contentType, contentDisposition, body, -1);
	}
	*/

	protected Message(Id conversationId, int version, Id from, Id to, long serialNumber, long created,
			int messageType, Map<String, Object> properties, String contentType, String contentDisposition,
			byte[] body, long timestamp) {
		this.version = version;
		this.conversationId = conversationId;
		this.from = from;
		this.to = to;
		this.serialNumber = serialNumber;
		this.created = created;
		this.messageType = messageType;
		this.properties = properties;
		this.contentType = contentType;
		this.contentDisposition = contentDisposition;
		this.body = body;
		this.timestamp = timestamp;
	}

	public int getVersion() {
		return version;
	}

	public Id getConversationId() {
		return conversationId;
	}

	protected void setConversationId(Id id) {
		this.conversationId = id;
	}

	public Id getFrom() {
		return from;
	}

	public Id getTo() {
		return to;
	}

	public long getSerialNumber() {
		return serialNumber;
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

	public String getContentType() {
		return contentType == null || contentType.isEmpty() ? ContentTypes.DEFAULT : contentType;
	}

	public String getContentDisposition() {
		return contentDisposition == null || contentDisposition.isEmpty() ? ContentDispositions.DEFAULT : contentDisposition;
	}

	public byte[] getBody() {
		return body;
	}

	public String getBodyAsText() {
		return body != null ? new String(body, UTF_8) : null;
	}

	public Map<String, Object> getBodyAsMap() throws IOException {
		return body == null ? null : getBodyAs(Json.MAP_TYPE);
	}

	public JsonObject getBodyAsJson() throws IOException {
		return body != null ? new JsonObject(getBodyAs(Json.MAP_TYPE)) : null;
	}

	public <T> T getBodyAs(Class<T> clazz) throws IOException {
		return body != null ? getObjectMapper().readValue(body, clazz) : null;
	}

	public <T> T getBodyAs(TypeReference<T> type) throws IOException {
		return body != null ? getObjectMapper().readValue(body, type) : null;
	}

	public long getTimestamp() {
		return timestamp;
	}

	protected void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	protected static ObjectMapper getObjectMapper() {
		return Json.cborMapper();
	}

	public byte[] serialize() {
		try {
			return getObjectMapper().writeValueAsBytes(this);
		} catch (IOException wnh) {
			// will never happen
			throw new IllegalStateException("Message serialization failed", wnh);
		}
	}

	public void serialize(ByteBuf output) throws IOException {
		getObjectMapper().writeValue((OutputStream)new ByteBufOutputStream(output), this);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Message that) {
			return this.version == that.version &&
					this.serialNumber == that.serialNumber &&
					this.created == that.created &&
					this.messageType == that.messageType &&
					Objects.equals(this.from, that.from) &&
					Objects.equals(this.to, that.to) &&
					Objects.equals(this.getProperties(), that.getProperties()) &&
					Objects.equals(this.getContentType(), that.getContentType()) &&
					Objects.equals(this.getContentDisposition(), that.getContentDisposition()) &&
					Arrays.equals(this.body, that.body);
		}

		return false;
	}

	public static abstract class Builder {
		private Message message;

		protected Builder(Message message) {
			this.message = message;
		}

		public Builder to(Id to) {
			Objects.requireNonNull(to, "to");
			message.to = to;
			return this;
		}

		public Builder property(String name, Object value) {
			Objects.requireNonNull(name, "name");

			if (message.properties == null)
				message.properties = new HashMap<>();

			if (value != null)
				message.properties.put(name, value);
			else
				message.properties.remove(name);

			return this;
		}

		public Builder clearProperty() {
			if (message.properties != null)
				message.properties.clear();

			return this;
		}

		public Builder contentType(String contentType) {
			Objects.requireNonNull(contentType, "contentType");
			if (contentType.isEmpty())
				throw new IllegalArgumentException("contentType");

			message.contentType = contentType;
			return this;
		}

		public Builder body(byte[] body) {
			Objects.requireNonNull(body, "body");
			message.body = body;

			if (message.contentType == null)
				message.contentType = ContentTypes.BINARY;

			return this;
		}

		public Builder body(String body) {
			Objects.requireNonNull(body, "body");
			message.body = body.getBytes(UTF_8);

			if (message.contentType == null)
				message.contentType = ContentTypes.TEXT;

			return this;
		}

		public Builder body(Object body) {
			Objects.requireNonNull(body, "body");

			try {
				message.body = getObjectMapper().writeValueAsBytes(body);
			} catch (JsonProcessingException e) {
				throw new IllegalArgumentException("body can not be serialized", e);
			}

			if (message.contentType == null)
				message.contentType = ContentTypes.JSON;

			return this;
		}

		protected Message build() {
			return message;
		}

		public abstract CompletableFuture<Message> send();
	}
}
