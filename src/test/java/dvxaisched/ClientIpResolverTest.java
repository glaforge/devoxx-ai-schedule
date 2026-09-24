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

package dvxaisched;

import dvxaisched.config.ClientIpResolver;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    @Test
    void testSingleIpInXForwardedFor() {
        HttpRequest<?> req = HttpRequest.GET("/test")
            .header("X-Forwarded-For", "203.0.113.195");

        assertEquals("203.0.113.195", ClientIpResolver.resolveClientIp(req));
    }

    @Test
    void testMultipleIpsInXForwardedForExtractsConnectingClientIp() {
        // Cloud Run appends client IP to the end of any incoming X-Forwarded-For chain
        HttpRequest<?> req = HttpRequest.GET("/test")
            .header("X-Forwarded-For", "192.168.1.1, 10.0.0.2, 198.51.100.42");

        assertEquals("198.51.100.42", ClientIpResolver.resolveClientIp(req));
    }

    @Test
    void testXRealIpFallback() {
        HttpRequest<?> req = HttpRequest.GET("/test")
            .header("X-Real-IP", "198.51.100.99");

        assertEquals("198.51.100.99", ClientIpResolver.resolveClientIp(req));
    }

    @Test
    void testSessionIdFromHeader() {
        HttpRequest<?> req = HttpRequest.GET("/test")
            .header("X-Session-ID", "sess-abc-123_xyz");

        assertEquals("sess-abc-123_xyz", ClientIpResolver.resolveSessionId(req));
    }

    @Test
    void testSessionIdSanitization() {
        HttpRequest<?> req = HttpRequest.GET("/test")
            .header("X-Session-ID", "sess<script>alert(1)</script>!");

        assertEquals("sessscriptalert1script", ClientIpResolver.resolveSessionId(req));
    }

    @Test
    void testSessionIdFromQueryParam() {
        HttpRequest<?> req = HttpRequest.GET("/test?sessionId=custom-session-token");

        assertEquals("custom-session-token", ClientIpResolver.resolveSessionId(req));
    }

    @Test
    void testSessionIdFallbackToClientIp() {
        HttpRequest<?> req = HttpRequest.GET("/test")
            .header("X-Forwarded-For", "203.0.113.50");

        assertEquals("203.0.113.50", ClientIpResolver.resolveSessionId(req));
    }
}
