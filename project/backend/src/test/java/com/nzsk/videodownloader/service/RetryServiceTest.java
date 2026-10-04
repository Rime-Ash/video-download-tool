package com.nzsk.videodownloader.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryServiceTest {
    @Test
    void retriesWithExponentialDelays() throws Exception {
        ArrayList<Long> delays = new ArrayList<>();
        int[] attempts = {0};
        RetryService service = new RetryService(3, delays::add);

        String result = service.execute(attempt -> {
            attempts[0]++;
            if (attempt < 3) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts[0]);
        assertEquals(java.util.List.of(1_000L, 2_000L), delays);
    }
}
