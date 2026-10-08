package com.shilapi.xcertplay.media

import android.media.AudioTrack
import android.os.Build

/** Missing on API 23. Unknown is distinct from a measured count of zero. */
internal fun audioTrackUnderrunCount(track: AudioTrack): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) track.underrunCount else null

/** Without a hardware counter, rebuffer only after all written PCM has played. */
internal fun audioBufferStarved(count: Int?, countAtStart: Int, queuedBytes: Long): Boolean =
    if (count != null) count > countAtStart else queuedBytes == 0L
