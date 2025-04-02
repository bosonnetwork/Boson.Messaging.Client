package io.bosonnetwork.messaging.persistence;

import java.util.Collection;
import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterArgumentFactory;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Contact;

public interface Contacts {
	// Version
	@SqlUpdate("""
			INSERT INTO contacts_version VALUES(0, :id, :created)
			ON CONFLICT(rid) DO UPDATE SET
				id=EXCLUDED.id, created=EXCLUDED.created
			""")
	int putVersion(@Bind("id") String versionId, @Bind("created") long created);

	@SqlQuery("SELECT id FROM contacts_version WHERE rid=0")
	String getVersion();

	@SqlUpdate("DELETE FROM contacts_version")
	long clearVersion();

	// Contacts
	@SqlUpdate("""
			INSERT INTO contacts(id, type, auto, homePeerId, sessionKey, name, avatar, remark, tags, muted, blocked, created, lastModified, lastUpdated, deleted, revision, modified)
			VALUES(:id, :type, :auto, :homePeerId, :sessionKey, :name, :avatar, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated, :deleted, :revision, :modified)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, homePeerId=EXCLUDED.homePeerId,
				sessionKey=EXCLUDED.sessionKey, name=EXCLUDED.name, avatar=EXCLUDED.avatar,
				remark=EXCLUDED.remark, tags=EXCLUDED.tags, muted=EXCLUDED.muted,
				blocked=EXCLUDED.blocked, created=EXCLUDED.created,
				lastModified=EXCLUDED.lastModified, lastUpdated=EXCLUDED.lastUpdated,
				deleted=EXCLUDED.deleted, revision=EXCLUDED.revision, modified=EXCLUDED.modified
			""")
	int putContact(@BindBean Contact contact);

	@SqlBatch("""
			INSERT INTO contacts(id, type, auto, homePeerId, sessionKey, name, avatar, remark, tags, muted, blocked, created, lastModified, lastUpdated, deleted, revision, modified)
			VALUES(:id, :type, :auto, :homePeerId, :sessionKey, :name, :avatar, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated, :deleted, :revision, :modified)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, homePeerId=EXCLUDED.homePeerId,
				sessionKey=EXCLUDED.sessionKey, name=EXCLUDED.name, avatar=EXCLUDED.avatar,
				remark=EXCLUDED.remark, tags=EXCLUDED.tags, muted=EXCLUDED.muted,
				blocked=EXCLUDED.blocked, created=EXCLUDED.created,
				lastModified=EXCLUDED.lastModified, lastUpdated=EXCLUDED.lastUpdated,
				deleted=EXCLUDED.deleted, revision=EXCLUDED.revision, modified=EXCLUDED.modified
			""")
	int[] putContacts(@BindBean Collection<Contact> contacts);

	@SqlQuery("SELECT * FROM contacts WHERE id = ?")
	@RegisterRowMapper(ContactRowMapper.class)
	Contact getContact(Id id);

	@SqlQuery("SELECT * FROM contacts WHERE id IN (<ids>)")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getContacts(@BindList("ids") List<Id> ids);

	@SqlQuery("SELECT * FROM contacts")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getAllContacts();

	@SqlQuery("SELECT * FROM contacts WHERE auto = false AND deleted = false")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getAllUserContacts();

	@SqlQuery("SELECT * FROM contacts WHERE type = ?")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getAllContacts(int type);

	@SqlQuery("SELECT * FROM contacts WHERE auto = false AND modified = true")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getModifiedContacts();

	@SqlQuery("UPDATE contacts SET modified = :modified WHERE id IN (<ids>)")
	int updateContactsModifiedStatus(@BindList("ids") List<Id> ids, @Bind("modified") boolean modified);

	// Channels
	@SqlUpdate("""
			INSERT INTO contacts(id, type, auto, homePeerId, sessionKey, name, avatar, notice, owner, permission, remark, tags, muted, blocked, created, lastModified, lastUpdated, deleted, revision)
			VALUES(:id, :type, :auto, :homePeerId, :sessionKey, :name, :avatar, :notice, :owner, :permission, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated, :deleted, :revision)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, homePeerId=EXCLUDED.homePeerId,
				sessionKey=EXCLUDED.sessionKey, name=EXCLUDED.name, avatar=EXCLUDED.avatar,
				notice=EXCLUDED.notice, owner=EXCLUDED.owner, permission=EXCLUDED.permission,
				remark=EXCLUDED.remark, tags=EXCLUDED.tags, muted=EXCLUDED.muted,
				blocked=EXCLUDED.blocked, created=EXCLUDED.created,
				lastModified=EXCLUDED.lastModified, lastUpdated=EXCLUDED.lastUpdated,
				deleted=EXCLUDED.deleted, revision=EXCLUDED.revision
			""")
	@RegisterArgumentFactory(PermissionArgumentFactory.class)
	int putChannel(@BindBean Channel channel);

