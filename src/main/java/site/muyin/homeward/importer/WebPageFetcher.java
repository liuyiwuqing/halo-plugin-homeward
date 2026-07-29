package site.muyin.homeward.importer;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface WebPageFetcher {

    Mono<FetchedWebPage> fetch(String sourceUrl);
}
