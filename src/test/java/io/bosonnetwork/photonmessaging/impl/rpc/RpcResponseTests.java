package io.bosonnetwork.photonmessaging.impl.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.SessionInfo;
import io.bosonnetwork.photonmessaging.impl.ChannelInfo;

public class RpcResponseTests {
	private static long nextId() {
		return Random.random().nextLong();
	}

	private static Stream<Arguments> responseProvider() {
		List<Arguments> responses = new ArrayList<>();

		ChannelInfo channelInfo = new ChannelInfo(Id.random(), Id.random(), Id.random(), Random.randomBytes(64),
				Channel.Permission.MODERATOR_INVITE, "Foo Bar", "Hello world", true,
				System.currentTimeMillis(), System.currentTimeMillis(), null);

		responses.add(Arguments.of(RpcMethod.SESSION_LIST.toString(),
				RpcResponse.listSessions(nextId(), List.of(new SessionInfo(Id.random(), true, System.currentTimeMillis())))));
		responses.add(Arguments.of(RpcMethod.SESSION_REVOKE.toString(),
				RpcResponse.revokeSession(nextId())));
		responses.add(Arguments.of(RpcMethod.CONTACT_MUTATE.toString(),
				RpcResponse.contactMutate(nextId(), 67)));
		responses.add(Arguments.of(RpcMethod.CHANNEL_CREATE.toString(),
				RpcResponse.createChannel(nextId(), channelInfo)));
		responses.add(Arguments.of(RpcMethod.CHANNEL_DELETE.toString(),
				RpcResponse.deleteChannel(nextId())));
		responses.add(Arguments.of(RpcMethod.CHANNEL_OWNERSHIP_TRANSFER.toString(),
				RpcResponse.transferChannelOwnership(nextId())));
		responses.add(Arguments.of(RpcMethod.CHANNEL_SESSION_KEY_ROTATE.toString(),
				RpcResponse.rotateChannelSessionKey(nextId())));
		responses.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_ROLE_UPDATE.toString(),
				RpcResponse.updateChannelMembersRole(nextId(), List.of(Id.random(), Id.random()))));
		responses.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_BAN.toString(),
				RpcResponse.banChannelMembers(nextId(), List.of(Id.random(), Id.random()))));
		responses.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_UNBAN.toString(),
				RpcResponse.unbanChannelMembers(nextId(), List.of(Id.random()))));
		responses.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_REMOVE.toString(),
				RpcResponse.removeChannelMembers(nextId(), List.of(Id.random(), Id.random()))));
		responses.add(Arguments.of(RpcMethod.CHANNEL_JOIN.toString(),
				RpcResponse.joinChannel(nextId(), channelInfo)));
		responses.add(Arguments.of(RpcMethod.CHANNEL_LEAVE.toString(),
				RpcResponse.leaveChannel(nextId())));
		responses.add(Arguments.of(RpcMethod.CHANNEL_INFO.toString(),
				RpcResponse.getChannelInfo(nextId(), channelInfo)));
		responses.add(Arguments.of(RpcMethod.CHANNEL_JOIN.toString(),
				RpcResponse.error(nextId(), RpcMethod.CHANNEL_JOIN, 10000, "Test error")));

		return responses.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("responseProvider")
	void testSerialization(String name, RpcResponse response) {
		System.out.println(Json.toString(response));
		RpcResponse parsed = RpcResponse.parse(response.serialize());

		assertEquals(response.getId(), parsed.getId());
		assertEquals(response.getMethod(), parsed.getMethod());

		Object result = response.getResult();
		if (result instanceof ChannelInfo) {
			Object parsedResult = parsed.getResult();
			assertThat(parsedResult)
					.usingRecursiveComparison()
					.ignoringFieldsMatchingRegexes(".*b58")
					.isEqualTo(result);
		} else {
			assertEquals(result, parsed.getResult());
		}

		RpcError error = response.getError();
		assertEquals(error, parsed.getError());

		assertEquals(response.failed(), parsed.failed());
		assertEquals(response.succeeded(), parsed.succeeded());
	}
}