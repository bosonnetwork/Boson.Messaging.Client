package io.bosonnetwork.messaging;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.CryptoBox.Nonce;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.Signature;

public class Group {
	@JsonProperty(value = "id", required = true)
	private final Id id; // server public key
	@JsonProperty(value = "owner", required = true)
	private Id owner;

	private Signature.KeyPair keyPair; // member keypair
	private CryptoBox.KeyPair encryptionKeyPair;

	@JsonProperty(value = "permission", required = true)
	private Permission permission;

	@JsonProperty(value = "name")
	@JsonInclude(Include.NON_EMPTY)
	private String name;
	@JsonProperty(value = "notice")
	@JsonInclude(Include.NON_EMPTY)
	private String notice;

	@JsonProperty(value = "created", required = true)
	private final long created;

	private HashMap<Id, Role> members;

	private transient Nonce nextNonce = Nonce.random();

	public static enum Permission {
		PUBLIC(0), MEMBER_INVITE(1), MODERATOR_INVITE(2), OWNER_INVITE(3);

		private int value;

		Permission(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		@JsonCreator
		public static Permission valueOf(int value) {
			switch (value) {
			case 0:
				return PUBLIC;

			case 1:
				return MEMBER_INVITE;

			case 2:
				return MODERATOR_INVITE;

			case 3:
				return OWNER_INVITE;

			default:
				throw new IllegalArgumentException("Invalid permission value");
			}
		}
	}

	public static enum Role {
		OWNER(0), MODERATOR(1), MEMBER(2), BANNED(-1);

		private int value;

		Role(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		public boolean isBanned() {
			return value == BANNED.value;
		}

		@JsonCreator
		public static Role valueOf(int value) {
			switch (value) {
			case 0:
				return OWNER;

			case 1:
				return MODERATOR;

			case 2:
				return MEMBER;

			case -1:
				return BANNED;

			default:
				throw new IllegalArgumentException("Invalid role value");
			}
		}
	}

	Group(Id id, Id owner, Permission permission, String name, String notice, long created, HashMap<Id, Role> members) {
		this.id = id;
		this.owner = owner;

		this.permission = permission;
		this.name = name;
		this.notice = notice;
		this.created = created;

		this.members = members;
	}

	void setMemberPrivateKey(byte[] privateKey) {
		this.keyPair = Signature.KeyPair.fromPrivateKey(privateKey);
		this.encryptionKeyPair = CryptoBox.KeyPair.fromSignatureKeyPair(keyPair);
	}

	public Id getId() {
		return id;
	}

    protected final HashMap<Id, Role> _getMembers() {
        return members;
    }

    protected final void _setMembers(HashMap<Id, Role> members) {
        this.members = members;
    }

    /*
	public boolean setOwner(Id owner) {
		synchronized(lock) {
			if (this.owner.equals(owner))
				return true;

			HashMap<Id, Role> current = _getMembers();

			if (!current.containsKey(owner) || current.get(owner).isBanned())
				return false;

			boolean success = repository != null ? repository.updateOwner(id, owner) : true;
			if (success) {
				@SuppressWarnings("unchecked")
				HashMap<Id, Role> newMembers = (HashMap<Id, Role>)current.clone();
				newMembers.put(this.owner, Role.MEMBER);
				newMembers.put(owner, Role.OWNER);
				this.owner = owner;
				_setMembers(newMembers);
			}

			return success;
		}
	}
	*/

	public Id getOwner() {
		return owner;
	}

	public boolean isOwner(Id id) {
		return id.equals(owner);
	}

	public boolean isModerator(Id id) {
		return members.containsKey(id) && members.get(id) == Role.MODERATOR;
	}

	public boolean isBanned(Id id) {
		return members.containsKey(id) && members.get(id) == Role.BANNED;
	}

	public boolean isMember(Id id) {
		return members.containsKey(id) && members.get(id) != Role.BANNED;
	}

