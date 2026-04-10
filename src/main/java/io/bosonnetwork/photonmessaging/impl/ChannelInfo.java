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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;

public record ChannelInfo(@JsonProperty(value = "id", required = true) Id channelId,
                          @JsonProperty(value = "o", required = true) Id ownerId,
                          @JsonProperty(value = "sid", required = true) Id sessionId,
                          @JsonProperty(value = "sk") @JsonInclude(JsonInclude.Include.NON_EMPTY) byte[] sessionKey,
                          @JsonProperty(value = "p", required = true) Channel.Permission permission,
                          @JsonProperty(value = "n") @JsonInclude(JsonInclude.Include.NON_NULL) String name,
                          @JsonProperty(value = "nt") @JsonInclude(JsonInclude.Include.NON_EMPTY) String notice,
                          @JsonProperty(value = "a") @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean announce,
                          @JsonProperty(value = "c") long createdAt,
                          @JsonProperty(value = "u") long updateAt,
                          @JsonProperty(value = "m") @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Channel.Member> members) {
}