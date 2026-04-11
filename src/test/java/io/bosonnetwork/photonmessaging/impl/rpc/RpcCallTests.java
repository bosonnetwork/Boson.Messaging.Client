package io.bosonnetwork.photonmessaging.impl.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.vertx.core.Vertx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.SessionInfo;
import io.bosonnetwork.photonmessaging.impl.ChannelInfo;

@ExtendWith(VertxExtension.class)
public class RpcCallTests {
	@Test
	void testSucceededCall(Vertx vertx, VertxTestContext context) {
		List<SessionInfo> sessions = List.of(new SessionInfo(Id.random(), false, System.currentTimeMillis()));

		RpcCall<List<SessionInfo>> call = RpcCall.listSessions();
		vertx.setTimer(500, id ->
				call.setResponse(RpcResponse.listSessions(call.getId(), sessions))
		);

		call.getFuture().onComplete(context.succeeding(result ->
				context.verify(() -> {
					RpcResponse response = call.getResponse();
					assertNotNull(response);
					assertTrue(response.succeeded());
					assertFalse(response.failed());

					assertNotNull(result);
					assertEquals(sessions, result);
					context.completeNow();
				})
		));
	}

	@Test
	void testFailedCall(Vertx vertx, VertxTestContext context) {
		RpcError error = new RpcError(-1000, "Failed");

		RpcCall<Void> call = RpcCall.deleteChannel();
		vertx.setTimer(500, id ->
				call.setResponse(RpcResponse.error(call.getId(), call.getMethod(), error))
		);

		call.getFuture().onComplete(ar ->
				context.verify(() -> {
					RpcResponse response = call.getResponse();
					assertNotNull(response);
					assertFalse(response.succeeded());
					assertTrue(response.failed());

					assertTrue(ar.failed());
					assertNotNull(ar.cause());
					// TODO: check the cause and compare with the error
					context.completeNow();
				})
		);
	}

	@Test
	void testTimeoutCall(VertxTestContext context) {
		RpcCall<ChannelInfo> call = new RpcCall<>(RpcRequest.getChannelInfo(10), 1500);
		call.getFuture().onComplete(ar ->
				context.verify(() -> {
					assertTrue(ar.failed());
					assertNotNull(ar.cause());
					assertInstanceOf(RpcTimeoutException.class, ar.cause());
					context.completeNow();
				})
		);
	}

	@Test
	void testWrongResponse() {
		RpcCall<Void> call = RpcCall.revokeSession(Id.random());
		assertThrows(IllegalArgumentException.class, () -> call.setResponse(RpcResponse.revokeSession(call.getId() + 1)));
		assertThrows(IllegalArgumentException.class, () -> call.setResponse(RpcResponse.deleteChannel(call.getId())));

		call.setResponse(RpcResponse.revokeSession(call.getId()));
		assertThrows(IllegalStateException.class, () -> call.setResponse(RpcResponse.revokeSession(call.getId())));
	}
}