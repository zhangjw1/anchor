package com.anchor.api.support;

import com.anchor.common.chain.AnchorSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderWhenNotTrusted() {
        AnchorSettings settings = new AnchorSettings();
        settings.getHttp().setTrustForwardedHeaders(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        assertEquals("10.0.0.1", new ClientIpResolver(settings).resolve(request));
    }

    @Test
    void usesFirstForwardedIpWhenTrusted() {
        AnchorSettings settings = new AnchorSettings();
        settings.getHttp().setTrustForwardedHeaders(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        assertEquals("1.2.3.4", new ClientIpResolver(settings).resolve(request));
    }
}
