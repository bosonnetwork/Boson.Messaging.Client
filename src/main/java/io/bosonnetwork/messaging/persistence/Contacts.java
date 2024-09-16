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
	// Contacts
	@SqlUpdate("""
			INSERT INTO contacts(id, type, auto, homePeerId, name, avatar, remark, tags, muted, blocked, created, lastModified, lastUpdated)
			VALUES(:id, :type, :auto, :homePeerId, :name, :avatar, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, homePeerId=EXCLUDED.homePeerId,
				name=EXCLUDED.name, avatar=EXCLUDED.avatar, remark=EXCLUDED.remark,
				tags=EXCLUDED.tags, muted=EXCLUDED.muted, blocked=EXCLUDED.blocked,
				created=EXCLUDED.created, lastModified=EXCLUDED.lastModified,
				lastUpdated=EXCLUDED.lastUpdated
			""")
	int putContact(@BindBean Contact contact);

	@SqlBatch("""
			INSERT INTO contacts(id, type, auto, homePeerId, name, avatar, remark, tags, muted, blocked, created, lastModified, lastUpdated)
			VALUES(:id, :type, :auto, :homePeerId, :name, :avatar, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, homePeerId=EXCLUDED.homePeerId,
				name=EXCLUDED.name, avatar=EXCLUDED.avatar, remark=EXCLUDED.remark,
				tags=EXCLUDED.tags, muted=EXCLUDED.muted, blocked=EXCLUDED.blocked,
				created=EXCLUDED.created, lastModified=EXCLUDED.lastModified,
				lastUpdated=EXCLUDED.lastUpdated
			""")
	int[] putContacts(@BindBean Collection<Contact> contacts);

	@SqlQuery("SELECT * FROM contacts WHERE id = ?")
	@RegisterRowMapper(ContactRowMapper.class)
	Contact getContact(Id id);

	@SqlQuery("SELECT * FROM contacts")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getAllContacts();

	@SqlQuery("SELECT * FROM contacts WHERE type = ?")
	@RegisterRowMapper(ContactRowMapper.class)
	List<Contact> getAllContacts(int type);

	// Channels
	@SqlUpdate("""
			INSERT INTO contacts(id, type, auto, privateKey, name, avatar, notice, owner, permission, remark, tags, muted, blocked, created, lastModified, lastUpdated)
			VALUES(:id, :type, :auto, :privateKey, :name, :avatar, :notice, :owner, :permission, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, privateKey=EXCLUDED.privateKey,
				name=EXCLUDED.name, avatar=EXCLUDED.avatar, notice=EXCLUDED.notice,
				owner=EXCLUDED.owner, permission=EXCLUDED.permission,
				remark=EXCLUDED.remark, tags=EXCLUDED.tags, muted=EXCLUDED.muted,
				blocked=EXCLUDED.blocked, created=EXCLUDED.created,
				lastModified=EXCLUDED.lastModified, lastUpdated=EXCLUDED.lastUpdated
			""")
	@RegisterArgumentFactory(PermissionArgumentFactory.class)
	int putChannel(@BindBean Channel group);

	@SqlBatch("""
			INSERT INTO contacts(id, type, auto, privateKey, name, avatar, notice, owner, permission, remark, tags, muted, blocked, created, lastModified, lastUpdated)
			VALUES(:id, :type, :auto, :privateKey, :name, :avatar, :notice, :owner, :permission, :remark, :tags, :muted, :blocked, :created, :lastModified, :lastUpdated)
			ON CONFLICT(id) DO UPDATE SET
				type=EXCLUDED.type, auto=EXCLUDED.auto, privateKey=EXCLUDED.privateKey,
				name=EXCLUDED.name, avatar=EXCLUDED.avatar, notice=EXCLUDED.notice,
				owner=EXCLUDED.owner, permission=EXCLUDED.permission,
				remark=EXCLUDED.remark, tags=EXCLUDED.tags, muted=EXCLUDED.muted,
				blocked=EXCLUDED.blocked, created=EXCLUDED.created,
				lastModified=EXCLUDED.lastModified, lastUpdated=EXCLUDED.lastUpdated
			""")
	@RegisterArgumentFactory(PermissionArgumentFactory.class)
	int[] putChannels(@BindBean Collection<Channel> groups);

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
