/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl;

import java.util.HashMap;
import java.util.Map;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.CryptoIdentity;

public abstract class SessionContext {
	protected final Id contactId;
	protected final Identity userIdentity;
	protected final CryptoIdentity sessionIdentity;

	private CryptoContext rxCryptoContext;
	private CryptoContext txCryptoContext;

	protected SessionContext(Id userId, Identity userIdentity, CryptoIdentity sessionIdentity) {
		this.contactId = userId;
		this.userIdentity = userIdentity;
		this.sessionIdentity = sessionIdentity;
	}

	public static SessionContext forUser(Id userId, Identity userIdentity, CryptoIdentity sessionIdentity) {
		return new UserContext(userId, userIdentity, sessionIdentity);
	}

	public static SessionContext forChannel(Id userId, Identity userIdentity, CryptoIdentity sessionIdentity) {
		return new ChannelContext(userId, userIdentity, sessionIdentity);
	}

	public Id getSessionId() {
		return sessionIdentity.getId();
	}

	public byte[] sign(byte[] data) {
		return sessionIdentity.sign(data);
	}

	public boolean verify(byte[] data, byte[] signature) {
		return sessionIdentity.verify(data, signature);
	}

	// one-shot encryption
	public byte[] encrypt(Id recipient, byte[] data) throws CryptoException {
		return sessionIdentity.encrypt(recipient, data);
	}

	// one-shot decryption
	public byte[] decrypt(Id sender, byte[] data) throws CryptoException {
		return sessionIdentity.decrypt(sender, data);
	}

	public CryptoContext createCryptoContext(Id id) throws CryptoException {
		return sessionIdentity.createCryptoContext(id);
	}

	public CryptoContext getTxCryptoContext() throws CryptoException {
		CryptoContext ctx = this.txCryptoContext;
		if (ctx == null) {
			ctx = userIdentity.createCryptoContext(sessionIdentity.getId());
			this.txCryptoContext = ctx;
		}

		return txCryptoContext;
	}

	public CryptoContext getRxCryptoContext() throws CryptoException {
		CryptoContext ctx = this.rxCryptoContext;
		if (ctx == null) {
			ctx = createCryptoContext(contactId);
			this.rxCryptoContext = ctx;
		}

		return ctx;
	}

	public abstract CryptoContext getRxCryptoContext(Id sender) throws CryptoException;

	private static class UserContext extends SessionContext {
		protected UserContext(Id userId, Identity userIdentity, CryptoIdentity sessionIdentity) {
			super(userId, userIdentity, sessionIdentity);
		}

		@Override
		public CryptoContext getRxCryptoContext(Id sender) throws CryptoException {
			if (!sender.equals(contactId))
				throw new IllegalArgumentException("Invalid sender, the sender should be the contact");

			return getRxCryptoContext();
		}
	}

	private static class ChannelContext extends SessionContext {
		private final Map<Id, CryptoContext> memberRxCryptoContexts;

		protected ChannelContext(Id userId, Identity userIdentity, CryptoIdentity sessionIdentity) {
			super(userId, userIdentity, sessionIdentity);
			memberRxCryptoContexts = new HashMap<>();
		}


		@Override
		public CryptoContext getRxCryptoContext(Id sender) throws CryptoException {
			if (sender.equals(contactId))
				return getRxCryptoContext();

			return memberRxCryptoContexts.computeIfAbsent(sender, k -> {
				try {
					return createCryptoContext(k);
				} catch (CryptoException e) {
					sneakyThrow(e);
					// dead code, but needed to suppress the warning
					return null;
				}
			});
		}

		@SuppressWarnings("unchecked")
		private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
			throw (E) e;
		}
	}
}