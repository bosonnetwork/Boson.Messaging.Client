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
 * Thrown to indicate that a specified contact does not exist.
 * This exception is a subclass of {@link MessagingException}
 * and can be used to explicitly signal a scenario where a contact
 * is being accessed but cannot be found.
 */
public class ContactNotExistsException extends MessagingException {
	private static final long serialVersionUID = 3170164510589184027L;

	/**
	 * Constructs a new ContactNotExistsException with the specified detail message.
	 * The detail message is saved for later retrieval by the {@link #getMessage()} method.
	 *
	 * @param message the detail message indicating the reason for the exception.
	 */
	public ContactNotExistsException(String message) {
		super(message);
	}

	/**
	 * Constructs a new ContactNotExistsException with the specified detail message
	 * and cause. The detail message provides additional information about the
	 * exception, and the cause is the underlying reason for the exception.
	 *
	 * @param message the detail message indicating the reason for the exception.
	 * @param cause the cause of the exception, which may be null to indicate that
	 *              the cause is nonexistent or unknown.
	 */
	public ContactNotExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}