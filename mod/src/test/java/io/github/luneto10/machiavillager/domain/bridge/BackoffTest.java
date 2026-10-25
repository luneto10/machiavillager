package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BackoffTest {

    @Test
    void next_growsByDoublingThenCapsAtThirtySeconds() {
        Backoff backoff = new Backoff();

        assertEquals(Duration.ofSeconds(1), backoff.next());
        assertEquals(Duration.ofSeconds(2), backoff.next());
        assertEquals(Duration.ofSeconds(4), backoff.next());
        assertEquals(Duration.ofSeconds(8), backoff.next());
        assertEquals(Duration.ofSeconds(16), backoff.next());
        assertEquals(Duration.ofSeconds(30), backoff.next());
        assertEquals(Duration.ofSeconds(30), backoff.next());
    }

    @Test
    void reset_startsOverAtOneSecond() {
        Backoff backoff = new Backoff();
        backoff.next();
        backoff.next();

        backoff.reset();

        assertEquals(Duration.ofSeconds(1), backoff.next());
    }
}
