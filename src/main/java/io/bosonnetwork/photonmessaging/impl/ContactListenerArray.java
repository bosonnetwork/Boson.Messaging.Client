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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.ContactListener;

public class ContactListenerArray extends CopyOnWriteArrayList<ContactListener> implements ContactListener {
	private static final long serialVersionUID = -6724210075837468138L;
	private final Logger log;

	public ContactListenerArray(ContactListener listener, Logger log) {
		super();
		this.log = log;
		add(listener);
	}

	@Override
	public void onContactAdded(Contact contact) {
		for (ContactListener listener : this) {
			try {
				listener.onContactAdded(contact);
			} catch (Throwable t) {
				log.error("Error dispatching onContactAdded to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onContactsUpdated(List<Contact> contacts) {
		for (ContactListener listener : this) {
			try {
				listener.onContactsUpdated(contacts);
			} catch (Throwable t) {
				log.error("Error dispatching onContactsUpdated to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onContactsRemoved(List<Id> contactIds) {
		for (ContactListener listener : this) {
			try {
				listener.onContactsRemoved(contactIds);
			} catch (Throwable t) {
				log.error("Error dispatching onContactsRemoved to listener: {}", listener, t);
			}
		}
	}

	@Override
	public void onContactsCleared() {
		for (ContactListener listener : this) {
			try {
				listener.onContactsCleared();
			} catch (Throwable t) {
				log.error("Error dispatching onContactsCleared to listener: {}", listener, t);
			}
		}
	}
}