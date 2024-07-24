package io.bosonnetwork.messaging;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class VertxTests {
	private static Vertx vertx = Vertx.vertx();

	private Future<URL> attemptServiceCheck(List<URL> urls, int index) {
		if (index >= urls.size())
            return Future.failedFuture("No more candidate URLs to attempt");

		Promise<URL> promise = Promise.promise();

		URL url = urls.get(index);
		WebClientOptions opts = new WebClientOptions()
				.setSsl(url.getProtocol().equals("https"))
				.setDefaultHost(url.getHost())
				.setDefaultPort(url.getPort() > 0 ? url.getPort() : url.getDefaultPort())
				.setProtocolVersion(HttpVersion.HTTP_1_1);

		WebClient webClient = WebClient.create(vertx, opts);
		System.out.printf("Testing %s ...", url);

		webClient.get("/").send()
			.andThen((ar) -> {
				if (ar.succeeded()) {
					HttpResponse<Buffer> res = ar.result();
					if (res.statusCode() == 200) {
						System.out.println("... ok");
						promise.complete(url);
					} else {
						System.out.println("... error");
						promise.fail("HTTP status: " + res.statusCode());
					}
				} else {
					System.out.println("... error");
					attemptServiceCheck(urls, index + 1).onComplete(promise);
				}

				webClient.close();
			});

		return promise.future();
	}

	@Test
	void test1() throws Exception {
		List<URL> urls = List.of(
				new URL("https://bad-url-1.com"),
				new URL("https://bad-url-2.com"),
				new URL("https://bad-url-3.com"),
				new URL("https://bing.com"));

		URL url = attemptServiceCheck(urls, 0).toCompletionStage().toCompletableFuture().get();
		System.out.println("==>> " + url);
	}

}
