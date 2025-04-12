package io.bosonnetwork.messaging.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.Map;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.messaging.Message;
import io.vertx.core.Promise;

public class MessageImpl extends Message {
	private transient Promise<Message> promise;

	// implementation details, related with the Vert.x MQTT library
	// private transient int packetId;

	@JdbiConstructor
	public MessageImpl(@ColumnName("rid") long rid, @ColumnName("conversationId") Id conversationId,
			@ColumnName("version") int version, @ColumnName("from") Id from, @ColumnName("to") Id to,
			@ColumnName("serialNumber") long serialNumber, @ColumnName("created") long created,
			@ColumnName("messageType") int messageType, @ColumnName("properties") Map<String, Object> properties,
			@ColumnName("contentType") String contentType, @ColumnName("contentDisposition") String contentDisposition,
			@ColumnName("body") byte[] body, @ColumnName("completed") long completed,
			@ColumnName("encrypted") boolean encrypted) {
		super(rid, conversationId, version, from, to, serialNumber, created, messageType, properties,
				contentType, contentDisposition, body, completed, encrypted);
	}

	@JsonCreator
	public MessageImpl() {
		super();
	}

	public MessageImpl(Id from, long id, int messageType) {
		super(from, id, messageType);
	}

	public static Message channelNotification(Id channelId, String body) {
		long now = System.currentTimeMillis();
		return new MessageImpl(-1, channelId, Message.VERSION, channelId, channelId, -1,
				now, Message.Types.NOTIFICATION, null, null, null, body.getBytes(UTF_8), now, false);
	}

	@Override
	public void setRid(long rid) {
		super.setRid(rid);
	}

	@Override
	public void setConversationId(Id id) {
		super.setConversationId(id);
	}

	@Override
	public void setCompleted(long timestamp) {
		super.setCompleted(timestamp);
	}

	@Override
	public boolean hasOriginalBody() {
		return super.hasOriginalBody();
	}

	@Override
	public Object getOriginalBody() {
		return super.getOriginalBody();
	}

	protected Promise<Message> initSendPromise() {
		if (this.promise == null)
			this.promise = Promise.promise();

		return this.promise;
	}

	protected void sent() {
		if (this.promise != null)
			this.promise.complete(this);
	}

	protected void failed(Throwable e) {
		if (this.promise != null)
			this.promise.fail(e);
	}

	protected MessageImpl dup(byte[] body) {
		return new MessageImpl(getRid(), getConversationId(), getVersion(), getFrom(), getTo(),
				getSerialNumber(), getCreated(), getMessageType(), getProperties(),
				getContentType(), getContentDisposition(), body, getCompleted(), isEncrypted());
	}

	@Override
	protected void decryptBody(CryptoContext ctx) throws CryptoException {
		super.decryptBody(ctx);
	}

	@Override
	protected void setEncrypted(boolean encrypted) {
		super.setEncrypted(encrypted);
	}

	public static MessageImpl parse(byte[] input) throws IOException {
		return getObjectMapper().readValue(input, MessageImpl.class);
	}
}
