package io.bosonnetwork.messaging.persistence;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.impl.MessageImpl;
import io.bosonnetwork.utils.Json;

public class MessageRowMapper implements RowMapper<Message> {
	protected static Message map(ResultSet rs) throws SQLException {
		try {
			long rid = rs.getLong("rid");
			Id conversationId = Id.of(rs.getBytes("conversationId"));

			int version = rs.getInt("version");

			Id from = Id.of(rs.getBytes("from"));
			if (from.equals(conversationId))
				from = conversationId;

			Id to = Id.of(rs.getBytes("to"));
			if (to.equals(conversationId))
				to = conversationId;

			long serialNumber = rs.getLong("serialNumber");
			long created = rs.getLong("created");
			int messageType = rs.getInt("messageType");
			byte[] props = rs.getBytes("properties");
			Map<String, Object> properties = rs.wasNull() ? null :
					Json.cborMapper().readValue(props, new TypeReference<Map<String, Object>>() {});
			String contentType = rs.getString("contentType");
			String contentDisposition = rs.getString("contentDisposition");
			byte[] body = rs.getBytes("body");

			long timestamp = rs.getLong("timestamp");

			return new MessageImpl(rid, conversationId, version,
					from, to, serialNumber, created, messageType, properties,
					contentType, contentDisposition, body, timestamp);
		} catch (IOException e) {
			throw new SQLException("Invalid data of the properties column", e);
		}
	}

	@Override
	public Message map(ResultSet rs, StatementContext ctx) throws SQLException {
		return map(rs);
	}
}
