package io.bosonnetwork.messaging.persistence;

import java.util.Collection;
import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterArgumentFactory;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Message;

public interface Messages {
	@SqlUpdate("""
			INSERT INTO messages(conversationId, version, "from", "to", serialNumber, created, messageType, properties, contentType, contentDisposition, body, "timestamp")
			VALUES(:conversationId, :version, :from, :to, :serialNumber, :created, :messageType, :properties, :contentType, :contentDisposition, :body, :timestamp)
			""")
	@RegisterArgumentFactory(PropertiesArgumentFactory.class)
	@GetGeneratedKeys("sid")
	long put(@BindBean Message message);

	@SqlBatch("""
			INSERT INTO messages(conversationId, version, "from", "to", serialNumber, created, messageType, properties, contentType, contentDisposition, body, "timestamp")
			VALUES(:conversationId, :version, :from, :to, :serialNumber, :created, :messageType, :properties, :contentType, :contentDisposition, :body, :timestamp)
			""")
	@RegisterArgumentFactory(PropertiesArgumentFactory.class)
	@GetGeneratedKeys("sid")
	// SQLite can not return the generated keys in batch mode
	long[] putAll(@BindBean Collection<Message> messages);

	@SqlUpdate("UPDATE messages SET timestamp = :timestamp WHERE sid = :sid")
	int updateTimestamp(@BindBean Message message);

	@SqlQuery("SELECT * FROM messages WHERE sid = ?")
	@RegisterRowMapper(MessageRowMapper.class)
	Message get(long sid);

	@SqlQuery("""
			SELECT * FROM messages
			WHERE conversationId = :conversationId AND created <= :begin AND created > :end
			ORDER BY created DESC, serialNumber DESC
			""")
	@RegisterRowMapper(MessageRowMapper.class)
	// begin(inclusive), end(exclusive)
	List<Message> get(@Bind("conversationId") Id conversationId, @Bind("begin") long begin, @Bind("end") long end);

	@SqlQuery("""
			SELECT * FROM messages
			WHERE conversationId = :conversationId AND created <= :since
			ORDER BY created DESC, serialNumber DESC
			LIMIT :limit OFFSET :offset
			""")
	@RegisterRowMapper(MessageRowMapper.class)
	// since(inclusive)
	List<Message> get(@Bind("conversationId") Id conversationId, @Bind("since") long since, @Bind("limit") int limit, @Bind("offset") int offset);

	@SqlUpdate("DELETE FROM messages WHERE sid = ?")
	int remove(long sid);

	@SqlUpdate("DELETE FROM messages WHERE sid IN (<sids>)")
	int removeAll(@BindList("sids") Collection<Long> sids);

	@SqlUpdate("DELETE FROM messages WHERE conversationId = :conversationId")
	int removeAll(@Bind("conversationId") Id conversationId);
}