	public boolean isQualifiedInviter(Id inviter) {
		Map<Id, Role> current = _getMembers();

		if (!current.containsKey(inviter))
			return false;

		Role role = current.get(inviter);
		switch (permission) {
		case PUBLIC:
		case MEMBER_INVITE:
			return role.value <= Role.MEMBER.value;

		case MODERATOR_INVITE:
			return role.value <= Role.MODERATOR.value;

		case OWNER_INVITE:
			return inviter.equals(owner);

		default:
			return false;
		}
	}

	Signature.KeyPair getKeyPair() {
		return keyPair;
	}

	public long getCreated() {
		return created;
	}

	public int size() {
		return members.size();
	}

	public int banned() {
		return members.values().stream().mapToInt(r -> r.isBanned() ? 1 : 0).sum();
	}

	/*
	public void setPermission(Permission permission) {
		synchronized(lock) {
			if (this.permission == permission)
				return true;

			boolean success = repository != null ? repository.updatePermission(id, permission) : true;
			if (success)
				this.permission = permission;

			return success;
		}
	}

	public Permission getPermission() {
		return permission;
	}

	public boolean setName(String name) throws StorageException {
		synchronized(lock) {
			if (name != null && (name.isBlank() || name.isEmpty()))
				name = null;

			if (Objects.equals(this.name, name))
				return true;

			boolean success = repository != null ? repository.updateName(id, name) : true;
			if (success)
				this.name = name;

			return success;
		}
	}

	public String getName() {
		return name;
	}

	public Boolean setNotice(String notice) throws StorageException {
		synchronized(lock) {
			if (notice != null && (notice.isBlank() || notice.isEmpty()))
				notice = null;

			if (Objects.equals(this.notice, notice))
				return true;

			boolean success = repository != null ? repository.updateNotice(id, notice) : true;
			if (success)
				this.notice = notice;

			return success;
		}
	}

	public String getNotice() {
		return notice;
	}

	public Map<Id, Role> getMembers() {
		return Collections.unmodifiableMap(_getMembers());
	}

	public Role getMember(Id member) {
		return _getMembers().get(member);
	}

	public boolean removeMember(Id member) throws StorageException {
		synchronized(lock) {
			HashMap<Id, Role> current = _getMembers();
			if (member.equals(owner) || !current.containsKey(member))
				return false;

			boolean success = repository != null ?
					repository.removeMembers(id, Collections.singleton(member)) : true;

			if (success) {
				@SuppressWarnings("unchecked")
				HashMap<Id, Role> newMembers = (HashMap<Id, Role>)current.clone();
				newMembers.remove(member);
				_setMembers(newMembers);
			}

			return success;
		}
	}

	public boolean removeMembers(Collection<Id> members) throws StorageException {
		synchronized(lock) {
			ArrayList<Id> toBeRemoved = new ArrayList<>(members);

			toBeRemoved.removeIf(id -> id.equals(owner));

			HashMap<Id, Role> current = _getMembers();
			toBeRemoved.removeIf(id -> !current.containsKey(id));
			if (toBeRemoved.isEmpty())
				return false;

			boolean success = repository != null ? repository.removeMembers(id, toBeRemoved) : true;
			if (success) {
				@SuppressWarnings("unchecked")
				HashMap<Id, Role> newMembers = (HashMap<Id, Role>)current.clone();
				toBeRemoved.forEach(id -> newMembers.remove(id));
				_setMembers(newMembers);
			}

			return success;
		}
	}

	public boolean banMember(Id member) throws StorageException {
		synchronized(lock) {
			HashMap<Id, Role> current = _getMembers();
			if (member.equals(owner) || !current.containsKey(member))
				return false;

			if (current.get(member).isBanned())
				return true;

			boolean success = repository != null ?
					repository.banMembers(id, Collections.singleton(member)) : true;

			if (success)
				current.put(member, Role.BANNED);

			return success;
		}
	}

	public boolean unbanMember(Id member) throws StorageException {
		synchronized(lock) {
			HashMap<Id, Role> current = _getMembers();
			if (!current.containsKey(member))
				return false;

			if (!current.get(member).isBanned())
				return true;

			boolean success = repository != null ?
					repository.unbanMembers(id, Collections.singleton(member)) : true;

			if (success)
				current.put(member, Role.MEMBER);

			return success;
		}
	}

	public boolean banMembers(Collection<Id> members) throws StorageException {
		synchronized(lock) {
			ArrayList<Id> toBeBanned = new ArrayList<>(members);

			toBeBanned.removeIf(id -> id.equals(owner));

			HashMap<Id, Role> current = _getMembers();
			toBeBanned.removeIf(id -> (!current.containsKey(id) || current.get(id).isBanned()));
			if (toBeBanned.isEmpty())
				return false;

			boolean success = repository != null ? repository.banMembers(id, toBeBanned) : true;
			if (success)
				toBeBanned.forEach(id -> current.put(id, Role.BANNED));

			return success;
		}
	}

	public boolean unbanMembers(Collection<Id> members) throws StorageException {
		synchronized(lock) {
			ArrayList<Id> toBeUnbanned = new ArrayList<>(members);

			HashMap<Id, Role> current = _getMembers();
			toBeUnbanned.removeIf(id -> (!current.containsKey(id) || !current.get(id).isBanned()));
			if (toBeUnbanned.isEmpty())
				return false;

			boolean success = repository != null ? repository.unbanMembers(id, toBeUnbanned) : true;
			if (success)
				toBeUnbanned.forEach(id -> current.put(id, Role.MEMBER));

			return success;
		}
	}

	public boolean setRole(Id member, Role role) throws StorageException {
		synchronized(lock) {
			HashMap<Id, Role> current = _getMembers();
			if (!current.containsKey(member) || current.get(member).isBanned() || member.equals(owner))
				return false;

			if (current.get(member) == role)
				return true;

			boolean success = repository != null ?
					repository.updateRole(id, member, role) : true;

			if (success)
				current.put(member, role);

			return success;
		}
	}
	*/

