package com.orodent.ecos.launcher.update;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Public release assets only; no GitHub credential is needed on user machines. */
public final class GithubTransfer implements UpdateManager.Transfer {
    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    @Override
    public void download(String url, Path destination, long limit) throws Exception {
        URI uri = URI.create(url);
        if (!url.startsWith(UpdateManager.REPOSITORY + "/releases/"))
            throw new IllegalArgumentException("URL release inatteso");
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(limit > UpdateCatalog.MAX_CATALOG ? 300 : 10))
                        .header("User-Agent", "Orodent-EcoS-Updater/2")
                        .GET()
                        .build();
        HttpResponse<Path> response =
                client.send(request, info -> new LimitedFile(destination, limit));
        if (response.statusCode() != 200
                || !"https".equals(response.uri().getScheme())
                || !Set.of(
                                "github.com",
                                "release-assets.githubusercontent.com",
                                "objects.githubusercontent.com")
                        .contains(response.uri().getHost())) {
            Files.deleteIfExists(destination);
            throw new java.io.IOException(
                    "Download release non disponibile: HTTP " + response.statusCode());
        }
    }

    private static final class LimitedFile implements HttpResponse.BodySubscriber<Path> {
        private final CompletableFuture<Path> result = new CompletableFuture<>();
        private final Path destination;
        private final long limit;
        private long size;
        private Flow.Subscription subscription;
        private OutputStream output;

        LimitedFile(Path destination, long limit) {
            this.destination = destination;
            this.limit = limit;
        }

        @Override
        public CompletionStage<Path> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            try {
                output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW);
                subscription.request(1);
            } catch (Exception failure) {
                onError(failure);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            try {
                for (ByteBuffer item : items) {
                    size += item.remaining();
                    if (size > limit) throw new java.io.IOException("Download troppo grande");
                    byte[] buffer = new byte[Math.min(65536, item.remaining())];
                    while (item.hasRemaining()) {
                        int count = Math.min(buffer.length, item.remaining());
                        item.get(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                }
                subscription.request(1);
            } catch (Exception failure) {
                onError(failure);
            }
        }

        @Override
        public void onError(Throwable failure) {
            if (subscription != null) subscription.cancel();
            try {
                if (output != null) output.close();
            } catch (Exception ignored) {
            }
            result.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            try {
                output.close();
                result.complete(destination);
            } catch (Exception failure) {
                result.completeExceptionally(failure);
            }
        }
    }
}
