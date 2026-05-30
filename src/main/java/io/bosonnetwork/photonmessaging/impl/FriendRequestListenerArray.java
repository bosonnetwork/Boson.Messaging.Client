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

import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.FriendRequestListener;

public class FriendRequestListenerArray extends CopyOnWriteArrayList<FriendRequestListener> implements FriendRequestListener {
	private static final long serialVersionUID = -3378257093435511382L;
	private static final Logger log = LoggerFactory.getLogger(FriendRequestListenerArray.class);

	public FriendRequestListenerArray(FriendRequestListener existing, FriendRequestListener newListener) {
		super();
		add(existing);
		add(newListener);
	}

	@Override
	public void onFriendRequest(Id userId, String hello) {
		for (FriendRequestListener listener : this) {
			try {
				listener.onFriendRequest(userId, hello);
			} catch (Throwable t) {
				log.error("Error dispatching onFriendRequest to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onFriendRequestAccepted(Id userId) {
		for (FriendRequestListener listener : this) {
			try {
				listener.onFriendRequestAccepted(userId);
			} catch (Throwable t) {
				log.error("Error dispatching onFriendRequestAccepted to listener: {}", listener, t);
			}
		}
	}
}