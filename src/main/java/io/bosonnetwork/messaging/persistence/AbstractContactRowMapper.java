package io.bosonnetwork.messaging.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel.Permission;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;
import io.bosonnetwork.messaging.impl.ChannelImpl;
import io.bosonnetwork.messaging.impl.ContactImpl;

public class AbstractContactRowMapper<T extends AbstractContact> implements RowMapper<T> {
	private Class<T> clazz;

	protected AbstractContactRowMapper(Class<T> clazz) {
		this.clazz = clazz;
	}

	private static String n(String prefix, String column) {
		return prefix == null ? column : prefix + column;
	}

	protected static AbstractContact map(ResultSet rs, Id contactId, String prefix) throws SQLException {
		Id id = contactId != null ? contactId : Id.of(rs.getBytes(n(prefix, "id")));
		int type = rs.getInt(n(prefix, "type"));
		boolean auto = rs.getBoolean(n(prefix, "auto"));

		byte[] value = rs.getBytes(n(prefix, "homePeerId"));
		Id homePeerId = rs.wasNull() ? null : Id.of(value);

		value = rs.getBytes(n(prefix, "sessionKey"));
		byte[] sessionKey = rs.wasNull() ? null : value;

		String name = rs.getString(n(prefix, "name"));
		boolean avatar = rs.getBoolean(n(prefix, "avatar"));

		String remark = rs.getString(n(prefix, "remark"));
		String tags = rs.getString(n(prefix, "tags"));
		boolean muted = rs.getBoolean(n(prefix, "muted"));
		boolean blocked = rs.getBoolean(n(prefix, "blocked"));
		long created = rs.getLong(n(prefix, "created"));
		long lastModified = rs.getLong(n(prefix, "lastModified"));
		long lastUpdated = rs.getLong(n(prefix, "lastUpdated"));

		boolean deleted = rs.getBoolean(n(prefix, "deleted"));
		int revision = rs.getInt(n(prefix, "revision"));
		boolean modified = rs.getBoolean(n(prefix, "modified"));

		AbstractContact contact;
		if (type == AbstractContact.Types.CONTACT) {
			contact = new ContactImpl(id, homePeerId, auto, sessionKey, name, avatar, remark, tags,
					muted, blocked, created, lastModified, lastUpdated, deleted, revision, modified);
		} else if (type == AbstractContact.Types.CHANNEL) {
			String notice = rs.getString(n(prefix, "notice"));

			value = rs.getBytes(n(prefix, "owner"));
			Id owner = rs.wasNull() ? null : Id.of(value);

			int perm = rs.getInt(n(prefix, "permission"));
			Permission permission = rs.wasNull() ? null : Permission.valueOf(perm);

			contact = new ChannelImpl(id, homePeerId, auto, sessionKey,
					name, avatar, notice, owner, permission, remark, tags,
					muted, created, lastModified, lastUpdated, deleted, revision, modified);
		} else {
			throw new SQLException("Unknown contact type: " + type);
		}

		return contact;
	}


	@SuppressWarnings("unchecked")
	@Override
	public T map(ResultSet rs, StatementContext ctx) throws SQLException {
		AbstractContact contact = map(rs, null, null);
		if (clazz.isInstance(contact))
			return (T)contact;
		else
			throw new SQLException("Mismatched contact type.");
	}
}