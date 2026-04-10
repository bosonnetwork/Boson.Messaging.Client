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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Contact;

/**
 * Represents a compact contact mutation operation used in the messaging system.
 *
 * <p>
 * A ContactMutation is a binary-encoded command describing a change to the contact state.
 * It is designed for efficient CBOR serialization and cross-language compatibility with Rust.
 *
 * <p>
 * The structure is an event-like envelope consisting of:
 * <ul>
 *   <li>revision number (r) - ensures operations are applied against the correct state version</li>
 *   <li>operation type (op) - determines the mutation kind</li>
 *   <li>operation data (d) - polymorphic payload dependent on op</li>
 * </ul>
 *
 * <h2>Wire Format</h2>
 *
 * CBOR-equivalent JSON representation:
 *
 * <pre>
 * {
 *   "r": 12,
 *   "op": "a",
 *   "d": { ... }
 * }
 * </pre>
 *
 * Special case:
 * <pre>
 * {
 *   "r": 12,
 *   "op": "c"
 * }
 * </pre>
 * (CLEAR operation has no payload)
 *
 * <h2>Operation Mapping</h2>
 *
 * <pre>
 * a → ADD    (PhotonContact)
 * u → UPDATE (JsonNode / partial update)
 * r → REMOVE (List&lt;Id&gt;)
 * c → CLEAR  (no payload)
 * </pre>
 *
 * <h2>Rust Equivalent</h2>
 *
 * <pre>
 * use serde::{Serialize, Deserialize};
 *
 * #[derive(Debug, Serialize, Deserialize, PartialEq, Eq, Clone, Copy)]
 * #[serde(rename_all = "lowercase")]
 * pub enum Op {
 *     #[serde(rename = "a")]
 *     Add = 1,
 *     #[serde(rename = "u")]
 *     Update = 2,
 *     #[serde(rename = "r")]
 *     Remove = 3,
 *     #[serde(rename = "c")]
 *     Clear = 4,
 * }
 *
 * #[derive(Debug, Serialize, Deserialize)]
 * #[serde(tag = "op", content = "d")]
 * pub enum MutationData {
 *     #[serde(rename = "a")]
 *     Add(PhotonContact),
 *     #[serde(rename = "u")]
 *     Update(serde_json::Value),
 *     #[serde(rename = "r")]
 *     Remove(Vec<Id>),
 *     #[serde(rename = "c")]
 *     Clear,
 * }
 *
 * #[derive(Debug, Serialize, Deserialize)]
 * pub struct ContactMutation {
 *     #[serde(rename = "r")]
 *     pub revision: i32,
 *     #[serde(flatten)]
 *     pub data: MutationData,
 * }
 * </pre>
 *
 * <h2>Design Notes</h2>
 *
 * <ul>
 *   <li>Uses compact string tags ("a", "u", "r", "c") optimized for CBOR encoding</li>
 *   <li>Operation type is the single source of truth for payload interpretation</li>
 *   <li>Rust and Java share identical wire format (no translation layer required)</li>
 *   <li>Clear operation intentionally omits payload to minimize encoding size</li>
 *   <li>Jackson uses EXTERNAL_PROPERTY mapping between op → d</li>
 * </ul>
 */
public class ContactMutation {
	// current client revision, make sure the client makes the change based on the latest revision
	@JsonProperty(value = "r", required = true)
	private final int revision;
	@JsonProperty(value = "op", required = true)
	private final Op op;
	@JsonProperty(value = "d")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonTypeInfo(
			use = JsonTypeInfo.Id.NAME,
			include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
			property = "op",
			defaultImpl = Void.class
	)
	@JsonSubTypes({
			@JsonSubTypes.Type(value = PhotonContact.class, name = "a"),
			@JsonSubTypes.Type(value = JsonNode.class, name = "u"),
			@JsonSubTypes.Type(value = IdList.class, name = "r"),
			@JsonSubTypes.Type(value = Void.class, name = "c")
	})
	private final Object data;

	public enum Op {
		@JsonProperty("a")
		ADD,
		@JsonProperty("u")
		UPDATE,
		@JsonProperty("r")
		REMOVE,
		@JsonProperty("c")
		CLEAR;

		public int value() {
			return this.ordinal() + 1;
		}

		public static Op valueOf(int value) {
			return switch (value) {
				case 1 -> ADD;
				case 2 -> UPDATE;
				case 3 -> REMOVE;
				case 4 -> CLEAR;
				default -> throw new IllegalArgumentException("Invalid operation: " + value);
			};
		}
	}

	@JsonCreator
	private ContactMutation(@JsonProperty(value = "r", required = true) int revision,
	                       @JsonProperty(value = "op", required = true) Op op,
	                       @JsonProperty(value = "d") Object data) {
		this.revision = revision;
		this.op = op;
		this.data = data;
	}

	public int getRevision() {
		return revision;
	}

	public Op getOp() {
		return op;
	}

	@SuppressWarnings("unchecked")
	public <T> T getData() {
		return (T) data;
	}

	public static ContactMutation add(int revision, Contact contact) {
		return new ContactMutation(revision, Op.ADD, contact);
	}

	public static ContactMutation update(int revision, JsonNode data) {
		return new ContactMutation(revision, Op.UPDATE, data);
	}

	public static ContactMutation remove(int revision, List<Id> contactIds) {
		return new ContactMutation(revision, Op.REMOVE, contactIds);
	}

	public static ContactMutation clear(int revision) {
		return new ContactMutation(revision, Op.CLEAR, null);
	}
}