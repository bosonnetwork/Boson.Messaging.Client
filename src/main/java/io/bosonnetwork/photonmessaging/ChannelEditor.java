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
 * A builder for creating or updating {@link Channel} instances.
 * Extends {@link ContactEditor} to provide channel-specific configuration.
 */
public interface ChannelEditor {
	/**
	 * Sets the join permission policy for the channel.
	 *
	 * @param permission the join permission level
	 * @return this builder instance
	 */
	ChannelEditor setPermission(Channel.Permission permission);

	/**
	 * Sets the name of the channel.
	 *
	 * @param name the channel name
	 * @return this builder instance
	 */
	ChannelEditor setName(String name);

	/**
	 * Sets the channel notice or description.
	 *
	 * @param notice the channel notice
	 * @return this builder instance
	 */
	ChannelEditor setNotice(String notice);

	/**
	 * Sets whether the channel is announced to the network.
	 *
	 * @param announce true if the channel should be announced, false otherwise
	 * @return this builder instance
	 */
	ChannelEditor setAnnounce(boolean announce);

	/**
	 * Builds and returns the {@link Channel} instance.
	 *
	 * @return the constructed Channel
	 */
	Channel build();
}