package io.bosonnetwork.photonmessaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcRequest;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcResponse;

public class MessageTests {
	@Test
	void testBytesMessageSerialization() throws Exception {
		Id id = Id.random();
		Id recipient = Id.random();
		Map<String, Object> payload = Map.of(
				"key", "value",
				"foo", false,
				"bar", 1245678990,
				"hello", "world",
				"zoo", "Lion");
		byte[] serializedPayload = Json.toBytes(payload);
		long now = System.currentTimeMillis();

		PhotonMessage<Map<String, Object>> ref = new PhotonMessage<>(id, recipient, Message.Type.CONTENT_MESSAGE, now, payload);
		BytesMessage message = BytesMessage.dup(ref, serializedPayload);

		byte[] data = message.serialize();
		BytesMessage parsed = BytesMessage.parse(data);

		assertEquals(id, parsed.getId());
		assertEquals(recipient, parsed.getRecipient());
		assertEquals(Message.Type.CONTENT_MESSAGE, parsed.getType());
		assertEquals(now, parsed.getCreatedAt());
		assertArrayEquals(serializedPayload, parsed.getPayloadAsBytes());
	}

	@Test
	void testPhotonMessagePayloads() {
		Id id = Id.random();
		Id recipient = Id.random();
		long now = System.currentTimeMillis();

		// 1. MessageContent (Text)
		MessageContent textContent = MessageContent.text("Hello Photon");
		PhotonMessage<MessageContent> textMsg = new PhotonMessage<>(id, recipient, Message.Type.CONTENT_MESSAGE, now, textContent);
		assertArrayEquals(textContent.serialize(), textMsg.getPayloadAsBytes());

		// 2. Notification (FriendRequest)
		Notification notification = Notification.friendRequest(Id.random(), Id.random(), "Add me!");
		PhotonMessage<Notification> notifyMsg = new PhotonMessage<>(id, recipient, Message.Type.STATE_MESSAGE, now, notification);
		assertArrayEquals(notification.serialize(), notifyMsg.getPayloadAsBytes());

		// 3. RpcRequest
		RpcRequest request = RpcRequest.listSessions(12345);
		PhotonMessage<RpcRequest> rpcRequestMsg = new PhotonMessage<>(id, recipient, Message.Type.CONTROL_MESSAGE, now, request);
		assertArrayEquals(request.serialize(), rpcRequestMsg.getPayloadAsBytes());

		// 4. RpcResponse
		RpcResponse response = RpcResponse.revokeSession(12345);
		PhotonMessage<RpcResponse> rpcResponseMsg = new PhotonMessage<>(id, recipient, Message.Type.CONTROL_MESSAGE, now, response);
		assertArrayEquals(response.serialize(), rpcResponseMsg.getPayloadAsBytes());

		// 5. Generic Serializable Object
		Map<String, String> genericPayload = Map.of("key", "value");
		PhotonMessage<Map<String, String>> genericMsg = new PhotonMessage<>(id, recipient, Message.Type.CONTENT_MESSAGE, now, genericPayload);
		assertArrayEquals(Json.toBytes(genericPayload), genericMsg.getPayloadAsBytes());
	}

	@Test
	void testTwoLayerSerialization() throws Exception {
		// Layer 1: Payload Serialization (Body Encryption Layer)
		Id msgId = Id.random();
		Id recipient = Id.random();
		MessageContent content = MessageContent.text("Secret Message");
		PhotonMessage<MessageContent> photonMsg = new PhotonMessage<>(msgId, recipient, Message.Type.CONTENT_MESSAGE, System.currentTimeMillis(), content);

		byte[] encryptedBodyPlaceholder = photonMsg.getPayloadAsBytes(); // In real case, this would be encrypted

		// Layer 2: Envelope Serialization (Transmission Layer)
		BytesMessage envelope = BytesMessage.dup(photonMsg, encryptedBodyPlaceholder);
		byte[] transmissionData = envelope.serialize();

		// Deserialization
		BytesMessage receivedEnvelope = BytesMessage.parse(transmissionData);
		assertEquals(msgId, receivedEnvelope.getId());
		byte[] receivedEncryptedBody = receivedEnvelope.getPayloadAsBytes();
		assertArrayEquals(encryptedBodyPlaceholder, receivedEncryptedBody);
		assertThat(receivedEnvelope)
				.usingRecursiveComparison()
				.withComparatorForType(Id::compare, Id.class)
				.isEqualTo(envelope);

		// Layer 1 Deserialization
		MessageContent receivedContent = MessageContent.parse(receivedEncryptedBody);
		assertEquals(content.asText(), receivedContent.asText());
		assertThat(receivedContent)
				.usingRecursiveComparison()
				.withComparatorForType(Id::compare, Id.class)
				.ignoringFields("origin")
				.isEqualTo(content);
	}

	@Test
	void testSentFutureSupport() {
		PhotonMessage<String> message = new PhotonMessage<>(Id.random(), Id.random(), Message.Type.CONTENT_MESSAGE, System.currentTimeMillis(), "Test");

		// Initially no promise
		assertThrows(IllegalStateException.class, message::getFuture);

		message.prepareForSending();
		assertNotNull(message.getFuture());
		assertFalse(message.getFuture().isComplete());

		// Success path
		message.sent();
		assertTrue(message.getFuture().succeeded());
		assertTrue(message.getSentAt() > 0);

		// Failure path
		PhotonMessage<String> failedMsg = new PhotonMessage<>(Id.random(), Id.random(), Message.Type.CONTENT_MESSAGE, System.currentTimeMillis(), "Fail");
		failedMsg.prepareForSending();
		Exception error = new RuntimeException("Send failed");
		failedMsg.failed(error);
		assertTrue(failedMsg.getFuture().failed());
		assertEquals(error, failedMsg.getFuture().cause());
	}
}