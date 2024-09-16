package io.bosonnetwork.messaging;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import io.bosonnetwork.Id;

public class MessagingPeerInfo {
	private Id peerId;
	private Id nodeId;
	private URL apiURL;

	private MessagingPeerInfo(Id peerId, Id nodeId, URL apiURL) {
		this.peerId = peerId;
		this.nodeId = nodeId;
		this.apiURL = apiURL;
	}

	public static MessagingPeerInfo of(Id peerId, Id nodeId, URL apiURL) {
		if (peerId == null && apiURL == null)
			throw new IllegalArgumentException("Missing peer id or apiURL");

		return new MessagingPeerInfo(peerId, nodeId, apiURL);
	}

	public static MessagingPeerInfo of(Id peerId, Id nodeId, String apiURL) {
		if (peerId == null && apiURL == null)
			throw new IllegalArgumentException("Missing peer id or apiURL");

		try {
			return new MessagingPeerInfo(peerId, nodeId, apiURL != null ? new URL(apiURL) : null);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("apiURL", e);
		}
	}

	public static MessagingPeerInfo of(Id peerId, URL apiURL) {
		return of(peerId, null, apiURL);
	}

	public static MessagingPeerInfo of(Id peerId, String apiURL) {
		return of(peerId, null, apiURL);
	}

	public static MessagingPeerInfo of(Id peerId) {
		Objects.requireNonNull(peerId);
		return of(peerId, null, (URL)null);
	}

	public static MessagingPeerInfo of(URL apiURL) {
		Objects.requireNonNull(apiURL);
		return of(null, null, apiURL);
	}

	public static MessagingPeerInfo of(String apiURL) {
		Objects.requireNonNull(apiURL);
		return of(null, null, apiURL);
	}

	public Id getPeerId() {
		return peerId;
	}

	public Id getNodeId() {
		return nodeId;
	}

	public URL getApiURL() {
		return apiURL;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o != null && o instanceof MessagingPeerInfo that) {
			return Objects.equals(peerId, that.peerId) &&
					Objects.equals(nodeId, that.nodeId) &&
					Objects.equals(apiURL, that.apiURL);
		}

		return false;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(256);
		String sep = "";

		repr.append("MessagingPeerInfo[");
		if (peerId != null) {
			repr.append("peerId=").append(peerId);
			sep = ", ";
		}
		if (nodeId != null) {
			repr.append(sep).append("nodeId=").append(nodeId);
			sep = ", ";
		} if (apiURL != null) {
			repr.append(sep).append("apiURL=").append(apiURL);
		}
		repr.append(']');

		return repr.toString();
	}
}
