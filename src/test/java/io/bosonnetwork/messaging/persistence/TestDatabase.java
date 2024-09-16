package io.bosonnetwork.messaging.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.Extensions;
import org.jdbi.v3.core.statement.Slf4JSqlLogger;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import io.bosonnetwork.util.jdbi.BosonPlugin;
import io.bosonnetwork.utils.FileUtils;

public class TestDatabase {
	private static Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "boson", "TestDatabase");
	private Jdbi jdbi;

	private TestDatabase(Jdbi jdbi) {
		this.jdbi = jdbi;
	}

	public static TestDatabase open(String database) throws IOException {
		if (Files.exists(dataDir))
			FileUtils.deleteFile(dataDir);

		Files.createDirectories(dataDir);
		Path db = dataDir.resolve(database);

		Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + db.toString());
		System.out.println("Database opened: " + db);

		// for debugging
		jdbi.configure(Extensions.class, s -> s.setAllowProxy(true));

		jdbi.installPlugin(new SqlObjectPlugin());
		jdbi.installPlugin(new BosonPlugin());
		jdbi.setSqlLogger(new Slf4JSqlLogger());

		jdbi.useHandle((handle) -> {
			Schema dao = handle.attach(Schema.class);
			dao.create();
		});

		return new TestDatabase(jdbi);
	}

	public Jdbi getJdbi() {
		return jdbi;
	}

	public void close(boolean delete) throws IOException {
		jdbi = null;

		FileUtils.deleteFile(dataDir);
		System.out.println("Database folder deleted: " + dataDir);
	}

	public void close() throws IOException {
		close(true);
	}
}
