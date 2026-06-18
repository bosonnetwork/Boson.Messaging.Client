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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.photonmessaging.ConnectionListener;

public class PhotonMessagingListenersTests {
	private static class RecordingConnectionListener implements ConnectionListener {
		final List<String> events = new ArrayList<>();

		@Override
		public void onConnecting() {
			events.add("connecting");
		}

		@Override
		public void onConnected() {
			events.add("connected");
		}

		@Override
		public void onReady() {
			events.add("ready");
		}

		@Override
		public void onDisconnected() {
			events.add("disconnected");
		}
	}

	// Regression guard for the onDisconnected dispatch bug: the facade overrides every
	// ConnectionListener method (including the default onDisconnected) so disconnect events
	// reach the registered listener instead of hitting the inherited no-op default.
	@Test
	void dispatchesAllConnectionEventsIncludingDisconnected() {
		PhotonMessagingListeners listeners = new PhotonMessagingListeners();
		RecordingConnectionListener listener = new RecordingConnectionListener();
		listeners.addConnectionListener(listener);

		listeners.onConnecting();
		listeners.onConnected();
		listeners.onReady();
		listeners.onDisconnected();

		assertEquals(List.of("connecting", "connected", "ready", "disconnected"), listener.events);
	}

	@Test
	void removedListenerReceivesNoFurtherEvents() {
		PhotonMessagingListeners listeners = new PhotonMessagingListeners();
		RecordingConnectionListener listener = new RecordingConnectionListener();
		listeners.addConnectionListener(listener);
		listeners.removeConnectionListener(listener);

		listeners.onDisconnected();

		assertEquals(List.of(), listener.events);
	}

	@Test
	void dispatchToMultipleListenersIsExceptionIsolated() {
		PhotonMessagingListeners listeners = new PhotonMessagingListeners();
		ConnectionListener throwing = new ConnectionListener() {
			@Override
			public void onReady() {
			}

			@Override
			public void onDisconnected() {
				throw new RuntimeException("boom");
			}
		};
		RecordingConnectionListener good = new RecordingConnectionListener();
		listeners.addConnectionListener(throwing);
		listeners.addConnectionListener(good);

		// The throwing listener must not prevent the good listener from being notified.
		listeners.onDisconnected();

		assertEquals(List.of("disconnected"), good.events);
	}
}