	private synchronized Nonce getAndIncrementNonce() {
		Nonce nonce = nextNonce;
		nextNonce = nonce.increment();
		return nonce;
	}

	public byte[] encrypt(byte[] plain, Id receiver) throws CryptoException {
		CryptoBox.PublicKey receiverPk = CryptoBox.PublicKey.fromSignatureKey(Signature.PublicKey.fromBytes(receiver.bytes()));
		Nonce nonce = getAndIncrementNonce();

		// TODO: how to avoid the memory copy?!
		byte[] cipher = CryptoBox.encrypt(plain, receiverPk, encryptionKeyPair.privateKey(), nonce);
		byte[] buf = new byte[Nonce.BYTES + cipher.length];
		System.arraycopy(nonce.bytes(), 0, buf, 0, Nonce.BYTES);
		System.arraycopy(cipher, 0, buf, Nonce.BYTES, cipher.length);
		return buf;
	}

	public byte[] decrypt(byte[] cipher, Id sender) throws CryptoException {
		if (cipher.length <= Nonce.BYTES + CryptoBox.MAC_BYTES)
			throw new CryptoException("Invalid cipher size");

		// TODO: how to avoid the memory copy?!
		byte[] n = Arrays.copyOfRange(cipher, 0, Nonce.BYTES);
		Nonce nonce = Nonce.fromBytes(n);
		byte[] m = Arrays.copyOfRange(cipher, Nonce.BYTES, cipher.length);
		CryptoBox.PublicKey senderPk = CryptoBox.PublicKey.fromSignatureKey(Signature.PublicKey.fromBytes(sender.bytes()));

		return CryptoBox.decrypt(m, senderPk, encryptionKeyPair.privateKey(), nonce);
	}

	@Override
	public int hashCode() {
		return id.hashCode() + 0x0B030909;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Group) {
			Group that = (Group)o;
			return this.id.equals(that.id) &&
					this.owner.equals(that.owner) &&
					this.keyPair.equals(that.keyPair) &&
					Objects.equals(this.name, that.name) &&
					Objects.equals(this.notice, that.notice) &&
					this.permission == that.permission &&
					this.created == that.created &&
					this.members.equals(that.members);
		}

		return false;
	}
}
