/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl.database;

import java.net.URL;
import java.nio.file.Path;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.database.SqlSafety;
import io.bosonnetwork.photonmessaging.impl.DatabaseStore;
import io.bosonnetwork.utils.FileUtils;

/**
 * PostgreSQL implementation of PhotonMessaging Client database.
 */
public class PostgresDatabase extends DatabaseStore {
	public static final String CONNECTION_URI_PREFIX = "postgresql://";
	private static final int DEFAULT_POOL_SIZE = 4;

	private final String connectionUri;
	private final int poolSize;
	private final @Nullable String schema;
	private final SqlDialect sqlDialect;
	private @Nullable Pool client;
	private static final Logger log = LoggerFactory.getLogger(PostgresDatabase.class);

	public PostgresDatabase(String connectionUri, int poolSize, @Nullable String schema) {
		this.connectionUri = connectionUri;
		this.poolSize = poolSize <= 0 ? DEFAULT_POOL_SIZE : poolSize;
		// Defense-in-depth: the schema is interpolated into "SET search_path TO ..." in
		// prepareConnection(), so re-validate it here even though Configuration already does -
		// this class must not assume its caller validated the identifier.
		this.schema = SqlSafety.validateSchema(schema);
		this.sqlDialect = new PostgresSqlDialect();
	}

	protected PostgresDatabase(String connectionUri) {
		this(connectionUri, 0, null);
	}

	@Override
	protected void init(Vertx vertx) {
		PgConnectOptions connectOptions = PgConnectOptions.fromUri(connectionUri);
		PoolOptions poolOptions = new PoolOptions().setMaxSize(poolSize);
		client = PgBuilder.pool()
				.with(poolOptions)
				.connectingTo(connectOptions)
				.using(vertx)
				.build();
	}

	@Override
	protected Path getMigrationPath() {
		URL migrationResource = getClass().getResource("/db/photonmessaging/client/postgres");
		if (migrationResource == null || migrationResource.getPath() == null)
			throw new IllegalStateException("Migration path not exists");

		try {
			return FileUtils.pathOf(migrationResource);
		} catch (Exception e) {
			throw new IllegalStateException("Migration path error", e);
		}
	}

	@Override
	public @Nullable SqlClient getClient() {
		return client;
	}

	@Override
	protected @Nullable String getSchema() {
		return schema;
	}

	@Override
	public Future<Void> prepareConnection(SqlConnection connection) {
		if (schema == null)
			return Future.succeededFuture();
		else
			return connection.query("SET search_path TO " + schema)
					.execute()
					.mapEmpty();
	}

	@Override
	public SqlDialect getDialect() {
		return sqlDialect;
	}

	@Override
	protected Logger getLogger() {
		return log;
	}
}