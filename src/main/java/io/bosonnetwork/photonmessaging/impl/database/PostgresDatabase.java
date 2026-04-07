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
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.photonmessaging.impl.Database;

/**
 * PostgreSQL implementation of Photon Messaging Client database.
 */
public class PostgresDatabase extends Database {
	private static final Logger log = LoggerFactory.getLogger(PostgresDatabase.class);
	public static final String CONNECTION_URI_PREFIX = "postgres://";

	private final String uri;
	private final int poolSize;
	private final String schema;
	private Pool pool;
	private final PostgresSqlDialect dialect = new PostgresSqlDialect();

	public PostgresDatabase(String uri, int poolSize, String schema) {
		this.uri = Objects.requireNonNull(uri, "uri");
		this.poolSize = poolSize > 0 ? poolSize : 5;
		this.schema = schema;
	}

	@Override
	protected Logger getLogger() {
		return log;
	}

	@Override
	protected void init(Vertx vertx) {
		PgConnectOptions connectOptions = PgConnectOptions.fromUri(uri);
		PoolOptions poolOptions = new PoolOptions().setMaxSize(poolSize);

		this.pool = PgBuilder.pool()
				.with(poolOptions)
				.connectingTo(connectOptions)
				.using(vertx)
				.build();
	}

	@Override
	public SqlClient getClient() {
		return pool;
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
	protected String getSchema() {
		return schema;
	}

	@Override
	public SqlDialect getDialect() {
		return dialect;
	}

	@Override
	protected Path getMigrationPath() {
		URL migrationResource = getClass().getResource("/db/photonmessaging/client/postgres");
		if (migrationResource == null || migrationResource.getPath() == null)
			throw new IllegalStateException("Migration path not exists");

		return Path.of(migrationResource.getPath());
	}

	@Override
	public Future<Void> close() {
		if (pool != null) {
			return pool.close();
		}
		return Future.succeededFuture();
	}
}
