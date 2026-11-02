package com.gathin.tvhome;

import android.view.KeyEvent;

/** One OK press, shared by the native long click and the activity's fallback timer. */
final class OkHold {
    static final long TIMEOUT_MS = 550;

    private int keyCode = -1;
    private int deviceId;
    private long downTime;
    private boolean handled;
    private boolean canceled;

    static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    /** True only for a new press. Until UP, even repeatCount=0 frames keep the original deadline. */
    boolean down(int code, int device, long time, int repeatCount) {
        if (keyCode != -1) {
            if (!canceled || (matches(code, device) && downTime == time)) return false;
        }
        if (repeatCount != 0) return false;   // no initial DOWN, e.g. Home resumed during a hold
        keyCode = code;
        deviceId = device;
        downTime = time;
        handled = false;
        canceled = false;
        return true;
    }

    boolean matches(int code, int device) {
        return keyCode == code && deviceId == device;
    }

    boolean due(long eventTime) {
        return keyCode != -1 && !handled && eventTime - downTime >= TIMEOUT_MS;
    }

    /** Claim this press once. A touch long click has no key DOWN and must not consume a later OK. */
    boolean fire() {
        if (keyCode == -1 || handled) return false;
        handled = true;
        return true;
    }

    boolean handled() {
        return handled;
    }

    /** Consume only this button's release when a long click or cancellation already handled it. */
    boolean up(int code, int device, boolean isCanceled) {
        if (!matches(code, device)) return false;
        boolean consume = handled || isCanceled;
        keyCode = -1;
        handled = false;
        canceled = false;
        return consume;
    }

    /** Keep ownership of a late UP, but allow a fresh DOWN after focus returns if that UP was lost. */
    void cancel() {
        if (keyCode == -1) return;
        handled = true;
        canceled = true;
    }
}
