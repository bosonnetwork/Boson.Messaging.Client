package io.bosonnetwork.messaging;

import io.bosonnetwork.messaging.rpc.RPCError;

public class RPCException extends MessagingException {
	private static final long serialVersionUID = -8443942048672744708L;

	private RPCError error;

	/**
	 * Constructs a new exception with {@code null} as its detail message.
	 * The cause is not initialized, and may subsequently be initialized by a
	 * call to {@link #initCause}.
	 */
	public RPCException() {
		super();
	}

	public RPCException(RPCError error) {
		this(error.getMessage());
		this.error = error;
	}
	/**
	 * Constructs a new exception with the specified detail message.  The
	 * cause is not initialized, and may subsequently be initialized by
	 * a call to {@link #initCause}.
	 *
	 * @param   message   the detail message. The detail message is saved for
	 *		  later retrieval by the {@link #getMessage()} method.
	 */
	public RPCException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified detail message and
	 * cause.  <p>Note that the detail message associated with
	 * {@code cause} is <i>not</i> automatically incorporated in
	 * this exception's detail message.
	 *
	 * @param  message the detail message (which is saved for later retrieval
	 *		 by the {@link #getMessage()} method).
	 * @param  cause the cause (which is saved for later retrieval by the
	 *		 {@link #getCause()} method).  (A {@code null} value is
	 *		 permitted, and indicates that the cause is nonexistent or
	 *		 unknown.)
	 * @since  1.4
	 */
	public RPCException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new exception with the specified cause and a detail
	 * message of {@code (cause==null ? null : cause.toString())} (which
	 * typically contains the class and detail message of {@code cause}).
	 * This constructor is useful for exceptions that are little more than
	 * wrappers for other throwables (for example, {@link
	 * java.security.PrivilegedActionException}).
	 *
	 * @param  cause the cause (which is saved for later retrieval by the
	 *		 {@link #getCause()} method).  (A {@code null} value is
	 *		 permitted, and indicates that the cause is nonexistent or
	 *		 unknown.)
	 * @since  1.4
	 */
	public RPCException(Throwable cause) {
		super(cause);
	}

	public RPCError getError() {
		return error;
	}
}
