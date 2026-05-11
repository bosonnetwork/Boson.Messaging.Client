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

public enum Topic {
	USER_INBOX("u/i"),
	USER_OUTBOX("u/o"),
	DEVICE_INBOX("d/i"),
	DEVICE_OUTBOX("d/o");

	private final String value;

	Topic(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static Topic of(String topicName) {
		return switch (topicName) {
			case "u/i" -> USER_INBOX;
			case "u/o" -> USER_OUTBOX;
			case "d/i" -> DEVICE_INBOX;
			case "d/o" -> DEVICE_OUTBOX;
			default -> throw new IllegalArgumentException("Invalid topic name: " + topicName);
		};
	}

	@Override
	public String toString() {
		return value;
	}
}