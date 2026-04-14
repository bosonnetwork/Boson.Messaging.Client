package io.bosonnetwork.photonmessaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.FileUtils;
import io.bosonnetwork.utils.Hex;

public class ConfigurationTests {
	private static final Path testDir = Path.of(System.getProperty("java.io.tmpdir"), "boson", "ActiveProxyClient");

	@BeforeAll
	static void setup() throws Exception {
		if (Files.exists(testDir))
			FileUtils.deleteFile(testDir);

		Files.createDirectories(testDir);
	}

	@AfterAll
	static void tearDown() throws Exception {
		if (Files.exists(testDir))
			FileUtils.deleteFile(testDir);
	}

	@Test
	void testBuildConfig() {
		Configuration config = Configuration.builder()
				.servicePeerId(Id.random())
				.serviceEndpoint("mqtt://10.0.0.1:1883")
				.generateUserKey()
				.generateDeviceKey()
				.database("jdbc:sqlite:test.db", 1)
				.build();
		assertNotNull(config);

		config = Configuration.builder()
				.servicePeerId(Id.random())
				.serviceEndpoint("mqtts://10.0.0.1:8883")
				.userKey(Base58.encode(Signature.KeyPair.random().privateKey().bytes()))
				.deviceKey("0x" + Hex.encode(Signature.KeyPair.random().privateKey().bytes()))
				.databaseUri("postgresql://localhost:5432/test")
				.databasePoolSize(4)
				.build();
		assertNotNull(config);

		assertThrows(IllegalStateException.class, () -> Configuration.builder().build());

		assertThrows(IllegalArgumentException.class, () ->
				Configuration.builder()
						.servicePeerId(Id.random())
						.serviceEndpoint("tcp://10.0.0.1:8883")
						.generateUserKey()
						.generateDeviceKey()
						.database("jdbc:sqlite:test.db", 1)
						.build()
		);

		assertThrows(IllegalStateException.class, () ->
				Configuration.builder()
						.servicePeerId(Id.random())
						.serviceEndpoint("mqtts://10.0.0.1:8883")
						.generateDeviceKey()
						.database("jdbc:sqlite:test.db", 1)
						.build()
		);

		assertThrows(IllegalStateException.class, () ->
				Configuration.builder()
						.serviceEndpoint("mqtts://10.0.0.1:8883")
						.userKey(Base58.encode(Signature.KeyPair.random().privateKey().bytes()))
						.deviceKey("0x" + Hex.encode(Signature.KeyPair.random().privateKey().bytes()))
						.build()
		);
	}

	@Test
	void saveAndLoad() throws Exception {
		Signature.KeyPair userKey = Signature.KeyPair.random();
		Signature.KeyPair deviceKey = Signature.KeyPair.random();

		Configuration config = Configuration.builder()
				.service(Id.random(), "mqtts://10.0.0.1:8883")
				.userKey(userKey)
				.deviceKey(deviceKey)
				.database("postgresql://localhost:5432/test", 4)
				.databaseSchemaName("photon")
				.build();

		Map<String, Object> configMap = config.toMap();
		Path testFile = testDir.resolve("config.yaml");
		Json.yamlMapper().writeValue(testFile.toFile(), configMap);

		System.out.println("User id: " + Id.of(userKey.publicKey().bytes()));
		System.out.println("Device id: " + Id.of(deviceKey.publicKey().bytes()));

		System.out.println("Configuration:\n-------------");
		Files.readAllLines(testFile).forEach(System.out::println);

		Map<String, Object> loadedMap = Json.yamlMapper().readValue(testFile.toFile(), Json.mapType());
		assertEquals(configMap, loadedMap);

		Configuration loaded = Configuration.fromMap(loadedMap);
		assertEquals(config.getServicePeerId(), loaded.getServicePeerId());
		assertEquals(config.getServiceEndpoint(), loaded.getServiceEndpoint());
		assertEquals(config.getUserKey(), loaded.getUserKey());
		assertEquals(config.getDeviceKey(), loaded.getDeviceKey());
		assertEquals(config.getDatabaseUri(), loaded.getDatabaseUri());
		assertEquals(config.getDatabasePoolSize(), loaded.getDatabasePoolSize());
		assertEquals(config.getDatabaseSchemaName(), loaded.getDatabaseSchemaName());
	}
}