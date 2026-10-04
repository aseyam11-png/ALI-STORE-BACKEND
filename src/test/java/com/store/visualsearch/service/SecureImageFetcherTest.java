package com.store.visualsearch.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureImageFetcherTest {
    private VisualSearchProperties properties() {
        return new VisualSearchProperties(10*1024*1024,200,6,2500,10*1024*1024);
    }

    @Test void rejectsNonHttpsImageUrlsBeforeNetworkAccess() {
        SecureImageFetcher fetcher=new SecureImageFetcher(properties());
        assertThrows(RuntimeException.class,()->fetcher.fetch("http://example.com/image.jpg"));
    }

    @Test void rejectsLocalImageHostsBeforeNetworkAccess() {
        SecureImageFetcher fetcher=new SecureImageFetcher(properties());
        assertThrows(RuntimeException.class,()->fetcher.fetch("https://127.0.0.1/image.jpg"));
    }
}
