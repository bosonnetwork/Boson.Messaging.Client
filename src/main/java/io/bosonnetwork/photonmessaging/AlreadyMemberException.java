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
 * This exception is thrown when a member is already present in the channel.
 * It serves as a specific type of {@link MessagingException}
 * to indicate this specific error condition.
 */
public class AlreadyMemberException extends MessagingException {
	private static final long serialVersionUID = -3347433729528708826L;

	/**
	 * Constructs a new AlreadyMemberException with the specified detail message.
	 * This exception is thrown to indicate that a member is already part of a channel.
	 *
	 * @param message the detail message to provide additional context about the error.
	 */
	public AlreadyMemberException(String message) {
		super(message);
	}

	/**
	 * Constructs a new AlreadyMemberException with the specified detail message and cause.
	 * This exception is thrown to indicate that a member is already part of a channel.
	 *
	 * @param message the detail message providing additional context about the error.
	 * @param cause the cause of the exception, which can be used to retrieve the original exception
	 *              that led to this error (can be null if no such cause exists or is unknown).
	 */
	public AlreadyMemberException(String message, Throwable cause) {
		super(message, cause);
	}
}