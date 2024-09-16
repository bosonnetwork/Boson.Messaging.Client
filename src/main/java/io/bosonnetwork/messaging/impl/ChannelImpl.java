package io.bosonnetwork.messaging.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.messaging.Channel;

public class ChannelImpl extends Channel {
	private Signature.KeyPair keyPair; // member keypair
	private Id memberPublicKey;
	private CryptoBox.KeyPair encryptionKeyPair;

	private HashMap<Id, Member> members;
	private Callable<List<Member>> memberLoader;

	public ChannelImpl(Id id, Id homePeerId, boolean auto, String name, boolean avatar, String notice,
			byte[] privateKey, Id owner, Permission permission, String remark, String tags,
			boolean muted,long created, long lastModified, long lastUpdated) {
		super(id, homePeerId, auto, name, avatar, notice, privateKey, owner, permission,
				remark, tags, muted, created, lastModified, lastUpdated);
	}

	public ChannelImpl(Id id, Id homePeerId, boolean auto, byte[] privateKey,
			String remark, String tags, boolean muted, long created, long lastModified) {
		this(id, homePeerId, auto, null, false, null, privateKey, null, null,
				remark, tags, muted, created, lastModified, -1);
	}

	public ChannelImpl(Id id, Id homePeerId, boolean auto, String name, boolean avatar, String notice,
			byte[] privateKey, Id owner, Permission permission, long created, long lastModified) {
		this(id, homePeerId, auto, name, avatar, notice, privateKey, owner, permission,
				null, null, false, created, lastModified, System.currentTimeMillis());
	}

	public ChannelImpl(Id id, Id homePeerId, byte[] privateKey, boolean auto) {
		super(id, homePeerId, privateKey, auto);
	}

	public static Channel auto(Id id, Id homePeerId) {
		return new ChannelImpl(id, homePeerId, null, true);
	}

	public static Channel auto(Id id) {
		return new ChannelImpl(id, null, null, true);
	}

	protected void setMembers(Callable<List<Member>> memberLoader) {
		this.memberLoader = memberLoader;
	}

	@Override
	public int getType() {
		return Types.CHANNEL;
	}

	@Override
	protected void setMemberPrivateKey(byte[] privateKey) {
		this.keyPair = Signature.KeyPair.fromPrivateKey(privateKey);
		this.encryptionKeyPair = CryptoBox.KeyPair.fromSignatureKeyPair(keyPair);
		this.memberPublicKey = Id.of(keyPair.publicKey().bytes());
	}

	@Override
	public byte[] getPrivateKey() {
		return keyPair.privateKey().bytes(); // to be encrypt
	}

	@Override
	public Id getMemberPublicKey() {
		return memberPublicKey;
	}

	protected Signature.KeyPair getMemberKeyPair() {
		return keyPair;
	}

	@Override
	public byte[] sign(byte[] data) {
		return Signature.sign(data, keyPair.privateKey());
	}

	@Override
	public boolean verify(byte[] data, byte[] signature) {
		return Signature.verify(data, signature, keyPair.publicKey());
	}

	// one-shot encryption
	@Override
	public byte[] encrypt(Id recipient, byte[] data) {
		// TODO: how to avoid the memory copy?!
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();
		CryptoBox.PublicKey pk = recipient.toEncryptionKey();
		CryptoBox.PrivateKey sk = encryptionKeyPair.privateKey();
		byte[] cipher = CryptoBox.encrypt(data, pk, sk, nonce);

		byte[] buf = new byte[CryptoBox.Nonce.BYTES + cipher.length];
		System.arraycopy(nonce.bytes(), 0, buf, 0, CryptoBox.Nonce.BYTES);
		System.arraycopy(cipher, 0, buf,CryptoBox. Nonce.BYTES, cipher.length);
		return buf;
	}

	// one-shot decryption
	@Override
	public byte[] decrypt(Id sender, byte[] data) throws CryptoException {
		if (data.length <= CryptoBox.Nonce.BYTES + CryptoBox.MAC_BYTES)
			throw new CryptoException("Invalid cipher size");

		// TODO: how to avoid the memory copy?!
		byte[] n = Arrays.copyOfRange(data, 0, CryptoBox.Nonce.BYTES);
		CryptoBox.Nonce nonce = CryptoBox.Nonce.fromBytes(n);

		//if (lastPeerNonce != null && nonce.equals(lastPeerNonce))
		//	throw new CryptoException("Duplicated nonce");

		//	lastPeerNonce = nonce;
		CryptoBox.PublicKey pk = sender.toEncryptionKey();
		CryptoBox.PrivateKey sk = encryptionKeyPair.privateKey();
		byte[] cipher = Arrays.copyOfRange(data, CryptoBox.Nonce.BYTES, data.length);
		return CryptoBox.decrypt(cipher, pk, sk, nonce);
	}

	@Override
	public CryptoContext createCryptoContext(Id id) {
		CryptoBox.PublicKey pk = id.toEncryptionKey();
		CryptoBox box = CryptoBox.fromKeys(pk, encryptionKeyPair.privateKey());
		return new CryptoContext(id, box);
	}

	@Override
	protected void setPermission(Permission permission) {
		super.setPermission(permission);
	}

	private Map<Id, Member> members() {
		if (members == null && memberLoader != null) {
			HashMap<Id, Member> map = new HashMap<>();

			try {
				List<Member> lst = memberLoader.call();
				lst.forEach(m -> map.put(m.getId(), m));
				members = map;
			} catch (Exception e) {
				return Collections.emptyMap();
			}
		}

		return members;
	}

	protected void invalidateMembers() {
		members = null;
	}

	@Override
	protected List<Member> setMembersRole(List<Id> memberIds, Role role) {
		return super.setMembersRole(memberIds, role);
	}

	@Override
	protected List<Member> removeMembers(List<Id> memberIds) {
		Map<Id, Member> members = members();

		return memberIds.stream().map(id -> {
			return members.remove(id);
		}).collect(Collectors.toList());
	}

	@Override
	protected void addMember(Member member) {
		members().put(member.getId(), member);
	}

	@Override
	protected Member removeMember(Id memberId) {
		return members.remove(memberId);
	}

	@Override
	public int size() {
		return members().size();
	}

	@Override
	public List<Member> getMembers() {
		return new ArrayList<>(members().values());
	}

	@Override
	public Member getMember(Id id) {
		return members().get(id);
	}

	@Override
	protected void update(Channel channel) {
		super.update(channel);
	}

	@Override
	public int hashCode() {
		return getId().hashCode() + 0x8A7B3340;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder();

		repr.append("Channel: ")
			.append(getId().toBase58String()).append('[');

		if (getName() != null)
			repr.append("name= ").append(getName()).append(", ");

		if (hasAvatar())
			repr.append("avatar, ");

		if (getNotice() != null)
			repr.append("notice= ").append(getNotice()).append(", ");

		byte[] key = getPrivateKey();
		if (key != null)
			repr.append("sk*, ");

		if (getOwner() != null)
			repr.append("owner= ").append(getOwner().toBase58String()).append(", ");

		if (getPermission() != null)
			repr.append("permission= ").append(getPermission()).append(", ");

		if (getRemark() != null)
			repr.append("remark= ").append(getRemark()).append(", ");

		if (getTags() != null)
			repr.append("tags= ").append(getTags()).append(", ");

		if (isMuted())
			repr.append("muted, ");

		repr.append("created: ").append(Instant.ofEpochMilli(getCreated())).append(", ")
			.append("modified: ").append(Instant.ofEpochMilli(getLastModified())).append(']');

		return repr.toString();
	}
}
