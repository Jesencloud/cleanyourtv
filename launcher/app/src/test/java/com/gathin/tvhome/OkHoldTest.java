package com.gathin.tvhome;

import android.view.KeyEvent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Key sequences from normal remotes, repeated IR DOWN frames, touch and interrupted holds. */
public class OkHoldTest {
    private static final int OK = KeyEvent.KEYCODE_DPAD_CENTER;
    private static final int REMOTE = 3;

    @Test public void aShortPressAndTheNextTapStillReachTheTile() {
        OkHold hold = new OkHold();
        assertTrue(hold.down(OK, REMOTE, 1000, 0));
        assertFalse(hold.due(1200));
        assertFalse(hold.up(OK, REMOTE, false));
        assertFalse(hold.fire());   // an already queued timer cannot act after UP
        assertTrue(hold.down(OK, REMOTE, 1300, 0));
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void repeatsKeepTheFirstDownDeadline() {
        OkHold hold = new OkHold();
        assertTrue(hold.down(OK, REMOTE, 1000, 0));
        assertFalse(hold.down(OK, REMOTE, 1000, 1));
        assertFalse(hold.down(OK, REMOTE, 1000, 2));
        assertFalse(hold.due(1549));
        assertTrue(hold.due(1550));
        assertTrue(hold.fire());
        assertTrue(hold.up(OK, REMOTE, false));
    }

    @Test public void zeroCountRepeatsWithNewTimestampsDoNotRestartOrRearmTheHold() {
        OkHold hold = new OkHold();
        assertTrue(hold.down(OK, REMOTE, 1000, 0));
        assertFalse(hold.down(OK, REMOTE, 1200, 0));
        assertFalse(hold.down(OK, REMOTE, 1400, 0));
        assertTrue(hold.due(1550));
        assertTrue(hold.fire());
        assertFalse(hold.down(OK, REMOTE, 1600, 0));
        assertTrue(hold.handled());
        assertFalse(hold.fire());
        assertTrue(hold.up(OK, REMOTE, false));
        assertTrue(hold.down(OK, REMOTE, 2000, 0));
        assertFalse(hold.up(OK, REMOTE, false));   // one OK can now drop the tile
    }

    @Test public void aNativeLongClickWinsOnceAndOnlyConsumesItsOwnRelease() {
        OkHold hold = new OkHold();
        hold.down(OK, REMOTE, 1000, 0);
        assertTrue(hold.fire());   // View's callback, before the fallback deadline
        assertFalse(hold.due(1550));
        assertFalse(hold.fire());
        assertTrue(hold.up(OK, REMOTE, false));
        assertTrue(hold.down(OK, REMOTE, 2000, 0));
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void aFallbackLongClickWinsOnceAndLeavesTheNextOkAvailable() {
        OkHold hold = new OkHold();
        hold.down(OK, REMOTE, 1000, 0);
        assertTrue(hold.due(1550));
        assertTrue(hold.fire());
        assertFalse(hold.fire());   // a late View callback cannot claim the key again
        assertTrue(hold.up(OK, REMOTE, false));
        assertTrue(hold.down(OK, REMOTE, 2000, 0));
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void touchLongClickDoesNotConsumeTheFirstFollowingOk() {
        OkHold hold = new OkHold();
        assertFalse(hold.fire());   // touch has no preceding key DOWN
        assertFalse(hold.handled());
        assertTrue(hold.down(OK, REMOTE, 1000, 0));
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void otherButtonsAndDevicesCannotFinishTheHeldPress() {
        OkHold hold = new OkHold();
        hold.down(OK, REMOTE, 1000, 0);
        assertFalse(hold.down(KeyEvent.KEYCODE_ENTER, REMOTE, 1100, 0));
        assertFalse(hold.up(KeyEvent.KEYCODE_ENTER, REMOTE, false));
        assertFalse(hold.down(OK, REMOTE + 1, 1200, 0));
        assertFalse(hold.up(OK, REMOTE + 1, false));
        assertTrue(hold.due(1550));
        assertTrue(hold.fire());
        assertFalse(hold.up(OK, REMOTE + 1, false));
        assertTrue(hold.up(OK, REMOTE, false));
    }

    @Test public void canceledReleaseDoesNotClickAndClearsThePendingTimer() {
        OkHold hold = new OkHold();
        hold.down(OK, REMOTE, 1000, 0);
        assertTrue(hold.up(OK, REMOTE, true));
        assertFalse(hold.due(1550));
        assertFalse(hold.fire());
        assertTrue(hold.down(OK, REMOTE, 2000, 0));
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void losingWindowFocusCancelsTheHoldAndConsumesItsLateRelease() {
        OkHold hold = new OkHold();
        hold.down(OK, REMOTE, 1000, 0);
        hold.cancel();
        assertFalse(hold.due(1550));
        assertFalse(hold.fire());
        assertFalse(hold.down(OK, REMOTE, 1000, 1));
        assertFalse(hold.down(OK, REMOTE, 1000, 0));
        assertTrue(hold.handled());
        assertTrue(hold.up(OK, REMOTE, false));
    }

    @Test public void aFreshPressWorksAfterFocusReturnsWithoutTheOldRelease() {
        OkHold hold = new OkHold();
        hold.down(OK, REMOTE, 1000, 0);
        hold.fire();
        hold.cancel();
        assertTrue(hold.down(OK, REMOTE, 2000, 0));
        assertFalse(hold.handled());
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void cancelWithoutAKeyDoesNotConsumeALaterOk() {
        OkHold hold = new OkHold();
        hold.cancel();
        assertTrue(hold.down(OK, REMOTE, 1000, 0));
        assertFalse(hold.up(OK, REMOTE, false));
    }

    @Test public void aRepeatWithoutAnInitialDownDoesNotStartAHold() {
        OkHold hold = new OkHold();
        assertFalse(hold.down(OK, REMOTE, 1000, 1));
        assertFalse(hold.due(1550));
        assertFalse(hold.fire());
    }

    @Test public void remoteEnterAndGamepadAUseTheSameOkRules() {
        for (int code : new int[] {OK, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A}) {
            assertTrue(OkHold.isOkKey(code));
            OkHold hold = new OkHold();
            hold.down(code, REMOTE, 1000, 0);
            assertTrue(hold.fire());
            assertTrue(hold.up(code, REMOTE, false));
            hold.down(code, REMOTE, 2000, 0);
            assertFalse(hold.up(code, REMOTE, false));
        }
        assertFalse(OkHold.isOkKey(KeyEvent.KEYCODE_MENU));
        assertFalse(OkHold.isOkKey(KeyEvent.KEYCODE_BACK));
        assertFalse(OkHold.isOkKey(KeyEvent.KEYCODE_DPAD_DOWN));
    }
}
