package io.bosonnetwork.messaging.impl;

import java.util.Objects;

import io.bosonnetwork.Id;

public class IdPath {
	private Id from;
	private Id to;

	public IdPath(Id from, Id to) {
		this.from = from;
		this.to = to;
	}

	public static IdPath of(Id from, Id to) {
		return new IdPath(from, to);
	}

	public Id from() {
		return from;
	}

	public Id to() {
		return to;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof IdPath that) {
			return Objects.equals(from, that.from) &&
					Objects.equals(to, that.to);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash("idpath", from, to);
	}

	@Override
	public String toString() {
		return "IdPath{" + from + "->'" + to + '}';
	}
}
