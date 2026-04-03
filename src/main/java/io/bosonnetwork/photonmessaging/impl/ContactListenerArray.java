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

import java.util.ArrayList;
import java.util.List;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.ContactListener;

public class ContactListenerArray extends ArrayList<ContactListener> implements ContactListener {
	private static final long serialVersionUID = -6724210075837468138L;

	public ContactListenerArray(ContactListener existing, ContactListener newListener) {
		super();
		add(existing);
		add(newListener);
	}

	@Override
	public void onContactAdded(Contact contact) {
		for (ContactListener listener : this)
			listener.onContactAdded(contact);
	}

	@Override
	public void onContactsUpdated(List<Contact> contacts) {
		for (ContactListener listener : this)
			listener.onContactsUpdated(contacts);
	}

	@Override
	public void onContactRemoved(List<Id> contactIds) {
		for (ContactListener listener : this)
			listener.onContactRemoved(contactIds);
	}

	@Override
	public void onContactsCleared() {
		for (ContactListener listener : this)
			listener.onContactsCleared();
	}
}