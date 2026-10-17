import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Emulator-only system routing check; compiled separately and never packaged in DiPlay. */
public final class LegacyMediaSessionProbe {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        if (Build.VERSION.SDK_INT != 23) throw new AssertionError("Requires Android 6");
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Context context = system.createPackageContext("com.android.shell", 0);
        HandlerThread worker = new HandlerThread("media-key-probe");
        worker.start();
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch keys = new CountDownLatch(3);
        MediaSession session = new MediaSession(context, "DiPlay legacy routing probe");
        try {
            session.setCallback(new MediaSession.Callback() {
                @Override public boolean onMediaButtonEvent(Intent intent) {
                    KeyEvent key = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (key != null && key.getAction() == KeyEvent.ACTION_DOWN && key.getRepeatCount() == 0) {
                        received.add(key.getKeyCode());
                        keys.countDown();
                    }
                    return true;
                }
            }, new Handler(worker.getLooper()));
            session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
            session.setActive(true);
            if (session.getController().getFlags() != 0) throw new AssertionError("Unexpected default flags");
            System.out.println("media-key-probe NEGATIVE_READY flags=0");
            File proceed = new File(args[0]);
            long deadline = android.os.SystemClock.uptimeMillis() + 15000;
            while (!proceed.exists() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            if (!proceed.exists()) throw new AssertionError("Probe coordinator timed out");
            if (!received.isEmpty()) throw new AssertionError("flags=0 unexpectedly received keys: " + received);
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            System.out.println("media-key-probe POSITIVE_READY flags=" + session.getController().getFlags());
            if (!keys.await(15, TimeUnit.SECONDS)) throw new AssertionError("Missing keys: " + received);
            Thread.sleep(250);
            if (!received.equals(Arrays.asList(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))) throw new AssertionError("Wrong or duplicate keys: " + received);
            System.out.println("media-key-probe PASS sdk=23 noFlagsReceived=0 flags=3 next=1 previous=1 toggle=1");
        } finally {
            session.release();
            worker.quitSafely();
        }
        System.exit(0);
    }
}
