package io.bosonnetwork.messaging;

import java.util.Objects;

import io.bosonnetwork.Id;

public abstract class Conversation implements Comparable<Conversation> {
	public static final int MAX_SNIPPET_LENGTH = 128;
	public static final String DEFAULT_AVATAR = null;

	private Contact interlocutor;

	private Message lastMessage;
	private transient String snippet;

	protected Conversation(Contact interlocutor, Message lastMessage) {
		this.interlocutor = interlocutor;
		update(lastMessage);
	}

	protected Conversation(Contact interlocutor) {
		this.interlocutor = interlocutor;
	}

	public Id getId() {
		return interlocutor.getId();
	}

	public String getTitle() {
		return interlocutor.getDisplayName();
	}

	public String getAvatar() {
		return interlocutor.hasAvatar() ? interlocutor.getAvatar() : DEFAULT_AVATAR;
	}

	public String getSnippet() {
		if (snippet == null) {
			if (lastMessage == null) {
				snippet = "";
			} else {
				String contentType = lastMessage.getContentType();
				if (contentType.startsWith("text/")) {
					snippet = lastMessage.getBodyAsText().trim();
					if (snippet.length() > MAX_SNIPPET_LENGTH)
						snippet = snippet.substring(0, MAX_SNIPPET_LENGTH);
				} else if (contentType.startsWith("image/")) {
					snippet = "(Image)";
				} else if (contentType.startsWith("audio/")) {
					snippet = "(Audio)";
				} else if (contentType.startsWith("video/")) {
					snippet = "(Video)";
				} else {
					snippet = "(Attachment)";
				}
			}
		}

		return snippet;
	}

	public long getUpdated() {
		return lastMessage != null ? lastMessage.getCreated() : 0;
	}

	public Contact getInterlocutor() {
		return interlocutor;
	}

	protected void updateInterlocutor(Contact contact) {
		Objects.requireNonNull(contact, "contact");
		if (!contact.getId().equals(interlocutor.getId()))
			throw new IllegalArgumentException("Contact does not match the conversation");

		this.interlocutor = contact;
	}

	protected void update(Message message) {
		Objects.requireNonNull(message, "message");
		if (!message.getConversationId().equals(interlocutor.getId()))
			throw new IllegalArgumentException("Message does not match the conversation");

		this.lastMessage = message;
		this.snippet = null; // invalidate the previous snippet
	}

	public boolean is(Conversation conversation) {
		if (this == conversation)
			return true;

		return this.getId().equals(conversation.getId());
	}
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof Conversation) {
			Conversation that = (Conversation)o;
			return Objects.equals(this.getId(), that.getId());
		}

		return false;
	}

	@Override
	public int compareTo(Conversation o) {
 		int r = Long.compare(getUpdated(), o.getUpdated());
		/*
 		if (r == 0)
			r = getTitle().compareToIgnoreCase(o.getTitle());
		*/
		if (r == 0)
			r = getId().compareTo(o.getId());

		return r;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder();

		repr.append("Conversation: ")
			.append(getTitle())
			.append('[')
			.append(getId().toString()).append(", ")
			.append(getSnippet()).append(", ")
			.append(getUpdated())
			.append(']');

		return repr.toString();
	}
}
