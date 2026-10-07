package com.akansh.sharex.server;
import org.junit.Test;
import static org.junit.Assert.*;
public class SharingSessionTest {
    @Test public void durationResetsOnEveryStartAndStopsAtZero() {
        try {
            SharingSession.stop();
            assertFalse(SharingSession.isRunning());
            assertEquals(0, SharingSession.elapsed(1000));
            SharingSession.start(1000);
            assertTrue(SharingSession.isRunning());
            assertEquals(2500, SharingSession.elapsed(3500));
            SharingSession.start(4000);
            assertEquals(100, SharingSession.elapsed(4100));
            SharingSession.stop();
            assertEquals(0, SharingSession.elapsed(10000));
        } finally { SharingSession.stop(); }
    }
}
