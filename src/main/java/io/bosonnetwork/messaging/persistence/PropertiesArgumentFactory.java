package io.bosonnetwork.messaging.persistence;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Map;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.NullArgument;
import org.jdbi.v3.core.argument.internal.strategies.LoggableBinderArgument;
import org.jdbi.v3.core.config.ConfigRegistry;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.bosonnetwork.json.Json;

public class PropertiesArgumentFactory extends AbstractArgumentFactory<Map<String, Object>> {
	public PropertiesArgumentFactory() {
		super(Types.BLOB);
	}

	@Override
	protected Argument build(Map<String, Object> value, ConfigRegistry config) {
		if (value == null || value.isEmpty())
			return new NullArgument(Types.BLOB);
		else {
			try {
				byte[] data = Json.cborMapper().writeValueAsBytes(value);
				return new LoggableBinderArgument<>(data, PreparedStatement::setBytes);
			} catch (JsonProcessingException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}