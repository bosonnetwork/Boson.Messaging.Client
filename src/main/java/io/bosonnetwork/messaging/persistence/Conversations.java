package io.bosonnetwork.messaging.persistence;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Conversation;

public interface Conversations {
	@SqlQuery("""
			SELECT m.*, c.type AS c_type, c.auto AS c_auto, c.homePeerId as c_homePeerId,
				c.sessionKey AS c_sessionKey, c.name AS c_name, c.avatar AS c_avatar,
				c.notice AS c_notice, c.owner AS c_owner, c.permission AS c_permission,
				c.remark AS c_remark, c.tags AS c_tags, c.muted AS c_muted, c.blocked AS c_blocked,
				c.created AS c_created, c.lastModified AS c_lastModified, c.lastUpdated AS c_lastUpdated,
				c.deleted AS c_deleted, c.revision AS c_revision, c.modified AS c_modified
			FROM (SELECT *, MAX(created) AS mc FROM messages WHERE conversationId = ?) AS m
			LEFT JOIN contacts AS c ON m.conversationId = c.id
			""")
	@RegisterRowMapper(ConversationRowMapper.class)
	Conversation get(Id conversationId);

	@SqlQuery("""
			SELECT m.*, c.type AS c_type, c.auto AS c_auto, c.homePeerId as c_homePeerId,
				c.sessionKey AS c_sessionKey, c.name AS c_name, c.avatar AS c_avatar,
				c.notice AS c_notice, c.owner AS c_owner, c.permission AS c_permission,
				c.remark AS c_remark, c.tags AS c_tags, c.muted AS c_muted, c.blocked AS c_blocked,
				c.created AS c_created, c.lastModified AS c_lastModified, c.lastUpdated AS c_lastUpdated,
				c.deleted AS c_deleted, c.revision AS c_revision, c.modified AS c_modified
			FROM (SELECT *, MAX(created) AS mc FROM messages GROUP BY conversationId) AS m
			LEFT JOIN contacts AS c ON m.conversationId = c.id
			ORDER BY m.created DESC
			""")
	@RegisterRowMapper(ConversationRowMapper.class)
	List<Conversation> getAll();

	@SqlQuery("SELECT EXISTS (SELECT 1 FROM messages WHERE conversationId = ?)")
	boolean exists(Id conversationId);
}
