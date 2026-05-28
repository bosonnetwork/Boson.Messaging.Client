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

package io.bosonnetwork.photonmessaging.impl;

import java.util.Arrays;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.utils.Bytes;

/**
 * Represents an interface for objects that originate from a specific device.
 * This interface provides methods to work with and validate unique identifiers
 * that are associated with device-originated objects, as well as retrieve
 * relevant metadata such as timestamps.
 */
public interface DeviceOriginated {
	/**
	 * Retrieves the unique identifier originated with the current object.
	 *
	 * @return the unique {@code Id} of this object.
	 */
	Id getId();

	/**
	 * Retrieves the timestamp associated with the current object.
	 *
	 * @return the timestamp as a long value represented in milliseconds since the epoch.
	 */
	long getTimestamp();

	/**
	 * Determines whether the current object's unique identifier is originated from the specified device ID.
	 *
	 * @param deviceId the unique identifier of the device to compare against the object's originated ID
	 * @return {@code true} if the object's ID matches the hash generated from the provided device ID and its own timestamp; {@code false} otherwise
	 */
	default boolean isOriginated(Id deviceId) {
		byte[] seedBytes = Bytes.fromLong(getTimestamp());
		byte[] hash = Hash.sha256(deviceId.bytes(), seedBytes);
		return Arrays.equals(getId().bytes(), hash);
	}

	/**
	 * Generates a unique identifier based on the given device ID and timestamp.
	 *
	 * @param deviceId the unique identifier of the device used as a seed for the ID generation
	 * @param timestamp the timestamp in milliseconds since the epoch used as a seed for the ID generation
	 * @return a newly generated {@code Id} based on the provided device ID and timestamp
	 */
	static Id generateId(Id deviceId, long timestamp) {
		byte[] seedBytes = Bytes.fromLong(timestamp);
		return Id.of(Hash.sha256(deviceId.bytes(), seedBytes));
	}
}