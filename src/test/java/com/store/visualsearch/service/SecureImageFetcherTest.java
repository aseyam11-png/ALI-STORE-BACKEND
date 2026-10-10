package com.store.visualsearch.service;

import com.store.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureImageFetcherTest {
    private VisualSearchProperties properties() {
        return new VisualSearchProperties(
                10 * 1024 * 1024, 200, 6, 2500, 10 * 1024 * 1024);
    }

    @Test
    void rejectsNonHttpsImageUrlsBeforeNetworkAccess() {
        SecureImageFetcher fetcher = new SecureImageFetcher(properties());
        assertThrows(BusinessException.class,
                () -> fetcher.fetch("http://example.com/image.jpg"));
    }

    @Test
    void rejectsUrlsWithoutASchemeAsClientInputErrors() {
        SecureImageFetcher fetcher = new SecureImageFetcher(properties());
        assertThrows(BusinessException.class,
                () -> fetcher.fetch("example.com/image.jpg"));
    }

    @Test
    void rejectsLocalImageHostsBeforeNetworkAccess() {
        SecureImageFetcher fetcher = new SecureImageFetcher(properties());
        assertThrows(BusinessException.class,
                () -> fetcher.fetch("https://127.0.0.1/image.jpg"));
    }

    @Test
    void rejectsCarrierGradeNatAddressSpaceBeforeNetworkAccess() {
        SecureImageFetcher fetcher = new SecureImageFetcher(properties());
        assertThrows(BusinessException.class,
                () -> fetcher.fetch("https://100.64.0.1/image.jpg"));
    }

    @Test
    void rejectsIpv4MappedLoopbackIpv6BeforeNetworkAccess() {
        SecureImageFetcher fetcher = new SecureImageFetcher(properties());
        assertThrows(BusinessException.class,
                () -> fetcher.fetch("https://[::ffff:127.0.0.1]/image.jpg"));
    }
}
