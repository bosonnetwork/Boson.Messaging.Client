package io.bosonnetwork.messaging.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;
import io.bosonnetwork.photonmessaging.impl.ContactBuilder;

@JsonPOJOBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelBuilder extends ContactBuilder {
	public ChannelBuilder() {
		super(AbstractContact.Types.CHANNEL);
	}

	@Override
	public Channel build() {
		return (Channel)super.build();
	}
}