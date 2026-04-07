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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Conversation;
import io.bosonnetwork.photonmessaging.FriendRequest;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.photonmessaging.impl.database.PostgresDatabase;
import io.bosonnetwork.photonmessaging.impl.database.SqliteDatabase;
import io.bosonnetwork.utils.FileUtils;

@ExtendWith(VertxExtension.class)
public class DatabaseTests {
	private static final Path testRoot = Path.of(System.getProperty("java.io.tmpdir"), "boson");
	private static final Path testDir = testRoot.resolve("photon-messaging-client").resolve("DatabaseTests");

	private static PostgresqlServer pgServer;
	private static final List<Arguments> dbs = new ArrayList<>();

	@BeforeAll
	static void setup(Vertx vertx, VertxTestContext context) throws Exception {
		FileUtils.deleteFile(testDir);
		Files.createDirectories(testDir);

		// Initialize Postgres
		Future<Integer> f1;
		try {
			pgServer = PostgresqlServer.start("photon_client", "test", "secret");
			Database postgres = new PostgresDatabase(pgServer.getDatabaseUri(), 8, "photon_test");
			f1 = postgres.initialize(vertx).onSuccess(v -> dbs.add(Arguments.of("postgres", postgres)));
		} catch (Exception e) {
			System.err.println("PostgreSQL container failed to start, skipping: " + e.getMessage());
			f1 = Future.succeededFuture(0);
		}

		// Initialize SQLite
		String sqliteUri = "jdbc:sqlite:" + testDir.resolve("client.db");
		Database sqlite = new SqliteDatabase(sqliteUri, 1);
		Future<Integer> f2 = sqlite.initialize(vertx).onSuccess(v -> dbs.add(Arguments.of("sqlite", sqlite)));

		Future.all(f1, f2).onComplete(context.succeedingThenComplete());
	}

	@AfterAll
	static void teardown(VertxTestContext context) {
		List<Future<Void>> futures = new ArrayList<>();
		for (Arguments arg : dbs) {
			Database db = (Database) arg.get()[1];
			futures.add(db.close());
		}

		Future.all(futures).onComplete(ar -> {
			if (pgServer != null) pgServer.stop();
			context.completeNow();
		});
	}

	static Stream<Arguments> databaseProvider() {
		return dbs.stream();
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
				.compose(v -> db.getChannelMembers(channelId, null))
				.onComplete(context.succeeding(members -> {
					context.verify(() -> assertEquals(2, members.size()));
					
					db.refillChannelMembers(channelId, List.of(member1))
							.compose(v -> db.getChannelMembers(channelId, null))
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
					// Verify members exist
					return db.getChannelMembers(channelId, null).onSuccess(members -> assertEquals(1, members.size()));
				})
				.compose(v -> db.removeContactLocally(channelId))
				.compose(v -> db.getChannelMembers(channelId, null))
				.onComplete(context.succeeding(members -> {
					context.verify(() -> assertEquals(0, members.size(), "Channel members should be Cascaded deleted"));
					context.completeNow();
				}));
	}
}
