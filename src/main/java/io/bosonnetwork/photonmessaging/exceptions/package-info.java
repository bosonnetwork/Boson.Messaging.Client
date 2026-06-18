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

/**
 * Exceptions raised by the Boson Messaging Client.
 *
 * <h2>Hierarchy</h2>
 *
 * All messaging exceptions extend {@link io.bosonnetwork.photonmessaging.exceptions.MessagingException}
 * (itself a {@code io.bosonnetwork.BosonException}). Asynchronous client methods report failures
 * by completing their returned {@code CompletableFuture} exceptionally with one of these types
 * rather than throwing; see {@link io.bosonnetwork.photonmessaging.MessagingClient} for the
 * overall asynchronous error contract.
 *
 * <h2>Top-level types and when they are thrown</h2>
 *
 * <table border="1">
 *   <caption>Messaging exceptions</caption>
 *   <tr><th>Exception</th><th>Raised when</th></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.MessagingException}</td>
 *       <td>Base type for all messaging failures.</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.RepositoryException}</td>
 *       <td>A local persistence (database) operation failed.</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.MalformedMessageException}</td>
 *       <td>A message is structurally invalid or violates the expected format.</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.MessageTimeoutException}</td>
 *       <td>A sent message was not acknowledged within the allowed time.</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.InsufficientPermissionException}</td>
 *       <td>The caller lacks the rights to perform the requested operation (e.g. not a channel
 *       owner/moderator).</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.ChannelNotExistsException} /
 *       {@link io.bosonnetwork.photonmessaging.exceptions.ContactNotExistsException}</td>
 *       <td>The referenced channel or contact does not exist locally.</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.NotChannelMemberException} /
 *       {@link io.bosonnetwork.photonmessaging.exceptions.AlreadyMemberException}</td>
 *       <td>The user is not a member of the channel, or is already a member.</td></tr>
 *   <tr><td>{@link io.bosonnetwork.photonmessaging.exceptions.RevisionNotMonotonicException}</td>
 *       <td>A contact-synchronization revision went backwards / was not monotonic.</td></tr>
 * </table>
 *
 * <p>Failures reported by the remote messaging service are surfaced as
 * {@link io.bosonnetwork.photonmessaging.exceptions.rpc.RpcException} and its subtypes; see that
 * package for the error-code mapping.
 */
@NullUnmarked
package io.bosonnetwork.photonmessaging.exceptions;

import org.jspecify.annotations.NullUnmarked;