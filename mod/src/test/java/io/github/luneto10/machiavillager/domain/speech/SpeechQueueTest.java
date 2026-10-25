package io.github.luneto10.machiavillager.domain.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.meeting.ReadingTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpeechQueueTest {

    @Test
    void firstLine_comesOnTheFirstTick() {
        SpeechQueue queue = new SpeechQueue(List.of("one", "two"));

        assertEquals(Optional.of("one"), queue.tick());
        assertEquals(1, queue.spoken());
        assertEquals(2, queue.total());
    }

    @Test
    void nextLine_waitsForTheReadingTimeOfThePreviousOne() {
        String first = "hi";
        SpeechQueue queue = new SpeechQueue(List.of(first, "next"));
        queue.tick();

        // The reading time of the first line must pass before the second one may start.
        int wait = ReadingTime.ticksFor(first);
        for (int i = 0; i < wait; i++) {
            assertEquals(Optional.empty(), queue.tick());
        }
        assertEquals(Optional.of("next"), queue.tick());
    }

    @Test
    void finished_onlyAfterTheLastLineWasReadAndTheQueueIsEmpty() {
        SpeechQueue queue = new SpeechQueue(List.of("a"));
        assertFalse(queue.finished());

        queue.tick();
        assertFalse(queue.finished());

        // Drain the reading time of the single line.
        for (int i = 0; i < ReadingTime.ticksFor("a"); i++) {
            queue.tick();
        }
        assertTrue(queue.finished());
    }

    @Test
    void emptyList_isFinishedAtOnce() {
        assertTrue(new SpeechQueue(List.of()).finished());
    }

    @Test
    void interrupt_dropsTheRemainingLines_butTheCurrentOneIsStillRead() {
        SpeechQueue queue = new SpeechQueue(List.of("first", "second", "third"));
        queue.tick();

        queue.interrupt();

        assertFalse(queue.finished(), "the line on screen keeps its reading time");
        for (int i = 0; i < ReadingTime.ticksFor("first"); i++) {
            assertEquals(Optional.empty(), queue.tick());
        }
        assertTrue(queue.finished());
        assertEquals(1, queue.spoken());
        assertEquals(3, queue.total());
    }

    @Test
    void copiesTheLines_so_laterChangesDoNotMatter() {
        List<String> source = new java.util.ArrayList<>(List.of("a", "b"));
        SpeechQueue queue = new SpeechQueue(source);
        source.clear();

        assertEquals(Optional.of("a"), queue.tick());
        assertEquals(2, queue.total());
    }
}
