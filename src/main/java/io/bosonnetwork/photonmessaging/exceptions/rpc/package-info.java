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
 * Exceptions representing failures reported by the remote messaging service over RPC.
 *
 * <p>All extend {@link io.bosonnetwork.photonmessaging.exceptions.rpc.RpcException}, which carries
 * a numeric error {@linkplain io.bosonnetwork.photonmessaging.exceptions.rpc.RpcException#getCode()
 * code}. When an RPC call fails, the client maps the service's error code to the matching subtype
 * below; unknown codes surface as a plain {@code RpcException} preserving the original code.
 *
 * <h2>Error-code &rarr; exception mapping</h2>
 *
 * <table border="1">
 *   <caption>RPC error codes and their exception types</caption>
 *   <tr><th>Code</th><th>Exception</th><th>Meaning</th></tr>
 *   <tr><td>-1</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.RpcInternalException}</td><td>Internal service error</td></tr>
 *   <tr><td>-2</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.MalformedRpcRequestException}</td><td>Malformed request</td></tr>
 *   <tr><td>-3</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.MalformedRpcResponseException}</td><td>Malformed response</td></tr>
 *   <tr><td>-4</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.InvalidRpcMethodException}</td><td>Unknown/invalid method</td></tr>
 *   <tr><td>-5</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.UnimplementedRpcMethodException}</td><td>Method not implemented</td></tr>
 *   <tr><td>-6</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.InvalidRpcParametersException}</td><td>Invalid parameters</td></tr>
 *   <tr><td>-7</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.InvalidRpcResultException}</td><td>Invalid result</td></tr>
 *   <tr><td>-8</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ForbiddenRpcRequestException}</td><td>Request forbidden</td></tr>
 *   <tr><td>-9</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.RpcTimeoutException}</td><td>Service-side timeout</td></tr>
 *   <tr><td>-101</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.SessionNotExistsException}</td><td>Session does not exist</td></tr>
 *   <tr><td>-102</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.RevokeCurrentSessionException}</td><td>Attempt to revoke the current session</td></tr>
 *   <tr><td>-201</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ContactStoreException}</td><td>Contact store error</td></tr>
 *   <tr><td>-202</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.RevisionOutdateException}</td><td>Contact revision out of date</td></tr>
 *   <tr><td>-203</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.MalformedMutationDataException}</td><td>Malformed contact-mutation data</td></tr>
 *   <tr><td>-204</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ContactNotExistsException}</td><td>Contact does not exist</td></tr>
 *   <tr><td>-205</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ContactAlreadyExistsException}</td><td>Contact already exists</td></tr>
 *   <tr><td>-301</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ChannelNotExistsException}</td><td>Channel does not exist</td></tr>
 *   <tr><td>-302</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ChannelAlreadyExistsException}</td><td>Channel already exists</td></tr>
 *   <tr><td>-303</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.AlreadyJoinedException}</td><td>Already joined the channel</td></tr>
 *   <tr><td>-304</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.InvalidInviteTicket}</td><td>Invalid invite ticket</td></tr>
 *   <tr><td>-305</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ForbiddenNonChannelMemberException}</td><td>Caller is not a channel member</td></tr>
 *   <tr><td>-306</td><td>{@link io.bosonnetwork.photonmessaging.exceptions.rpc.ForbiddenBannedMemberException}</td><td>Caller is a banned channel member</td></tr>
 * </table>
 *
 * <p>The numeric codes themselves are defined by {@code io.bosonnetwork.photonmessaging.impl.rpc.RpcErrorCode}
 * (internal); the mapping above is performed when a failed {@code RpcResponse} is turned into a
 * thrown/completed exception.
 */
@NullUnmarked
package io.bosonnetwork.photonmessaging.exceptions.rpc;

import org.jspecify.annotations.NullUnmarked;