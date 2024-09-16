package io.bosonnetwork.messaging.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel.Member;
import io.bosonnetwork.messaging.Channel.Role;

public class ChannelMemberRowMapper implements RowMapper<Member> {
	@Override
	public Member map(ResultSet rs, StatementContext ctx) throws SQLException {
		Id id = Id.of(rs.getBytes("memberId"));
		int value = rs.getInt("role");
		Role role = rs.wasNull() ? null : Role.valueOf(value);
		long joined = rs.getLong("joined");

		return new Member(id, role, joined);
	}
}
