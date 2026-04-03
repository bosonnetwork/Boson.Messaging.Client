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
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.json.Json;

public class ContactMutation<T> {
	// current client revision, make sure the client makes the change based on the latest revision
	@JsonProperty(value = "r", required = true)
	private final int revision;
	@JsonProperty(value = "op", required = true)
	private final Op op;
	@JsonProperty(value = "d")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final T data;

	public enum Op {
		ADD(1),
		UPDATE(2),
		REMOVE(3),
		CLEAR(4);

		private final int value;

		Op(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		@JsonCreator
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
	protected ContactMutation(@JsonProperty(value = "r", required = true) int revision,
	                       @JsonProperty(value = "op", required = true) Op op,
	                       @JsonProperty(value = "d") T data) {
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

	public T getData() {
		return data;
	}

	public byte[] getDataAsBytes() {
		if (data == null)
			return null;

		try {
			return Json.cborMapper().writeValueAsBytes(data);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: ContactMutation data serialization", e);
		}
	}

	public static class GenericContactMutation extends ContactMutation<JsonNode> {
		@JsonCreator
		protected GenericContactMutation(int revision, Op op, JsonNode data) {
			super(revision, op, data);
		}

		public <DT> DT getDataAs(Class<DT> clazz) {
			final JsonNode data = getData();
			return data == null ? null : Json.cborMapper().convertValue(data, clazz);
		}

		public <E> List<E> getDataAsListOf(Class<E> elementType) {
			JavaType type = Json.cborMapper().getTypeFactory().constructCollectionType(List.class, elementType);
			final JsonNode data = getData();
			return data == null ? null : Json.cborMapper().convertValue(data, type);
		}
	}
}