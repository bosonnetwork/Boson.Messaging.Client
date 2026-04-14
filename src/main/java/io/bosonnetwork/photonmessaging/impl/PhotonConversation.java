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

import java.util.Objects;
import java.util.function.Function;

import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.Conversation;
import io.bosonnetwork.photonmessaging.Message;

public class PhotonConversation implements Conversation {
	public static final int MAX_SNIPPET_LENGTH = 128;

	private PhotonContact contact;
	private Message lastMessage;
	private transient String preview;

	private transient Function<PhotonContact, SessionContext> sessionContextFactory;
	private transient SessionContext sessionContext;

	protected PhotonConversation(PhotonContact contact, Message lastMessage) {
		this.contact = contact;
		update(lastMessage);
		sessionContext = null;
	}

	protected PhotonConversation(PhotonContact contact) {
		this.contact = contact;
	}

	@Override
	public PhotonContact getContact() {
		return contact;
	}

	@Override
	public boolean isChannel() {
		return contact.getType() == Contact.Type.CHANNEL;
	}

	@Override
	public String getPreview() {
		String snippet = preview;
		if (snippet == null && lastMessage != null) {
			Message.Content content = lastMessage.getPayloadAsContent();
			String contentType = content.getContentType();
			if (contentType.startsWith("text/")) {
				String body = content.asText().trim();
				snippet = body.length() < MAX_SNIPPET_LENGTH ? body : body.substring(0, MAX_SNIPPET_LENGTH - 3) + "...";
			} else if (contentType.startsWith("image/")) {
				preview = "(Image)";
			} else if (contentType.startsWith("audio/")) {
				preview = "(Audio)";
			} else if (contentType.startsWith("video/")) {
				preview = "(Video)";
			} else {
				if (content.getContentDisposition() != null)
					preview = "(Attachment)";
				else
					preview = "(Unknown)";
			}
		}

		return snippet;
	}

	@Override
	public long getUpdatedAt() {
		return lastMessage != null ? lastMessage.getReceivedAt() : 0;
	}

	protected void updateParticipant(PhotonContact contact) {
		Objects.requireNonNull(contact, "contact");
		if (!contact.getId().equals(this.contact.getId()))
			throw new IllegalArgumentException("Contact does not match the conversation");

		this.contact = contact;
	}

	protected void update(Message message) {
		Objects.requireNonNull(message, "message");
		if (!message.getConversationId().equals(contact.getId()))
			throw new IllegalArgumentException("Message does not match the conversation");

		this.lastMessage = message;
		this.preview = null;
	}

	void setSessionContextFactory(Function<PhotonContact, SessionContext> factory) {
		this.sessionContextFactory = factory;
	}

	protected SessionContext getSessionContext() {
		SessionContext ctx = sessionContext;
		if (ctx == null) {
			if (sessionContextFactory == null)
				throw new IllegalStateException("INTERNAL ERROR: Session context factory not set");

			ctx = sessionContextFactory.apply(contact);
			sessionContext = ctx;
		}

		return sessionContext;
	}

	public boolean is(Conversation conversation) {
		if (this == conversation)
			return true;

		return this.getId().equals(conversation.getId());
	}

	@Override public int hashCode() {
		return 0x6030AC0A + getId().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof Conversation that)
			return this.getId().equals(that.getId());

		return false;
	}

	@Override
	public int compareTo(Conversation o) {
		int r = Long.compare(getUpdatedAt(), o.getUpdatedAt());
		return r != 0 ? r : getId().compareTo(o.getId());
	}

	@Override
	public String toString() {
		return "Conversation: " +
				getTitle() +
				'[' +
				getId().toString() + ", " +
				getPreview() + ", " +
				getUpdatedAt() +
				']';
	}
}