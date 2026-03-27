package io.bosonnetwork.messaging.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;
import io.bosonnetwork.messaging.Conversation;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.impl.ConversationImpl;

public class ConversationRowMapper implements RowMapper<Conversation> {
	@Override
	public Conversation map(ResultSet rs, StatementContext ctx) throws SQLException {
		Message message = MessageRowMapper.map(rs);
		Id conversationId = message.getConversationId();
		AbstractContact contact = AbstractContactRowMapper.map(rs, conversationId, "c_");
		return new ConversationImpl(contact, message);
	}
}