	@SqlBatch("""
			INSERT INTO contacts(id, type, auto, homePeerId, sessionKey, name, avatar, notice, owner, permission, remark, tags, muted, blocked, created, lastModified, lastUpdated, deleted, revision)
			VALUES(:id, :type, :auto, :homePeerId, :sessionKey, :name, :avatar, :notice, :owner, :permission, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated, :deleted, :revision)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, homePeerId=EXCLUDED.homePeerId,
				sessionKey=EXCLUDED.sessionKey, name=EXCLUDED.name, avatar=EXCLUDED.avatar,
				notice=EXCLUDED.notice, owner=EXCLUDED.owner, permission=EXCLUDED.permission,
				remark=EXCLUDED.remark, tags=EXCLUDED.tags, muted=EXCLUDED.muted,
				blocked=EXCLUDED.blocked, created=EXCLUDED.created,
				lastModified=EXCLUDED.lastModified, lastUpdated=EXCLUDED.lastUpdated,
				deleted=EXCLUDED.deleted, revision=EXCLUDED.revision
			""")
	@RegisterArgumentFactory(PermissionArgumentFactory.class)
	int[] putChannels(@BindBean Collection<Channel> channels);

	/*
	@SqlQuery("SELECT * FROM contacts WHERE type = 2 AND id = ?")
	@RegisterRowMapper(ChannelRowMapper.class)
	Channel getChannel(Id id);

	@SqlQuery("SELECT * FROM contacts WHERE type = 2")
	@RegisterRowMapper(ChannelRowMapper.class)
	List<Channel> getAllChannels();
	*/

	// Contacts and channels
	@SqlQuery("SELECT EXISTS (SELECT 1 FROM contacts WHERE id = ?)")
	boolean existsContact(Id id);

	@SqlUpdate("DELETE FROM contacts WHERE id = ?")
	int removeContact(Id id);

	@SqlUpdate("DELETE FROM contacts WHERE id IN (<ids>)")
	int removeContacts(@BindList("ids") Collection<Id> ids);

	@SqlUpdate("DELETE FROM contacts")
	int removeAllContacts();

	@SqlUpdate("UPDATE contacts SET deleted = true, modified = false WHERE auto = false")
	int removeAllUserContacts();

	// Channel members
	@SqlUpdate("""
			INSERT INTO channel_members VALUES(:channelId, :id, :role, :joined)
			ON CONFLICT(channelId, memberId) DO UPDATE SET
				role=EXCLUDED.role, joined=EXCLUDED.joined
			""")
	@RegisterArgumentFactory(RoleArgumentFactory.class)
	int putChannelMember(@Bind("channelId") Id channelId, @BindBean Channel.Member member);

	@SqlBatch("""
			INSERT INTO channel_members VALUES(:channelId, :id, :role, :joined)
			ON CONFLICT(channelId, memberId) DO UPDATE SET
				role=EXCLUDED.role, joined=EXCLUDED.joined
			""")
	@RegisterArgumentFactory(RoleArgumentFactory.class)
	int[] putChannelMembers(@Bind("channelId") Id channelId, @BindBean Collection<Channel.Member> members);

	@SqlQuery("""
			SELECT memberId, role, joined
			FROM channel_members WHERE channelId = ? AND memberId = ?
			""")
	@RegisterRowMapper(ChannelMemberRowMapper.class)
	Channel.Member getChannelMember(Id channelId, Id memberId);

	@SqlQuery("""
			SELECT memberId, role, joined
			FROM channel_members WHERE channelId = ?
			""")
	@RegisterRowMapper(ChannelMemberRowMapper.class)
	List<Channel.Member> getAllChannelMembers(Id channelId);

	@SqlQuery("SELECT EXISTS (SELECT 1 from channel_members WHERE channelId = ? AND memberId = ?)")
	boolean existsChannelMember(Id channelId, Id memberId);

	@SqlUpdate("DELETE from channel_members WHERE channelId = ? AND memberId = ?")
	int removeChannelMember(Id channelId, Id memberId);

	@SqlUpdate("DELETE from channel_members WHERE channelId = :channelId AND memberId IN (<ids>)")
	int removeChannelMembers(@Bind("channelId") Id channelId, @BindList("ids") Collection<Id> ids);

	@SqlUpdate("DELETE from channel_members WHERE channelId = ?")
	int removeAllChannelMembers(Id channelId);

	@SqlUpdate("UPDATE channel_members SET role = :role WHERE channelId = :channelId AND memberId = :memberId")
	@RegisterArgumentFactory(RoleArgumentFactory.class)
	int updateChannelMemberRole(@Bind("channelId") Id channelId, @BindList("memberId") Id memberId, @Bind("role") Channel.Role role);

	@SqlUpdate("UPDATE channel_members SET role = :role WHERE channelId = :channelId AND memberId IN (<ids>)")
	@RegisterArgumentFactory(RoleArgumentFactory.class)
	int updateChannelMembersRole(@Bind("channelId") Id channelId, @BindList("ids") Collection<Id> ids, @Bind("role") Channel.Role role);
}
