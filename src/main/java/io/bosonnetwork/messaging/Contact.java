package io.bosonnetwork.messaging;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import io.bosonnetwork.Id;

public class Contact {
	public static final int PERSON_CONTACT = 0;
	public static final int GROUP_CONTACT = 1;

	@JsonProperty(value = "id", required = true)
	private Id id;

	@JsonProperty("t")
	private int type;

	@JsonProperty("n")
	@JsonInclude(Include.NON_EMPTY)
	private String name;

	@JsonProperty("r")
	@JsonInclude(Include.NON_EMPTY)
	private String remark;

	@JsonProperty("ts")
	@JsonInclude(Include.NON_EMPTY)
	private String tags;

	@JsonProperty("d")
	@JsonInclude(Include.NON_EMPTY)
	private byte[] data;

	@JsonProperty("b")
	@JsonInclude(Include.NON_EMPTY)
	private boolean blocked;

	@JsonProperty("m")
	private long lastModified;

	public Contact(Id id, int type, String name, String remark, String tags, byte[] data, boolean blocked, long lastModified) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.remark = remark;
		this.tags = tags;
		this.data = data;
		this.blocked = blocked;
		this.lastModified = lastModified;
	}

	public Id getId() {
		return id;
	}

	public int getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	private void touch() {
		this.lastModified = System.currentTimeMillis();
	}

	public void setName(String name) {
		this.name = name;
		touch();
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
		touch();
	}

	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags;
		touch();
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
		touch();
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
		touch();
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof Contact) {
			Contact that = (Contact)o;

			return this.id.equals(that.id) &&
					this.type == that.type &&
					Objects.equal(this.name, that.name) &&
					Objects.equal(this.remark, that.remark) &&
					Objects.equal(this.tags, that.tags) &&
					Arrays.equals(this.data, that.data) &&
					this.blocked == that.blocked &&
					this.lastModified == that.lastModified;
		}

		return false;
	}
}
