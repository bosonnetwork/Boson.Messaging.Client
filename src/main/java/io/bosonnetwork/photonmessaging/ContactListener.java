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

import java.util.List;

import io.bosonnetwork.Id;

/**
 * A listener interface for receiving contact-related events.
 * Implementations of this interface can be used to monitor when contacts
 * are added, updated, removed, or when the entire contact list is cleared.
 */
public interface ContactListener {
	/**
	 * Called when a new contact has been added to the local contact list.
	 *
	 * @param contact the {@link Contact} that was added.
	 */
	void onContactAdded(Contact contact);

	/**
	 * Called when one or more existing contacts have been updated.
	 *
	 * @param contacts the list of updated {@link Contact} objects.
	 */
	void onContactsUpdated(List<Contact> contacts);

	/**
	 * Called when one or more contacts have been removed from the local contact list.
	 *
	 * @param contactIds the list of {@link Id}s of the removed contacts.
	 */
	void onContactRemoved(List<Id> contactIds);

	/**
	 * Called when all contacts have been cleared from the local contact list.
	 */
	void onContactsCleared();
}