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

import io.bosonnetwork.Id;

public class Topics {
	public final String userInbox;
	public final String userOutbox;
	public final String deviceInbox;
	public final String deviceOutbox;

	public enum Type {
		UNKNOWN, USER_INBOX, USER_OUTBOX, DEVICE_INBOX, DEVICE_OUTBOX
	}

	public Topics(Id userId, Id deviceId) {
		this.userInbox = userId.toBase58String() + "/inbox";
		this.userOutbox = userId.toBase58String() + "/outbox";
		this.deviceInbox = userId.toBase58String() + "/" + deviceId.toBase58String() + "/inbox";
		this.deviceOutbox = userId.toBase58String() + "/" + deviceId.toBase58String() + "/outbox";
	}

	public Type typeOf(String topic) {
		if (topic.equals(userInbox))
			return Type.USER_INBOX;

		if (topic.equals(userOutbox))
			return Type.USER_OUTBOX;

		 if (topic.equals(deviceInbox))
			return Type.DEVICE_INBOX;

		 if (topic.equals(deviceOutbox))
			return Type.DEVICE_OUTBOX;

		 return Type.UNKNOWN;
	}
}