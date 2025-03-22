package io.bosonnetwork.messaging.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Contact;

@JsonPOJOBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelBuilder extends ContactBuilder {
	public ChannelBuilder() {
		super(Contact.Types.CHANNEL);
	}

	@Override
	public Channel build() {
		return (Channel)super.build();
	}
}
