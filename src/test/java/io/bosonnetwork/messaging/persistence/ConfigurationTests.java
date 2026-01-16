package io.bosonnetwork.messaging.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.bosonnetwork.json.Json;
import net.datafaker.Faker;

public class ConfigurationTests {
	private static TestDatabase db;
	private static Faker faker = new Faker();

	@BeforeAll
	static void setup() throws IOException {
		db = TestDatabase.open("configuration.db");
	}

	@AfterAll
	static void teardown() throws IOException {
		db.close();
	}

	@Test
	void testConfig() throws Exception {
		Map<String, Object> newConfig = Map.of(
				"app", faker.app().name(),
				"device", faker.device().modelName(),
				"avatar", faker.avatar().image());


		db.getJdbi().useHandle(handle -> {
			var dao = handle.attach(Configuration.class);

			byte[] config = dao.get("test").orElse(null);
			assertNull(config);

			var rc = dao.put("test", Json.toBytes(newConfig));
			assertEquals(1, rc);

			config = dao.get("test").get();
			var c = Json.parse(config);
			assertEquals(newConfig, c);
		});
	}

}