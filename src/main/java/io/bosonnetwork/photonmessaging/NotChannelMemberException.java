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

package io.bosonnetwork.photonmessaging;

/**
 * This exception is thrown to indicate that an operation failed because the user
 * is not a member of the specified channel.
 * <p>
 * It is a specific type of {@link MessagingException} that is used when an action
 * requires the user to be part of a channel, but the user is not currently a member.
 */
public class NotChannelMemberException extends MessagingException {
	private static final long serialVersionUID = -372522546897916642L;

	/**
	 * Constructs a new NotChannelMemberException with the specified detail message.
	 * This exception is thrown to indicate that an operation failed because the user
	 * is not a member of the specified channel.
	 *
	 * @param message the detail message. The detail message is saved for
	 *                later retrieval by the {@link #getMessage()} method.
	 */
	public NotChannelMemberException(String message) {
		super(message);
	}

	/**
	 * Constructs a new NotChannelMemberException with the specified detail message
	 * and cause. This exception is thrown to indicate that an operation failed
	 * because the user is not a member of the specified channel.
	 *
	 * @param message the detail message, which is saved for later retrieval by the
	 *                {@link #getMessage()} method.
	 * @param cause   the cause, which is saved for later retrieval by the {@link #getCause()}
	 *                method. A {@code null} value indicates that the cause is nonexistent
	 *                or unknown.
	 */
	public NotChannelMemberException(String message, Throwable cause) {
		super(message, cause);
	}
}