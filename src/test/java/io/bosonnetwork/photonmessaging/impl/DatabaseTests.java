package io.bosonnetwork.photonmessaging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.FriendRequest;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.photonmessaging.impl.database.PostgresDatabase;
import io.bosonnetwork.photonmessaging.impl.database.SqliteDatabase;
import io.bosonnetwork.utils.FileUtils;
import net.datafaker.Faker;

@ExtendWith(VertxExtension.class)
@SuppressWarnings("CodeBlock2Expr")
public class DatabaseTests {
	private static final Path testRoot = Path.of(System.getProperty("java.io.tmpdir"), "boson");
	private static final Path testDir = testRoot.resolve("photon-messaging-client").resolve("DatabaseTests");

	private static PostgresqlServer pgServer;
	private static Database postgres;
	private static Database sqlite;
	private static final Faker faker = new Faker();

	private static final List<Arguments> dbs = new ArrayList<>();

	@BeforeAll
	static void setup(Vertx vertx, VertxTestContext context) throws Exception {
		FileUtils.deleteFile(testDir);
		Files.createDirectories(testDir);

		// Initialize Postgres
		Future<Integer> f1;
		try {
			pgServer = PostgresqlServer.start("photon_client", "test", "secret");
		} catch (Exception e) {
			System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			System.err.println("Start PostgreSQL container failed: " + e.getMessage());
			System.err.println("Check your Docker installation.");
			System.err.println("Skipping Postgres tests.");
			System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		}

		if (pgServer != null) {
			postgres = new PostgresDatabase(pgServer.getDatabaseUri(), 4, "photon_test");
			f1 = postgres.initialize(vertx)
					.onSuccess(v -> dbs.add(Arguments.of("postgres", postgres)));
		} else {
			f1 = Future.succeededFuture(0);
		}

		// Initialize SQLite
		String sqliteUri = "jdbc:sqlite:" + testDir.resolve("client.db");
		Database sqlite = new SqliteDatabase(sqliteUri, 1);
		Future<Integer> f2 = sqlite.initialize(vertx)
				.onSuccess(v -> dbs.add(Arguments.of("sqlite", sqlite)));

		Future.all(f1, f2).onComplete(context.succeedingThenComplete());
	}

	@AfterAll
	static void teardown(VertxTestContext context) {
		List<Future<Void>> futures = new ArrayList<>();
		if (sqlite != null) futures.add(sqlite.close());
		if (postgres != null) futures.add(postgres.close());

		Future.all(futures).onComplete(ar -> {
			if (pgServer != null)
				pgServer.stop();
			context.completeNow();
		});
	}

	static Stream<Arguments> databaseProvider() {
		return dbs.stream();
	}

