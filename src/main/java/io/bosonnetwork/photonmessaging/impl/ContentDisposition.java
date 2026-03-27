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

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Objects;

public class ContentDisposition {
	private static final ContentDisposition INLINE = new ContentDisposition(Type.INLINE, null, null);

	private final Type type;
	private final String filename;
	private final String asciiFilename;
	private final String rfc5987Filename;

	public enum Type {
		INLINE("inline"), ATTACHMENT("attachment");

		private final String value;

		Type(String value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return value;
		}

		public static Type fromString(String value) {
			return switch (value) {
				case "inline" -> INLINE;
				case "attachment" -> ATTACHMENT;
				default -> throw new IllegalArgumentException("Invalid content disposition type: " + value);
			};
		}
	}

	public static ContentDisposition inline() {
		return INLINE;
	}

	public static ContentDisposition inline(String filename) {
		return filename == null || filename.isEmpty() ? INLINE :
				new ContentDisposition(Type.INLINE, toAsciiFallback(filename), encodeRFC5987("UTF-8", filename));
	}

	public static ContentDisposition attachment(String filename) {
		Objects.requireNonNull(filename, "filename");
		return new ContentDisposition(Type.ATTACHMENT, toAsciiFallback(filename), encodeRFC5987("UTF-8", filename));
	}

	private ContentDisposition(Type type, String asciiFilename, String rfc5987Filename) {
		this.type = type;
		this.asciiFilename = asciiFilename;
		this.rfc5987Filename = rfc5987Filename;

		String name;
		if (rfc5987Filename != null) {
			try {
				name = decodeRFC5987(rfc5987Filename);
			} catch (IllegalArgumentException e) {
				// fallback to ASCII filename if available
				if (asciiFilename != null)
					name = asciiFilename;
				else
					throw e;
			}
		} else {
			name = asciiFilename;
		}

		this.filename = name;
	}

	public Type getType() {
		return type;
	}

	public String getAsciiFilename() {
		return asciiFilename;
	}

	public String getRfc5987Filename() {
		return rfc5987Filename;
	}

	/**
	 * Returns the best filename (prefers filename* over filename)
	 */
	public String getFilename() {
		return filename;
	}

	public boolean isInline() {
		return type == Type.INLINE;
	}

	public boolean isAttachment() {
		return type == Type.ATTACHMENT;
	}

	public static ContentDisposition parse(String header) {
		Objects.requireNonNull(header, "header");

		// NOTE: This simple split does not handle semicolons inside quoted values (e.g., filename="a;b.txt").
		// This is an intentional tradeoff given controlled input constraints.
		String[] parts = header.split(";");
		Type type = Type.fromString(parts[0].trim().toLowerCase());
		String asciiFilename = null;
		String rfc5987Filename = null;
		for (int i = 1; i < parts.length; i++) {
			String part = parts[i].trim();
			int eq = part.indexOf('=');
			if (eq <= 0) continue;

			String name = part.substring(0, eq).trim().toLowerCase();
			String value = part.substring(eq + 1).trim();
			if (name.equals("filename"))
				asciiFilename = unquotes(value);
			else if (name.equals("filename*"))
				rfc5987Filename = value;

			// ignore other parameters
		}

		return new ContentDisposition(type, asciiFilename, rfc5987Filename);
	}

	private static String unquotes(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
			return value.substring(1, value.length() - 1);

		return value;
	}

	private static String toAsciiFallback(String filename) {
		String normalized = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFKD);
		return normalized.replaceAll("[^\\x20-\\x7E]", "_");
	}

	/**
	 * Decode RFC 5987 format: charset''urlencoded
	 * Example: UTF-8''file%20name.pdf
	 */
	private static String decodeRFC5987(String value) {
		int firstQuote = value.indexOf('\'');
		int secondQuote = value.indexOf('\'', firstQuote + 1);

		if (firstQuote <= 0 || secondQuote <= firstQuote)
			throw new IllegalArgumentException("Invalid RFC 5987 content disposition: " + value);

		String charset = value.substring(0, firstQuote);
		String encoded = value.substring(secondQuote + 1);

		try {
			return URLDecoder.decode(encoded, Charset.forName(charset));
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid RFC 5987 content disposition: " + value, e);
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static String encodeRFC5987(String charset, String filename) {
		try {
			String encoded = java.net.URLEncoder.encode(filename, charset)
					.replace("+", "%20")
					.replace("%7E", "~"); // optional normalization

			return charset + "''" + encoded;
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to encode filename: " + filename, e);
		}
	}

	public String getValue() {
		return type + (asciiFilename != null && !asciiFilename.isEmpty() ? "; filename=\"" + asciiFilename + "\"" : "") +
				(rfc5987Filename != null && !rfc5987Filename.isEmpty() ? "; filename*=" + rfc5987Filename : "");
	}

	@Override
	public String toString() {
		return "Content-Disposition: " + getValue();
	}
}