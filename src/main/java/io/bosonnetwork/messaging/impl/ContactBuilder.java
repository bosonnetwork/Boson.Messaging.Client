package io.bosonnetwork.messaging.impl;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Contact;

@JsonPOJOBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactBuilder {
	private Id id;
	private Id homePeerId;
	private int type;

	private String remark;
	private String tags;
	private boolean muted;
	private boolean blocked;
	private long created;
	private long lastModified;

	private String name;
	private boolean avatar;
	private String notice;
	private byte[] privateKey;
	private Id owner;
	private Channel.Permission permission;

	public ContactBuilder() {}

	@JsonProperty("id")
	public ContactBuilder withId(Id id) {
		Objects.requireNonNull(id, "id");
		this.id = id;
		return this;
	}

	@JsonProperty("p")
	public ContactBuilder withHomePeerId(Id peerId) {
		this.homePeerId = peerId;
		return this;
	}

	@JsonProperty("t")
	public ContactBuilder withType(int type) {
		if (type != Contact.Types.CONTACT && type != Contact.Types.CHANNEL)
			throw new IllegalArgumentException("type");
		this.type = type;
		return this;
	}

	@JsonProperty("n")
	public ContactBuilder withName(String name) {
		this.name = name == null || name.isEmpty() ? null : name;
		return this;
	}

	@JsonProperty("a")
	public ContactBuilder withAvatar(boolean avatar) {
		this.avatar = avatar;
		return this;
	}

	@JsonProperty("nt")
	public ContactBuilder withNotice(String notice) {
		this.notice = notice == null || notice.isEmpty() ? null : notice;
		return null;
	}

	@JsonProperty("k")
	public ContactBuilder withPrivateKey(byte[] privateKey) {
		Objects.requireNonNull(privateKey, "privateKey");
		// TODO: check the key is valid
		this.privateKey = privateKey;
		return null;
	}

	@JsonProperty("o")
	public ContactBuilder withOwner(Id owner) {
		Objects.requireNonNull(owner, "owner");
		this.owner = owner;
		return this;
	}

	@JsonProperty("pm")
	public ContactBuilder withPermission(Channel.Permission permission) {
		Objects.requireNonNull(permission, "permission");
		this.permission = permission;
		return this;
	}

	@JsonProperty("r")
	public ContactBuilder withRemark(String remark) {
		this.remark = remark == null || remark.isEmpty() ? null : remark;
		return this;
	}

	@JsonProperty("ts")
	public ContactBuilder withTags(String tags) {
		this.tags = tags == null || tags.isEmpty() ? null : tags;
		return this;
	}

	@JsonProperty("d")
	public ContactBuilder withMuted(boolean muted) {
		this.muted = muted;
		return this;
	}

	@JsonProperty("b")
	public ContactBuilder withBlocked(boolean blocked) {
		this.blocked = blocked;
		return this;
	}

	@JsonProperty("c")
	public ContactBuilder withCreated(long created) {
		this.created = created;
		return this;
	}

	@JsonProperty("m")
	public ContactBuilder withLastModified(long lastModified) {
		this.lastModified = lastModified;
		return this;
	}

	public Contact build() {
		if (id == null)
			throw new IllegalStateException("Missing id");

		if (type == Contact.Types.CONTACT)
			return new ContactImpl(id, homePeerId, false, name, avatar, remark, tags, muted, blocked, created, lastModified, -1);
		else if (type == Contact.Types.CHANNEL)
			return new ChannelImpl(id, homePeerId, false, name, avatar, notice, privateKey, owner, permission, remark, tags, muted, created, lastModified, -1);
		else
			throw new IllegalStateException("Unknown contact type: " + type);
	}
}
