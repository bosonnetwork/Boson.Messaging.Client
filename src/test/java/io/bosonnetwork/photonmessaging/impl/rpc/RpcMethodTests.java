package io.bosonnetwork.photonmessaging.impl.rpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class RpcMethodTests {
	@Test
	void testValueOf() {
		Set<RpcMethod> methods = EnumSet.allOf(RpcMethod.class);

		for (RpcMethod method : methods) {
			RpcMethod m = RpcMethod.valueOf(method.value());
			assertSame(method, m);
		}
	}

	@Test
	void testMethodCategory() {
		Set<RpcMethod> methods = EnumSet.allOf(RpcMethod.class);

		for (RpcMethod method : methods) {
			if (method.value() < 0x50) {
				assertTrue(method.isServiceRpc());
				assertFalse(method.isChannelRpc());
				assertFalse(method.isOwnerPrivilegedChannelRpc());
				assertFalse(method.isModeratorPrivilegedChannelRpc());
				assertFalse(method.isUnprivilegedChannelRpc());
			} else if (method.value() < 0x60) {
				assertFalse(method.isServiceRpc());
				assertTrue(method.isChannelRpc());
				assertTrue(method.isOwnerPrivilegedChannelRpc());
				assertFalse(method.isModeratorPrivilegedChannelRpc());
				assertFalse(method.isUnprivilegedChannelRpc());
			} else if (method.value() < 0x70) {
				assertFalse(method.isServiceRpc());
				assertTrue(method.isChannelRpc());
				assertFalse(method.isOwnerPrivilegedChannelRpc());
				assertTrue(method.isModeratorPrivilegedChannelRpc());
				assertFalse(method.isUnprivilegedChannelRpc());
			} else if (method.value() < 0x80) {
				assertFalse(method.isServiceRpc());
				assertTrue(method.isChannelRpc());
				assertFalse(method.isOwnerPrivilegedChannelRpc());
				assertFalse(method.isModeratorPrivilegedChannelRpc());
				assertTrue(method.isUnprivilegedChannelRpc());
			} else {
				fail("Illegal RPC method");
			}
		}
	}
}