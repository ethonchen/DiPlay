package com.diplay.mediakeyprobe;

import android.app.Activity;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;

/** Emulator-only APK; compiled separately and never packaged in DiPlay. */
public final class LegacyMediaSessionProbe extends Activity {
    private MediaSession session;
    private int next;
    private int previous;
    private int toggle;
    private int presses;

    @Override @SuppressWarnings("deprecation")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        final int flags = getIntent().getIntExtra("flags", 0);
        session = new MediaSession(this, "DiPlay legacy routing probe");
        session.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent key = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (key != null && key.getAction() == KeyEvent.ACTION_DOWN && key.getRepeatCount() == 0) {
                    presses++;
                    if (key.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT) next++;
                    if (key.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS) previous++;
                    if (key.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) toggle++;
                    Log.i("DiPlayMediaProbe", "KEY flags=" + flags + " code=" + key.getKeyCode() +
                        " next=" + next + " previous=" + previous + " toggle=" + toggle + " presses=" + presses);
                }
                return true;
            }
        }, new Handler(getMainLooper()));
        session.setFlags(flags);
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_PLAY_PAUSE)
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
        session.setActive(true);
        Log.i("DiPlayMediaProbe", "READY flags=" + session.getController().getFlags());
    }

    @Override public void onDestroy() {
        if (session != null) session.release();
        super.onDestroy();
    }
}
