package io.bosonnetwork.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;

public class Profile {
	@JsonProperty("id")
	private Id id;
	@JsonProperty("homePeerId")
	@JsonAlias("p")
	private Id homePeerId;
	@JsonProperty("homePeerSig")
	@JsonAlias("ps")
	private byte[] homePeerSig;

	@JsonProperty("name")
	@JsonAlias("n")
	private String name;

	@JsonProperty("avatar")
	@JsonAlias("a")
	private boolean avatar;

	@JsonProperty("notice")
	@JsonAlias("nt")
	private String notice;

	@JsonProperty("sig")
	@JsonAlias("s")
	private byte[] sig;

	@JsonCreator
	protected Profile() {}

	public Profile(Id id, Id homePeerId, byte[] homePeerSig, String name, boolean avatar, byte[] sig) {
		this.id = id;
		this.homePeerId = homePeerId;
		this.homePeerSig = homePeerSig;
		this.name = name;
		this.avatar = avatar;
		this.sig = sig;
	}

	public Profile(Id id, Id homePeerId, byte[] homePeerSig, String name, boolean avatar, String notice, byte[] sig) {
		this.id = id;
		this.homePeerId = homePeerId;
		this.homePeerSig = homePeerSig;
		this.name = name;
		this.notice = notice;
		this.avatar = avatar;
		this.sig = sig;
	}

	protected void setId(Id id) {
		this.id = id;
	}

	@JsonGetter
	public Id getHomePeerId() {
		return homePeerId;
	}

	public byte[] getHomePeerSig() {
		return homePeerSig;
	}

	public Id getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getNotice() {
		return notice;
	}

	public boolean hasAvatar() {
		return avatar;
	}

	public byte[] getSig() {
		return sig;
	}

	public static byte[] digest(Id id, Id homePeerId, String name, boolean avatar, String notice) {
		MessageDigest md = Hash.sha256();
		md.update(id.bytes());
		md.update(homePeerId.bytes());
		// md.update(homePeerSig);
		if (name != null)
			md.update(name.getBytes(UTF_8));

		md.update(avatar ? (byte)1 : (byte)0);

		if (notice != null)
			md.update(notice.getBytes(UTF_8));

		return md.digest();
	}

	public boolean isGenuine() {
		if (id == null || homePeerId == null || homePeerSig == null || sig == null)
			return false;

		// Home peer signature: sign(id + homePeerId)
		// Proof: the recipient (id) is served by the home peer.
		MessageDigest md = Hash.sha256();
		md.update(id.bytes());
		md.update(homePeerId.bytes());
		if (!homePeerId.toSignatureKey().verify(md.digest(), homePeerSig))
			return false;

		// Signature: sign(id + homePeerId + homePeerSig + name? + avatar? + notice?)
		// Proof: the profile is provided by the recipient and is genuine
		md.reset();
		md.update(id.bytes());
		md.update(homePeerId.bytes());
		// md.update(homePeerSig);
		if (name != null)
			md.update(name.getBytes(UTF_8));

		md.update(avatar ? (byte)1 : (byte)0);

		if (notice != null)
			md.update(notice.getBytes(UTF_8));
		return id.toSignatureKey().verify(md.digest(), sig);
	}
}