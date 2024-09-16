package io.bosonnetwork.messaging.impl;

import io.bosonnetwork.messaging.Contact;
import io.bosonnetwork.messaging.Conversation;
import io.bosonnetwork.messaging.Message;

public class ConversationImpl extends Conversation {
	public ConversationImpl(Contact interlocutor, Message lastMessage) {
		super(interlocutor, lastMessage);
	}

	public ConversationImpl(Contact interlocutor) {
		super(interlocutor);
	}

	@Override
	public void update(Message message) {
		super.update(message);
	}

	@Override
	public void updateInterlocutor(Contact contact) {
		super.updateInterlocutor(contact);
	}
}
