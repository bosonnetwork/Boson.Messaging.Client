package io.bosonnetwork.messaging.persistence;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.utils.Json;

public class PropertiesColumnMapper implements ColumnMapper<Map<String, Object>> {
	@Override
	public Map<String, Object> map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
		try {
			byte[] value = r.getBytes(columnNumber);
			return value == null || value.length == 0 ? null : Json.cborMapper().readValue(value, new TypeReference<Map<String, Object>>() {});
		} catch (IOException e) {
			throw new SQLException(e);
		}
	}
}