package com.harshshah.matchingengine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PublicDemoGuardTest {
    @Test
    void budgetRecoversAfterAFullWindow() {
        var clock = new AtomicLong(0);
        var budget = new PublicDemoGuard.Budget(2, clock::get);
        assertTrue(budget.allow());
        assertTrue(budget.allow());
        assertFalse(budget.allow());
        clock.set(60_000_000_000L);
        assertTrue(budget.allow());
    }

    @Test
    void sharedWriteLimitDoesNotBlockHealthOrReads() throws Exception {
        var guard = new PublicDemoGuard();
        for (int i = 0; i < 120; i++) {
            var response = new MockHttpServletResponse();
            guard.doFilter(new MockHttpServletRequest("POST", "/api/orders"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        var limited = new MockHttpServletResponse();
        guard.doFilter(new MockHttpServletRequest("POST", "/api/orders"), limited, new MockFilterChain());
        assertEquals(429, limited.getStatus());
        assertEquals("60", limited.getHeader("Retry-After"));
        for (String path : new String[]{"/api/book/DEMO", "/actuator/health"}) {
            var response = new MockHttpServletResponse();
            guard.doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
            assertNotNull(response.getHeader("Content-Security-Policy"));
        }
    }

    @Test
    void oversizedKnownBodyIsRejectedBeforeItReachesController() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(new byte[16385]);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new PublicDemoGuard().doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }
}
