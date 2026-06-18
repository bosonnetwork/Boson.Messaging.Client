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
 * The public API of the Boson federated messaging client.
 *
 * <p>This package is the primary surface external developers interact with. The implementation
 * lives in {@code io.bosonnetwork.photonmessaging.impl} and should not be used directly; obtain a
 * client through the factory methods on {@link io.bosonnetwork.photonmessaging.MessagingClient}.
 *
 * <h2>Entry point</h2>
 *
 * {@link io.bosonnetwork.photonmessaging.MessagingClient} is the central interface for managing
 * conversations, messages, contacts/friends, channels and sessions. Create one from a
 * {@link io.bosonnetwork.photonmessaging.Configuration} (built via
 * {@link io.bosonnetwork.photonmessaging.Configuration#builder()} or
 * {@link io.bosonnetwork.photonmessaging.Configuration#fromMap(java.util.Map)}):
 *
 * <pre>{@code
 * Configuration config = Configuration.builder()
 *         .service(servicePeerId, "mqtts://host:8883")
 *         .userKey(userPrivateKey)
 *         .deviceKey(devicePrivateKey)
 *         .database("jdbc:sqlite:photonmessaging.db", 1)
 *         .build();
 *
 * MessagingClient client = MessagingClient.create(node, config);
 *
 * client.addMessageListener(new MessageListener() {
 *     public void onMessage(Message message) { // handle incoming
 *     }
 *     public void onSent(Message message) {     // handle sent ack
 *     }
 * });
 *
 * client.start()
 *       .thenCompose(v -> client.message().to(recipientId).contentText("hello").send())
 *       .whenComplete((sent, err) -> { // handle result
 *       });
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 *
 * A client is <b>restartable</b>: {@link io.bosonnetwork.photonmessaging.MessagingClient#start()}
 * and {@link io.bosonnetwork.photonmessaging.MessagingClient#stop()} are atomic and idempotent, and
 * a stopped client may be started again. {@code isRunning()}/{@code isConnected()}/{@code isReady()}
 * expose the current state, and {@link io.bosonnetwork.photonmessaging.ConnectionListener} reports
 * connection transitions.
 *
 * <h2>Asynchronous model and error contract</h2>
 *
 * Operations are non-blocking and return {@link java.util.concurrent.CompletableFuture}. Failures
 * are reported by completing the future exceptionally with a
 * {@link io.bosonnetwork.photonmessaging.exceptions.MessagingException} subtype (or an
 * {@link io.bosonnetwork.photonmessaging.exceptions.rpc.RpcException} for service-side errors);
 * argument validation fails synchronously with {@link java.lang.NullPointerException} /
 * {@link java.lang.IllegalArgumentException}, and calling an operation while the client is not
 * running fails fast with {@link java.lang.IllegalStateException}. See
 * {@link io.bosonnetwork.photonmessaging.MessagingClient} for the full contract.
 *
 * <h2>Key types</h2>
 *
 * <ul>
 *   <li><b>Messaging:</b> {@link io.bosonnetwork.photonmessaging.Message} (and its
 *       {@code Builder}/{@code Content}), {@link io.bosonnetwork.photonmessaging.Conversation},
 *       {@link io.bosonnetwork.photonmessaging.ContentType} and
 *       {@link io.bosonnetwork.photonmessaging.ContentDisposition}.</li>
 *   <li><b>Contacts &amp; channels:</b> {@link io.bosonnetwork.photonmessaging.Contact},
 *       {@link io.bosonnetwork.photonmessaging.Channel} (with {@code Permission}/{@code Role}/
 *       {@code Member}), {@link io.bosonnetwork.photonmessaging.FriendRequest} and
 *       {@link io.bosonnetwork.photonmessaging.InviteTicket}.</li>
 *   <li><b>Sessions:</b> {@link io.bosonnetwork.photonmessaging.SessionInfo}.</li>
 *   <li><b>Listeners:</b> {@link io.bosonnetwork.photonmessaging.ConnectionListener},
 *       {@link io.bosonnetwork.photonmessaging.MessageListener},
 *       {@link io.bosonnetwork.photonmessaging.ChannelListener},
 *       {@link io.bosonnetwork.photonmessaging.ContactListener},
 *       {@link io.bosonnetwork.photonmessaging.SessionListener} and
 *       {@link io.bosonnetwork.photonmessaging.FriendRequestListener}.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * The client runs on a Vert.x event loop; returned futures complete on that context. Listener
 * callbacks are invoked on the event-loop thread and must not block. Listeners may be registered
 * and removed from any thread, including before {@code start()}.
 *
 * @see io.bosonnetwork.photonmessaging.MessagingClient
 * @see io.bosonnetwork.photonmessaging.Configuration
 * @see io.bosonnetwork.photonmessaging.exceptions
 */

@NullMarked
package io.bosonnetwork.photonmessaging;

import org.jspecify.annotations.NullMarked;