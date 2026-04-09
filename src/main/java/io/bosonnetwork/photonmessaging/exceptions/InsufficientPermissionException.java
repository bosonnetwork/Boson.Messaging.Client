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

package io.bosonnetwork.photonmessaging.exceptions;

/**
 * This exception is thrown to indicate that a user or process does not have the
 * necessary permissions to perform an operation within the messaging framework.
 */
public class InsufficientPermissionException extends MessagingException {
	private static final long serialVersionUID = 8334428958208589291L;

	/**
	 * Constructs a new {@code InsufficientPermissionException} with the specified
	 * detail message, which provides information about the permission-related issue.
	 *
	 * @param message the detail message describing the specific permission issue.
	 */
	public InsufficientPermissionException(String message) {
		super(message);
	}

	/**
	 * Constructs a new {@code InsufficientPermissionException} with the specified
	 * detail message and cause. This constructor allows for both a description of
	 * the permission issue and an underlying cause to be provided.
	 *
	 * @param message the detail message describing the specific permission issue.
	 * @param cause   the cause of the exception, which can help identify the
	 *                root problem (may be {@code null} if no cause is available).
	 */
	public InsufficientPermissionException(String message, Throwable cause) {
		super(message, cause);
	}
}