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
import io.bosonnetwork.photonmessaging.FriendRequest;

public class DefaultFriendRequest implements FriendRequest {
	private static final long EXPIRATION = 1000L * 60L * 60L * 24L * 7L; // 1 week

	private final Id userId;
	private final Id initiatorId;
	private final String hello;
	private final long createdAt;
	private long updatedAt;
	private boolean accepted;
	private long acceptedAt;

	protected DefaultFriendRequest(Id userId, Id initiatorId, String hello) {
		this(userId, initiatorId, hello, System.currentTimeMillis(), 0);
	}

	protected DefaultFriendRequest(Id userId, Id initiatorId, String hello, long createdAt, long updatedAt) {
		this(userId, initiatorId, hello, createdAt, updatedAt, false, 0);
	}

	protected DefaultFriendRequest(Id userId, Id initiatorId, String hello, long createdAt,
	                               long updatedAt, boolean accepted, long acceptedAt) {
		this.userId = userId;
		this.initiatorId = initiatorId;
		this.hello = hello;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt != 0 ? updatedAt : createdAt;
		this.accepted = accepted;
		this.acceptedAt = acceptedAt;
	}

	@Override
	public Id getUserId() {
		return userId;
	}

	@Override
	public Id getInitiatorId() {
		return initiatorId;
	}

	@Override
	public String getHello() {
		return hello;
	}

	@Override
	public boolean isAccepted() {
		return accepted;
	}

	protected void accept() {
		accepted = true;
		acceptedAt = System.currentTimeMillis();
		updatedAt = acceptedAt;
	}

	protected void accept(long timestamp) {
		accepted = true;
		acceptedAt = timestamp;
		updatedAt = timestamp;
	}

	@Override
	public boolean isExpired() {
		return !accepted && (System.currentTimeMillis() - updatedAt >= EXPIRATION);
	}

	@Override
	public long getCreatedAt() {
		return createdAt;
	}

	@Override
	public long getAcceptedAt() {
		return acceptedAt;
	}

	@Override
	public long getUpdatedAt() {
		return updatedAt;
	}
}