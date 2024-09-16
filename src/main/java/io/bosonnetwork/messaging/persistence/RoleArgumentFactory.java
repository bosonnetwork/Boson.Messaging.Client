package io.bosonnetwork.messaging.persistence;

import java.sql.PreparedStatement;
import java.sql.Types;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.NullArgument;
import org.jdbi.v3.core.argument.internal.strategies.LoggableBinderArgument;
import org.jdbi.v3.core.config.ConfigRegistry;

import io.bosonnetwork.messaging.Channel.Role;

public class RoleArgumentFactory extends AbstractArgumentFactory<Role> {
	public RoleArgumentFactory() {
		super(Types.SMALLINT);
	}

	@Override
	protected Argument build(Role value, ConfigRegistry config) {
		if (value == null)
			return new NullArgument(Types.SMALLINT);
		else {
			return new LoggableBinderArgument<>(value.value(), PreparedStatement::setInt);
		}
	}
}
