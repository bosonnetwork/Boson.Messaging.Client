package io.bosonnetwork.messaging.persistence;

import java.util.Optional;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface Configuration {
	@SqlUpdate("INSERT INTO configuration VALUES(:key, :value)")
	int put(@Bind("key") String key, @Bind("value") byte[] value);

	@SqlQuery("SELECT value FROM configuration WHERE key = ?")
	Optional<byte[]> get(String key);
}
