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

package io.bosonnetwork.photonmessaging;

/**
 * The ContentType class provides constants for commonly used content types
 * as well as the standard "Content-Type" header name. These constants can
 * be used to specify the type of data being sent or received in the message.
 * <p>
 * This class includes content type constants for text, JSON, CBOR, images,
 * audio, video, and binary data.
 * <p>
 * It is a utility class and is not meant to be instantiated.
 */
public class ContentType {
	/**
	 * The standard header name for the content type.
	 */
	public static final String HEADER_NAME = "Content-Type";

	/**
	 * Content type for plain text.
	 */
	public static final String TEXT = "text/plain";

	/**
	 * Content type for JSON data.
	 */
	public static final String JSON = "application/json";

	/**
	 * Content type for CBOR encoded data.
	 */
	public static final String CBOR = "application/cbor";

	/**
	 * Content type for JPEG images.
	 */
	public static final String IMAGE_JPEG = "image/jpeg";

	/**
	 * Content type for PNG images.
	 */
	public static final String IMAGE_PNG = "image/png";

	/**
	 * Content type for WebP images.
	 */
	public static final String IMAGE_WEBP = "image/webp";

	/**
	 * Content type for AAC audio.
	 */
	public static final String AUDIO_AAC = "audio/aac";

	/**
	 * Content type for MP3 audio.
	 */
	public static final String AUDIO_MP3 = "audio/mpeg";

	/**
	 * Content type for WebM audio.
	 */
	public static final String AUDIO_WEBM = "audio/webm";

	/**
	 * Content type for MP4 video.
	 */
	public static final String VIDEO_MP4 = "video/mp4";

	/**
	 * Content type for WebM video.
	 */
	public static final String VIDEO_WEBM = "video/webm";

	/**
	 * Content type for generic binary data.
	 */
	public static final String BINARY = "application/octet-stream";

	private ContentType() {}
}