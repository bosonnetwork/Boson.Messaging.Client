package io.bosonnetwork.photonmessaging.exceptions.rpc;

import io.bosonnetwork.photonmessaging.impl.rpc.RpcErrorCode;

/**
 * This exception is thrown to indicate that an operation is forbidden
 * because the user is a banned member of a channel.
 * This exception typically arises when a banned user attempts to interact
 * with a channel for which their access has been restricted.
 */
public class ForbiddenBannedMemberException extends RpcException {
	private static final long serialVersionUID = 8986019310819396035L;

	/**
	 * Constructs a new {@code ForbiddenBannedMemberException} with the specified detail message.
	 * This exception is thrown when an operation is prohibited because the user is a banned member of a channel.
	 *
	 * @param message the detail message explaining the reason for the exception.
	 */
	public ForbiddenBannedMemberException(String message) {
		super(RpcErrorCode.FORBIDDEN_BANNED_CHANNEL_MEMBER, message);
	}

	/**
	 * Constructs a new {@code ForbiddenBannedMemberException} with the specified detail message
	 * and cause. This exception is thrown when an operation is prohibited because the user
	 * is a banned member of a channel.
	 *
	 * @param message the detail message explaining the reason for the exception.
	 * @param cause the underlying cause of the exception, which may provide additional context
	 *              for the failure.
	 */
	public ForbiddenBannedMemberException(String message, Throwable cause) {
		super(RpcErrorCode.FORBIDDEN_BANNED_CHANNEL_MEMBER, message, cause);
	}
}