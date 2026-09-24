/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dvxaisched.config;

import io.micronaut.http.HttpRequest;

import java.net.InetSocketAddress;

public final class ClientIpResolver {

    private ClientIpResolver() {}

    /**
     * Resolves the real client IP address behind Google Cloud Run / Google Front End (GFE).
     * Cloud Run appends the verified connecting IP as the last entry in the comma-separated X-Forwarded-For header.
     */
    public static String resolveClientIp(HttpRequest<?> request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String xForwardedFor = request.getHeaders().get("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            String lastIp = parts[parts.length - 1].trim();
            if (!lastIp.isBlank()) {
                return sanitizeIp(lastIp);
            }
        }

        String xRealIp = request.getHeaders().get("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return sanitizeIp(xRealIp.trim());
        }

        try {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return remoteAddress.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }

    /**
     * Resolves an anonymous session token or falls back to the client IP.
     * Checks X-Session-ID header first, then the 'sessionId' query parameter.
     */
    public static String resolveSessionId(HttpRequest<?> request) {
        if (request == null) {
            return "anonymous";
        }

        String sessionHeader = request.getHeaders().get("X-Session-ID");
        if (sessionHeader != null && !sessionHeader.isBlank()) {
            return sanitizeToken(sessionHeader);
        }

        var sessionParam = request.getParameters().get("sessionId");
        if (sessionParam != null && !sessionParam.isBlank()) {
            return sanitizeToken(sessionParam);
        }

        return resolveClientIp(request);
    }

    private static String sanitizeIp(String ip) {
        // Support IPv4, IPv6, and remove port if present
        String clean = ip.trim();
        if (clean.startsWith("[") && clean.contains("]")) {
            clean = clean.substring(1, clean.indexOf("]"));
        } else if (clean.contains(":") && clean.indexOf(":") == clean.lastIndexOf(":")) {
            // IPv4 with port (e.g. 1.2.3.4:5678)
            clean = clean.substring(0, clean.indexOf(":"));
        }
        return clean.replaceAll("[^a-fA-F0-9.:]", "");
    }

    private static String sanitizeToken(String raw) {
        String clean = raw.trim().replaceAll("[^a-zA-Z0-9_-]", "");
        if (clean.length() > 64) {
            clean = clean.substring(0, 64);
        }
        return clean.isBlank() ? "anonymous" : clean;
    }
}
