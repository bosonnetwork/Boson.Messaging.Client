package io.bosonnetwork.messaging;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.impl.ContactBuilder;

@JsonDeserialize(builder = ContactBuilder.class)
public abstract class Contact implements Comparable<Contact> {
	private static final long STALE_TIME = TimeUnit.HOURS.toMillis(6);

	private Id id;
	private Id homePeerId;

	private boolean auto;

	private String name;
	private boolean avatar;

	private String remark;
	private String tags;
	private boolean muted;
	private boolean blocked;
	private long created;
	private long lastModified;

	private long lastUpdated;

	private transient String displayName;

	public static class Types {
		public static final int UNKNOWN = 0;
		public static final int CONTACT = 1;
		public static final int CHANNEL = 2;
	}

	protected Contact(Id id, Id homePeerId, boolean auto,  String name, boolean avatar,
			String remark, String tags, boolean muted, boolean blocked,
			long created, long lastModified,  long lastUpdated) {
		this.id = id;
		this.homePeerId = homePeerId;

		this.auto = auto;

		this.name = name;
		this.avatar = avatar;

		this.remark = remark;
		this.tags = tags;
		this.muted = muted;
		this.blocked = blocked;
		this.created = created;
		this.lastModified = lastModified;

		this.lastUpdated = lastUpdated;
	}

	protected Contact(Id id, Id homePeerId, boolean auto) {
		this.id = id;
		this.homePeerId = homePeerId;
		this.auto = auto;
		this.created = System.currentTimeMillis();
		this.lastModified = this.created;
		this.lastUpdated = -1;
	}

	@JsonProperty("id")
	public Id getId() {
		return id;
	}

	@JsonProperty("p")
	public Id getHomePeerId() {
		return homePeerId;
	}

	@JsonProperty("t")
	public abstract int getType();

	@JsonProperty("k")
	@JsonInclude(Include.NON_EMPTY)
	protected byte[] getPrivateKey() {
		return null;
	}

	// @JsonProperty("n")
	// @JsonInclude(Include.NON_EMPTY)
	public String getName() {
		return name;
	}

	// @JsonProperty("a")
	// @JsonInclude(Include.NON_EMPTY)
	public boolean hasAvatar() {
		return avatar;
	}

	public String getAvatar() {
		return avatar ? "bmr://" + homePeerId.toBase58String() + "/" + id.toBase58String() : null;
	}

	@JsonProperty("r")
	@JsonInclude(Include.NON_EMPTY)
	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark != null && !remark.isEmpty() ? remark : null;
		displayName = null;
		touch();
	}

	@JsonProperty("ts")
	@JsonInclude(Include.NON_EMPTY)
	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags != null && !tags.isEmpty() ? tags : null;
		touch();
	}

	@JsonProperty("d")
	@JsonInclude(Include.NON_DEFAULT)
	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
		touch();
	}

	@JsonProperty("b")
	@JsonInclude(Include.NON_DEFAULT)
	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
		touch();
	}

	@JsonProperty("c")
	@JsonInclude(Include.NON_EMPTY)
	public long getCreated() {
		return created;
	}

	@JsonProperty("m")
	@JsonInclude(Include.NON_EMPTY)
	public long getLastModified() {
		return lastModified;
	}

	public boolean isAuto() {
		return auto;
	}

	protected void setAuto(boolean auto) {
		this.auto = auto;
	}

	protected void touch() {
		this.lastModified = System.currentTimeMillis();
		this.auto = false;
	}

	protected void updated() {
		this.lastUpdated = System.currentTimeMillis();
	}

	public long getLastUpdated() {
		return lastUpdated;
	}

	public String getDisplayName() {
		if (displayName == null) {
			if (remark != null && !remark.isEmpty())
				displayName = remark;
			else if (name != null && !name.isEmpty())
				displayName = name;
			else
				displayName = id.toAbbrString();
		}

		return displayName;
	}

	// update the contact or channel information
	public void update(Profile profile) {
		Objects.requireNonNull(profile);
		if (!profile.getId().equals(id))
			throw new IllegalArgumentException("profile does not match the contact");
		if (!profile.isGenuine())
			throw new IllegalArgumentException("profile is not genuine");

		this.homePeerId = profile.getHomePeerId();
		this.name = profile.getName();
		this.avatar = profile.hasAvatar();
		this.displayName = null;

		updated();
	}

	public void update(Contact contact) {
		Objects.requireNonNull(contact);
		if (!contact.getId().equals(id))
			throw new IllegalArgumentException("contact does not matched");

		this.homePeerId = contact.homePeerId;
		this.remark = contact.remark;
		this.tags = contact.tags;
		this.muted = contact.muted;
		this.blocked = contact.blocked;
		this.created = contact.created;
		this.lastModified = contact.lastModified;

		this.name = contact.name;
		this.avatar = contact.avatar;

		this.displayName = null;

		updated();
	}

	public boolean isStaled() {
		return System.currentTimeMillis() - lastUpdated > STALE_TIME;
	}

	public boolean is(Contact contact) {
		if (contact == this)
			return true;

		return Objects.equals(this.id, contact.id);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof Contact that) {
			return Objects.equals(this.id, that.id) &&
					this.getType() == that.getType() &&
					Arrays.equals(this.getPrivateKey(), that.getPrivateKey()) &&
					Objects.equals(this.remark, that.remark) &&
					Objects.equals(this.tags, that.tags) &&
					this.muted == that.muted &&
					this.blocked == that.blocked &&
					this.created == that.created &&
					this.lastModified == that.lastModified;
		}

		return false;
	}

	@Override
	public int compareTo(Contact entry) {
		String n1 = getDisplayName();
		String n2 = entry.getDisplayName();

		int rc = n1.compareTo(n2);
		if (rc != 0)
			return rc;

		return id.compareTo(entry.id);
	}
}
