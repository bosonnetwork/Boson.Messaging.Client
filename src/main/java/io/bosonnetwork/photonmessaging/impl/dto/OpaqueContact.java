/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;

/**
 * Represents a contact entry within the Photon messaging contact synchronization service.
 * <p>
 * The contact data is opaque to the synchronization service; it is created and interpreted
 * exclusively by the messaging client. The service provides the mechanism to synchronize
 * this data across a user's various devices.
 * <p>
 * A plain class rather than a record: Jackson's record support calls
 * {@code Class.getRecordComponents()}, which the Android runtime does not implement, so a
 * record wire type fails to deserialize on-device. Accessors keep the record-style names so
 * call sites are unaffected.
 */
public final class OpaqueContact {
	@JsonProperty(value = "id", required = true)
	private final Id id;
	@JsonProperty(value = "v", required = true)
	private final int revision;
	@JsonProperty(value = "d", required = true)
	private final byte[] data;

	@JsonCreator
	public OpaqueContact(@JsonProperty(value = "id", required = true) Id id,
			@JsonProperty(value = "v", required = true) int revision,
			@JsonProperty(value = "d", required = true) byte[] data) {
		this.id = id;
		this.revision = revision;
		this.data = data;
	}

	public Id id() {
		return id;
	}

	public int revision() {
		return revision;
	}

	public byte[] data() {
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof OpaqueContact that))
			return false;
		return revision == that.revision && Objects.equals(id, that.id) && Objects.equals(data, that.data);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, revision, data);
	}

	@Override
	public String toString() {
		return "OpaqueContact[id=" + id + ", revision=" + revision + ", data=" + data + "]";
	}
}
