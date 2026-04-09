package io.bosonnetwork.photonmessaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoIdentity;

public class InviteTicketTests {
	@Test
	void testNamedTicketValidation() {
		CryptoIdentity inviterIdentity = new CryptoIdentity();
		CryptoIdentity sessionIdentity = new CryptoIdentity();
		Id channelId = Id.random();
		Id sessionId = sessionIdentity.getId();
		Id inviterId = inviterIdentity.getId();
		Id inviteeId = Id.random();
		byte[] sessionKey = sessionIdentity.getKeyPair().privateKey().bytes();
		long expiration = System.currentTimeMillis() + 3600000;

		// InviteTicket ticket = new InviteTicket(channelId, sessionId, inviterId, inviteeId, expiration, sig, sessionKey);
		InviteTicket ticket = InviteTicket.create(inviterIdentity, channelId, sessionId, inviteeId, expiration, sessionKey);
		assertTrue(ticket.isValid());
		assertFalse(ticket.isExpired());

		// Wrong signature
		InviteTicket invalidTicket = new InviteTicket(channelId, sessionId, inviterId, inviteeId, expiration, new byte[64], sessionKey);
		assertFalse(invalidTicket.isValid());
	}

	@Test
	void testBearerTicketValidation() {
		CryptoIdentity inviterIdentity = new CryptoIdentity();
		CryptoIdentity sessionIdentity = new CryptoIdentity();
		Id channelId = Id.random();
		Id sessionId = sessionIdentity.getId();
		Id inviterId = inviterIdentity.getId();
		long expiration = System.currentTimeMillis() + 3600000;
		byte[] sessionKey = sessionIdentity.getKeyPair().privateKey().bytes();

		InviteTicket ticket = InviteTicket.create(inviterIdentity, channelId, sessionId, null, expiration, sessionKey);
		assertTrue(ticket.isBearerTicket());
		assertTrue(ticket.isValid());
	}

	@Test
	void testExpiration() {
		CryptoIdentity inviterIdentity = new CryptoIdentity();
		CryptoIdentity sessionIdentity = new CryptoIdentity();
		Id channelId = Id.random();
		Id sessionId = sessionIdentity.getId();
		Id inviterId = inviterIdentity.getId();
		long expiration = System.currentTimeMillis() - 1000;
		byte[] sessionKey = sessionIdentity.getKeyPair().privateKey().bytes();

		InviteTicket expiredTicket = InviteTicket.create(inviterIdentity, channelId, sessionId, null, expiration, sessionKey);
		assertTrue(expiredTicket.isExpired());
	}
}