	@BeforeEach
	void clearDatabase(Vertx vertx, VertxTestContext context) {
		List<Future<Void>> futures = new ArrayList<>();
		for (Arguments arg : dbs) {
			Database db = (Database) arg.get()[1];
			futures.add(db.clearContacts(0)
					.compose(v -> db.clearFriendRequests())
					.compose(v -> db.clearMessages()));
		}
		Future.all(futures).onComplete(context.succeedingThenComplete());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testContactsRevision(String name, Database db, VertxTestContext context) {
		db.getContactsRevision().onComplete(context.succeeding(rev -> {
			context.verify(() -> assertEquals(0, rev));
			db.putContacts(123, List.of()).onComplete(context.succeeding(v -> {
				db.getContactsRevision().onComplete(context.succeeding(rev2 -> {
					context.verify(() -> assertEquals(123, rev2));
					context.completeNow();
				}));
			}));
		}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testFriendLifecycle(String name, Database db, VertxTestContext context) {
		Id friendId = Id.random();
		Friend friend = new Friend(friendId, null, "Alice", null, "Remark", null, false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);

		db.putContactLocally(friend).onComplete(context.succeeding(v1 -> {
			db.getContact(friendId).onComplete(context.succeeding(c -> {
				context.verify(() -> {
					assertNotNull(c);
					assertTrue(c instanceof Friend);
					assertEquals("Alice", c.getName());
				});

				db.removeContactLocally(friendId).onComplete(context.succeeding(v2 -> {
					db.getContact(friendId).onComplete(context.succeeding(c2 -> {
						context.verify(() -> assertNull(c2));
						context.completeNow();
					}));
				}));
			}));
		}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testChannelAndMembers(String name, Database db, VertxTestContext context) {
		Id channelId = Id.random();
		ChannelImpl channel = new ChannelImpl(channelId, null, Id.random(), Channel.Permission.PUBLIC, "Channel 1", "Notice", false, System.currentTimeMillis(), System.currentTimeMillis());

		Id m1 = Id.random();
		Id m2 = Id.random();
		Channel.Member member1 = new ChannelMember(m1, Channel.Role.MEMBER, System.currentTimeMillis());
		Channel.Member member2 = new ChannelMember(m2, Channel.Role.MEMBER, System.currentTimeMillis());

		db.putContactLocally(channel)
				.compose(v -> db.putChannelMembers(channelId, List.of(member1, member2)))
				.compose(v -> db.getAllChannelMembers(channelId))
				.onComplete(context.succeeding(members -> {
					context.verify(() -> assertEquals(2, members.size()));
					
					db.refillChannelMembers(channelId, List.of(member1))
							.compose(v -> db.getAllChannelMembers(channelId))
							.onComplete(context.succeeding(members2 -> {
								context.verify(() -> assertEquals(1, members2.size()));
								context.completeNow();
							}));
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testMessageHistory(String name, Database db, VertxTestContext context) {
		Id convId = Id.random();
		long baseTime = System.currentTimeMillis();

		// Insert 3 messages with same timestamp to test 'rid' stability
		List<Future<Void>> futures = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Id mid = Id.random();
			DefaultContent<String> content = new DefaultContent<String>(Map.of(), "Msg " + i);
			MessageImpl<DefaultContent<?>> msg = new MessageImpl<>(mid, Id.random(), Message.Type.CONTENT_MESSAGE, baseTime, content);
			msg.setConversationId(convId);
			futures.add(db.putMessage(msg));
		}

		Future.all(futures)
				.compose(v -> db.getMessages(convId, 0, 10, 0))
				.onComplete(context.succeeding(messages -> {
					context.verify(() -> {
						assertEquals(3, messages.size());
						// Verify rid-based ordering (stable)
						assertTrue(messages.get(0).getRid() < messages.get(1).getRid());
						assertTrue(messages.get(1).getRid() < messages.get(2).getRid());
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testConversationJoin(String name, Database db, VertxTestContext context) {
		Id contactId = Id.random();
		Friend friend = new Friend(contactId, null, "Bob", null, null, null, false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);

		Id msgId = Id.random();
		DefaultContent<String> content = new DefaultContent<String>(Map.of(), "Last");
		MessageImpl<DefaultContent<?>> msg = new MessageImpl<>(msgId, Id.random(), Message.Type.CONTENT_MESSAGE, System.currentTimeMillis(), content);
		msg.setConversationId(contactId);

		db.putContactLocally(friend)
				.compose(v -> db.putMessage(msg))
				.compose(v -> db.getConversation(contactId))
				.onComplete(context.succeeding(conv -> {
					context.verify(() -> {
						assertNotNull(conv);
						assertEquals("Bob", conv.getParticipant().getName());
						assertEquals(msg.getReceivedAt(), conv.getUpdatedAt());
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testCascadeDelete(String name, Database db, VertxTestContext context) {
		Id channelId = Id.random();
		ChannelImpl channel = new ChannelImpl(channelId, null, Id.random(), Channel.Permission.PUBLIC, "Cascade", "Notice", false, System.currentTimeMillis(), System.currentTimeMillis());
		Id m1 = Id.random();
		Channel.Member member1 = new ChannelMember(m1, Channel.Role.MEMBER, System.currentTimeMillis());

		db.putContactLocally(channel)
				.compose(v -> db.putChannelMembers(channelId, List.of(member1)))
				.compose(v -> {
					MessageImpl<DefaultContent<?>> msg = randomMessage(channelId);
					return db.putMessage(msg);
				})
				.compose(v -> {
					// Verify members and messages exist
					return db.getAllChannelMembers(channelId).compose(members -> {
						assertEquals(1, members.size());
						return db.getMessages(channelId, 0, 10, 0);
					}).onSuccess(messages -> assertEquals(1, messages.size()));
				})
				.compose(v -> db.removeContactLocally(channelId))
				.compose(v -> db.getAllChannelMembers(channelId))
				.compose(members -> {
					context.verify(() -> assertEquals(0, members.size(), "Channel members should be Cascaded deleted"));
					return db.getMessages(channelId, 0, 10, 0);
				})
				.onComplete(context.succeeding(messages -> {
					context.verify(() -> assertEquals(0, messages.size(), "Messages should be Cascaded deleted"));
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testMessageManagement(String name, Database db, VertxTestContext context) {
		Id convId = Id.random();
		MessageImpl<DefaultContent<?>> m1 = randomMessage(convId);
		MessageImpl<DefaultContent<?>> m2 = randomMessage(convId);
		MessageImpl<DefaultContent<?>> m3 = randomMessage(convId);

		db.putMessage(m1)
				.compose(v -> db.putMessage(m2))
				.compose(v -> db.putMessage(m3))
				.compose(v -> {
					m1.setSentAt(System.currentTimeMillis() + 1000);
					return db.updateMessageSentTime(m1);
				})
				.compose(v -> db.getMessages(convId, 0, 10, 0))
				.compose(messages -> {
					context.verify(() -> {
						assertEquals(3, messages.size());
						Message sentM1 = messages.stream().filter(m -> m.getId().equals(m1.getId())).findFirst().orElseThrow();
						assertEquals(m1.getSentAt(), sentM1.getSentAt());
					});
					return db.removeMessages(List.of(m1.getRid()));
				})
				.compose(removed -> {
					context.verify(() -> assertTrue(removed));
					return db.getMessages(convId, 0, 10, 0);
				})
				.compose(messages -> {
					context.verify(() -> assertEquals(2, messages.size()));
					return db.removeMessages(convId);
				})
				.compose(removed -> {
					context.verify(() -> assertTrue(removed));
					return db.getMessages(convId, 0, 10, 0);
				})
				.onComplete(context.succeeding(messages -> {
					context.verify(() -> assertEquals(0, messages.size()));
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testChannelManagement(String name, Database db, Vertx vertx, VertxTestContext context) {
		ChannelImpl channel = randomChannel();
		Id c1Id = Id.random();
		Id c2Id = Id.random();
		Channel.Member m1 = new ChannelMember(c1Id, Channel.Role.MEMBER, System.currentTimeMillis());
		Channel.Member m2 = new ChannelMember(c2Id, Channel.Role.MEMBER, System.currentTimeMillis());

		db.putContactLocally(channel)
				.compose(v -> db.putChannelMembers(channel.getId(), List.of(m1, m2)))
				.compose(v -> db.updateChannelMembersRole(channel.getId(), List.of(c1Id), Channel.Role.MODERATOR))
				.compose(success -> {
					context.verify(() -> assertTrue(success));
					return db.getChannelMember(channel.getId(), c1Id);
				})
				.compose(member -> {
					context.verify(() -> {
						assertNotNull(member);
						assertEquals(Channel.Role.MODERATOR, member.getRole());
					});
					Id newOwner = Id.random();
					return db.updateChannelOwnership(channel.getId(), channel.getOwnerId(), newOwner);
				})
				.compose(v -> db.getContact(channel.getId()))
				.compose(c -> {
					context.verify(() -> {
						assertNotNull(c);
						assertEquals(Channel.Permission.PUBLIC, ((Channel) c).getPermission());
					});
					return db.removeChannelMembers(channel.getId(), List.of(c2Id));
				})
				.compose(success -> {
					context.verify(() -> assertTrue(success));
					return db.getAllChannelMembers(channel.getId());
				})
				.onComplete(context.succeeding(members -> {
					context.verify(() -> {
						assertTrue(members.size() >= 1);
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testConversationManagement(String name, Database db, VertxTestContext context) {
		Id c1Id = Id.random();
		Id c2Id = Id.random();
		Friend f1 = new Friend(c1Id, Signature.KeyPair.random().privateKey().bytes(), "Alice", null, null, null, false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);
		Friend f2 = new Friend(c2Id, Signature.KeyPair.random().privateKey().bytes(), "Bob", null, null, null, false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);

		db.putContactLocally(f1)
				.compose(v -> db.putContactLocally(f2))
				.compose(v -> db.putMessage(randomMessage(c1Id)))
				.compose(v -> db.putMessage(randomMessage(c2Id)))
				.compose(v -> db.getAllConversations())
				.compose(conversations -> {
					context.verify(() -> assertEquals(2, conversations.size()));
					return db.removeConversations(List.of(c1Id));
				})
				.compose(success -> {
					context.verify(() -> assertTrue(success));
					return db.getConversation(c1Id);
				})
				.compose(conv -> {
					context.verify(() -> assertNull(conv));
					return db.removeConversation(c2Id);
				})
				.compose(success -> {
					context.verify(() -> assertTrue(success));
					return db.getAllConversations();
				})
				.onComplete(context.succeeding(conversations -> {
					context.verify(() -> assertEquals(0, conversations.size()));
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testChannelMemberRetrieval(String name, Database db, VertxTestContext context) {
		ChannelImpl channel = randomChannel();
		Id m1Id = Id.random();
		Id m2Id = Id.random();
		Channel.Member m1 = new ChannelMember(m1Id, Channel.Role.MEMBER, System.currentTimeMillis());
		Channel.Member m2 = new ChannelMember(m2Id, Channel.Role.MEMBER, System.currentTimeMillis());

		db.putContactLocally(channel)
				.compose(v -> db.putChannelMembers(channel.getId(), List.of(m1, m2)))
				.compose(v -> db.getChannelMember(channel.getId(), m1Id))
				.compose(member -> {
					context.verify(() -> {
						assertNotNull(member);
						assertEquals(m1Id, member.getId());
					});
					return db.getChannelMembers(channel.getId(), List.of(m2Id));
				})
				.onComplete(context.succeeding(members -> {
					context.verify(() -> {
						assertEquals(1, members.size());
						assertEquals(m2Id, members.get(0).getId());
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testConversationUpdateOnMessage(String name, Database db, VertxTestContext context) {
		Id convId = Id.random();
		Friend friend = new Friend(convId, Signature.KeyPair.random().privateKey().bytes(), "Alice", null, null, null, false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);
		MessageImpl<DefaultContent<?>> m1 = randomMessage(convId);
		long time1 = m1.getReceivedAt();

		db.putContactLocally(friend)
				.compose(v -> db.putMessage(m1))
				.compose(v -> db.getConversation(convId))
				.compose(conv -> {
					context.verify(() -> {
						assertNotNull(conv);
						assertEquals(time1, conv.getUpdatedAt());
					});
					MessageImpl<DefaultContent<?>> m2 = randomMessage(convId);
					m2.received(time1 + 5000);
					return db.putMessage(m2).map(m2.getReceivedAt());
				})
				.compose(time2 -> db.getConversation(convId).map(conv -> Map.entry(time2, conv)))
				.onComplete(context.succeeding(entry -> {
					context.verify(() -> {
						assertEquals(entry.getKey(), entry.getValue().getUpdatedAt());
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testFullContactSyncScenario(String name, Database db, VertxTestContext context) {
		Friend f1 = randomFriend();
		ChannelImpl c1 = randomChannel();
		List<Contact> contacts = List.of(f1, c1);
		int newRevision = 10;

		db.putContacts(newRevision, contacts)
				.compose(v -> db.getContactsRevision())
				.compose(rev -> {
					context.verify(() -> assertEquals(newRevision, rev));
					return db.getAllContacts();
				})
				.onComplete(context.succeeding(all -> {
					context.verify(() -> {
						assertEquals(2, all.size());
						assertTrue(all.stream().anyMatch(c -> c.getId().equals(f1.getId())));
						assertTrue(all.stream().anyMatch(c -> c.getId().equals(c1.getId())));

						Contact fetchedC1 = all.stream().filter(c -> c.getId().equals(c1.getId())).findFirst().orElseThrow();
						assertTrue(fetchedC1 instanceof Channel);
						assertEquals(c1.getOwnerId(), ((Channel) fetchedC1).getOwnerId());
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testFriendRequestAcceptanceFlow(String name, Database db, VertxTestContext context) {
		Id userId = Id.random();
		Id initiatorId = Id.random();
		FriendRequest request = new FriendRequestImpl(userId, initiatorId, faker.lorem().sentence());

		db.putFriendRequest(request)
				.compose(v -> db.getFriendRequest(userId))
				.compose(fr -> {
					context.verify(() -> {
						assertNotNull(fr);
						assertTrue(fr.getHello().length() > 0);
					});
					FriendRequestImpl fri = (FriendRequestImpl) fr;
					fri.accept();
					return db.putFriendRequest(fri);
				})
				.compose(v -> {
					Friend friend = new Friend(userId, Signature.KeyPair.random().privateKey().bytes(), "Alice", null, null, null, false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);
					return db.putContacts(2, List.of(friend));
				})
				.compose(v -> db.getContact(userId))
				.onComplete(context.succeeding(contact -> {
					context.verify(() -> {
						assertNotNull(contact);
						assertTrue(contact instanceof Friend);
						assertEquals(userId, contact.getId());
					});
					context.completeNow();
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("databaseProvider")
	@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
	void testChannelLifecycleScenario(String name, Database db, VertxTestContext context) {
		ChannelImpl channel = randomChannel();
		Channel.Member owner = new ChannelMember(channel.getOwnerId(), Channel.Role.OWNER, System.currentTimeMillis());

		db.putContactLocally(channel)
				.compose(v -> db.putChannelMembers(channel.getId(), List.of(owner)))
				.compose(v -> {
					MessageImpl<DefaultContent<?>> msg = randomMessage(channel.getId());
					return db.putMessage(msg);
				})
				.compose(v -> db.getConversation(channel.getId()))
				.onComplete(context.succeeding(conv -> {
					context.verify(() -> {
						assertNotNull(conv);
						assertTrue(conv.getParticipant() instanceof Channel);
						assertEquals(channel.getName(), conv.getParticipant().getName());
					});
					context.completeNow();
				}));
	}

	// Helpers
	static Friend randomFriend() {
		return new Friend(Id.random(), Signature.KeyPair.random().privateKey().bytes(),
				faker.name().fullName(), faker.lorem().sentence(),
				faker.internet().image(), faker.phoneNumber().phoneNumber(),
				false, false, System.currentTimeMillis(), System.currentTimeMillis(), 1);
	}

	static ChannelImpl randomChannel() {
		return new ChannelImpl(Id.random(), Signature.KeyPair.random().privateKey().bytes(),
				Id.random(), Channel.Permission.PUBLIC,
				faker.company().name(), faker.lorem().sentence(),
				false, System.currentTimeMillis(), System.currentTimeMillis());
	}

	static Channel.Member randomMember() {
		return new ChannelMember(Id.random(), Channel.Role.MEMBER, System.currentTimeMillis());
	}

	static MessageImpl<DefaultContent<?>> randomMessage(Id conversationId) {
		Id mid = Id.random();
		long now = System.currentTimeMillis();
		DefaultContent<String> content = new DefaultContent<>(Map.of(), faker.lorem().paragraph());
		MessageImpl<DefaultContent<?>> msg = new MessageImpl<>(mid, Id.random(), Message.Type.CONTENT_MESSAGE, now, content);
		msg.setConversationId(conversationId);
		return msg;
	}
}
