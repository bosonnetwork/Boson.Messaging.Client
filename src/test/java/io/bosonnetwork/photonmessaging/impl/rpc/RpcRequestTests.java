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
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.InviteTicket;
import io.bosonnetwork.photonmessaging.impl.ChannelMembersRole;
import io.bosonnetwork.photonmessaging.impl.ChannelSessionKeyRotation;
import io.bosonnetwork.photonmessaging.impl.ContactMutation;
import io.bosonnetwork.photonmessaging.impl.NewChannelInfo;

public class RpcRequestTests {
	private static long nextId() {
		return Random.random().nextLong();
	}

	private static Stream<Arguments> requestProvider() {
		List<Arguments> requests = new ArrayList<>();

		requests.add(Arguments.of(RpcMethod.SESSION_LIST.toString(),
				RpcRequest.listSessions(nextId())));
		requests.add(Arguments.of(RpcMethod.SESSION_REVOKE.toString(),
				RpcRequest.revokeSession(nextId(), Id.random())));
		requests.add(Arguments.of(RpcMethod.CONTACT_MUTATE.toString(),
				RpcRequest.contactMutate(nextId(), ContactMutation.clear(10))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_CREATE.toString(),
				RpcRequest.createChannel(nextId(), new NewChannelInfo(Id.random(), Random.randomBytes(64),
						Channel.Permission.MEMBER_INVITE, "Test Channel", null, false))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_DELETE.toString(),
				RpcRequest.deleteChannel(nextId())));
		requests.add(Arguments.of(RpcMethod.CHANNEL_OWNERSHIP_TRANSFER.toString(),
				RpcRequest.transferChannelOwnership(nextId(), Id.random())));
		requests.add(Arguments.of(RpcMethod.CHANNEL_SESSION_KEY_ROTATE.toString(),
				RpcRequest.rotateChannelSessionKey(nextId(), new ChannelSessionKeyRotation(Id.random(), Random.randomBytes(64)))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_ROLE_UPDATE.toString(),
				RpcRequest.updateChannelMembersRole(nextId(), new ChannelMembersRole(List.of(Id.random(), Id.random()), Channel.Role.MODERATOR))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_BAN.toString(),
				RpcRequest.banChannelMembers(nextId(), List.of(Id.random(), Id.random()))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_UNBAN.toString(),
				RpcRequest.unbanChannelMembers(nextId(), List.of(Id.random()))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_MEMBERS_REMOVE.toString(),
				RpcRequest.removeChannelMembers(nextId(), List.of(Id.random(), Id.random()))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_JOIN.toString(),
				RpcRequest.joinChannel(nextId(), InviteTicket.create(new CryptoIdentity(), Id.random(), Id.random(),
						null, System.currentTimeMillis() + 100000L, Random.randomBytes(64)))));
		requests.add(Arguments.of(RpcMethod.CHANNEL_LEAVE.toString(),
				RpcRequest.leaveChannel(nextId())));
		requests.add(Arguments.of(RpcMethod.CHANNEL_INFO.toString(),
				RpcRequest.getChannelInfo(nextId())));

		return requests.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("requestProvider")
	void testSerialization(String name, RpcRequest request) {
		System.out.println(Json.toString(request));
		RpcRequest parsed = RpcRequest.parse(request.serialize());

		assertEquals(request.getId(), parsed.getId());
		assertEquals(request.getMethod(), parsed.getMethod());

		Object params = request.getParams();
		if (params instanceof NewChannelInfo ||
				params instanceof ContactMutation ||
				params instanceof ChannelSessionKeyRotation ||
				params instanceof InviteTicket) {
			Object parsedParams = parsed.getParams();
			assertThat(parsedParams)
					.usingRecursiveComparison()
					.ignoringFieldsMatchingRegexes(".*b58")
					.isEqualTo(params);
		} else {
			assertEquals(params, parsed.getParams());
		}
	}
}