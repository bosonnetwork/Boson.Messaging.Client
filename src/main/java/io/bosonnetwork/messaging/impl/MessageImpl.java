package io.bosonnetwork.messaging.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.Map;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Message;
import io.vertx.core.Promise;

public class MessageImpl extends Message {
	private long sid;
	private transient Promise<Message> promise;

	// implementation details, related with the Vert.x MQTT library
	// private transient int packetId;

	@JdbiConstructor
	public MessageImpl(@ColumnName("sid") long sid, @ColumnName("conversationId") Id conversationId,
			@ColumnName("version") int version, @ColumnName("from") Id from, @ColumnName("to") Id to,
			@ColumnName("serialNumber") long serialNumber, @ColumnName("created") long created,
			@ColumnName("messageType") int messageType, @ColumnName("properties") Map<String, Object> properties,
			@ColumnName("contentType") String contentType, @ColumnName("contentDisposition") String contentDisposition,
			@ColumnName("body") byte[] body, @ColumnName("timestamp") long timestamp) {
		super(conversationId, version, from, to, serialNumber, created, messageType, properties,
				contentType, contentDisposition, body, timestamp);
		this.sid = sid;
	}

	@JsonCreator
	public MessageImpl() {
		super();
		this.sid = -1;
	}

	public MessageImpl(Id from, long id, int messageType) {
		super(from, id, messageType);
		this.sid = -1;
	}

	public static Message channelNotification(Id channelId, String body) {
		long now = System.currentTimeMillis();
		return new MessageImpl(-1, channelId, Message.VERSION, channelId, channelId, -1,
				now, Message.Types.NOTIFICATION, null, null, null, body.getBytes(UTF_8), now);
	}

	public long getSid() {
		return sid;
	}

	public void setSid(long sid) {
		this.sid = sid;
	}

	@Override
	public void setConversationId(Id id) {
		super.setConversationId(id);
	}

	@Override
	public void setTimestamp(long timestamp) {
		super.setTimestamp(timestamp);
	}

	protected Promise<Message> initSendPromise() {
		if (this.promise == null)
			this.promise = Promise.promise();

		return this.promise;
	}

	protected void sent() {
		setTimestamp(System.currentTimeMillis());
		if (this.promise != null)
			this.promise.complete(this);
	}

	protected void failed(Throwable e) {
		if (this.promise != null)
			this.promise.fail(e);
	}

	protected MessageImpl dup(byte[] body) {
		return new MessageImpl(this.sid, getConversationId(), getVersion(), getFrom(), getTo(),
				getSerialNumber(), getCreated(), getMessageType(), getProperties(),
				getContentType(), getContentDisposition(), body, getTimestamp());
	}

	public static MessageImpl parse(byte[] input) throws IOException {
		return getObjectMapper().readValue(input, MessageImpl.class);
	}
}
