package com.store.visualsearch.service;

import com.store.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class SecureImageFetcher {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("https");

    private final VisualSearchProperties properties;

    public SecureImageFetcher(VisualSearchProperties properties) {
        this.properties = properties;
    }

    public byte[] fetch(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        try {
            // Reject the entire hostname if any resolved answer is non-public. The
            // transport is still constrained by disabled redirects and byte/time limits.
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isPrivateOrLocal(address)) {
                    throw new BusinessException(
                            "Product image host is not allowed for visual search");
                }
            }

            HttpURLConnection connection =
                    (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(properties.timeoutMs());
            connection.setReadTimeout(properties.timeoutMs());
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "Accept", "image/avif,image/webp,image/png,image/jpeg;q=0.9,*/*;q=0.1");
            connection.setRequestProperty("User-Agent", "ALI-STORE-VisualSearch/1.0");

            try {
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    return null;
                }

                if (connection.getContentLengthLong() > properties.maxBytes()) {
                    return null;
                }

                try (InputStream input = connection.getInputStream()) {
                    return readBounded(input, properties.maxBytes());
                }
            } finally {
                connection.disconnect();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private URI parseAndValidate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException("Product image URL is missing");
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Product image URL is invalid");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new BusinessException(
                    "Product image URL must be a public HTTPS URL");
        }

        return uri;
    }

    private byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    private boolean isPrivateOrLocal(InetAddress address) throws UnknownHostException {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isNonPublicIpv4(bytes);
        }
        if (bytes.length != 16) {
            return true;
        }

        // InetAddress may normalize an IPv4-mapped IPv6 literal to Inet4Address,
        // but explicitly handle the byte form too for providers that preserve it.
        if (isIpv4Mapped(bytes)) {
            return isPrivateOrLocal(InetAddress.getByAddress(
                    Arrays.copyOfRange(bytes, 12, 16)));
        }

        // Reject deprecated IPv4-compatible IPv6 addresses, 6to4, Teredo and
        // documentation-only IPv6 ranges; these are not needed for product images.
        if (hasZeroPrefix(bytes, 12)
                || (bytes[0] == 0x20 && bytes[1] == 0x02)
                || (bytes[0] == 0x20 && bytes[1] == 0x01
                    && bytes[2] == 0x00 && bytes[3] == 0x00)
                || (bytes[0] == 0x20 && bytes[1] == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8)) {
            return true;
        }

        // Only accept IPv6 global-unicast space. This excludes special-use ranges
        // such as unique-local, link-local and well-known NAT64 prefixes.
        return (bytes[0] & 0xe0) != 0x20;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasZeroPrefix(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isNonPublicIpv4(byte[] bytes) {
        int a = bytes[0] & 0xff;
        int b = bytes[1] & 0xff;
        int c = bytes[2] & 0xff;

        return a == 0
                || a == 10
                || a == 127
                || a >= 224
                || (a == 169 && b == 254)
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168)
                || (a == 100 && b >= 64 && b <= 127) // Shared address space (RFC 6598).
                || (a == 192 && b == 0 && c == 0)
                || (a == 192 && b == 0 && c == 2)
                || (a == 192 && b == 88 && c == 99)
                || (a == 198 && (b == 18 || b == 19))
                || (a == 198 && b == 51 && c == 100)
                || (a == 203 && b == 0 && c == 113);
    }
}
