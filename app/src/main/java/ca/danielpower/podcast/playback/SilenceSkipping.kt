package ca.danielpower.podcast.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import java.nio.ByteBuffer

/**
 * Builds the audio sink with our own silence skipper in place of ExoPlayer's default one, so the
 * longest pause to keep can be changed while the player is running.
 */
@OptIn(UnstableApi::class)
class PodcastRenderersFactory(context: Context, private val silence: AdjustableSilenceSkipper) :
    DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessorChain(Chain(silence))
        .build()

    /** The same as ExoPlayer's default chain: silence skipping, then Sonic for speed. */
    private class Chain(private val silence: AdjustableSilenceSkipper) : AudioProcessorChain {
        private val sonic = SonicAudioProcessor()

        override fun getAudioProcessors(): Array<AudioProcessor> = arrayOf(silence, sonic)

        override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
            sonic.setSpeed(playbackParameters.speed)
            sonic.setPitch(playbackParameters.pitch)
            return playbackParameters
        }

        override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
            silence.setEnabled(skipSilenceEnabled)
            return skipSilenceEnabled
        }

        override fun getMediaDuration(playoutDuration: Long): Long =
            if (sonic.isActive) sonic.getMediaDuration(playoutDuration) else playoutDuration

        override fun getSkippedOutputFrameCount(): Long = silence.skippedFrames
    }
}

/**
 * Cuts every pause longer than [keepMs] down to [keepMs], leaving shorter ones alone, so speech
 * keeps its rhythm but dead air goes. The kept part fades out and back in, as in ExoPlayer.
 *
 * ExoPlayer's processor fixes its lengths when it's built, so this wraps one and builds a new one
 * at the next flush (a seek, or the next episode) after [keepMs] changes.
 */
@OptIn(UnstableApi::class)
class AdjustableSilenceSkipper(keepMs: Int) : AudioProcessor {
    /** Written from the main thread, read on the playback thread. */
    @Volatile var keepMs: Int = keepMs

    private var builtForMs = keepMs
    private var inner = build(keepMs)
    private var enabled = false
    private var format = AudioFormat.NOT_SET

    val skippedFrames: Long get() = inner.skippedFrames

    /** Silence skipped before the last flush; ExoPlayer's count starts again at each one. */
    @Volatile private var bankedUs = 0L

    /**
     * All the silence skipped since the player was built, in media time (divide by the speed for
     * real time). Read from the main thread, so it can be off by one buffer around a flush.
     */
    val skippedUs: Long get() = bankedUs + framesToUs(inner.skippedFrames)

    private fun framesToUs(frames: Long): Long =
        if (format.sampleRate > 0) frames * 1_000_000L / format.sampleRate else 0

    private fun bank() {
        bankedUs += framesToUs(inner.skippedFrames)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        inner.setEnabled(enabled)
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        format = inputAudioFormat
        return inner.configure(inputAudioFormat)
    }

    override fun isActive(): Boolean = inner.isActive
    override fun queueInput(inputBuffer: ByteBuffer) = inner.queueInput(inputBuffer)
    override fun queueEndOfStream() = inner.queueEndOfStream()
    override fun getOutput(): ByteBuffer = inner.output
    override fun isEnded(): Boolean = inner.isEnded

    override fun flush() {
        bank()
        val keep = keepMs
        if (keep != builtForMs) {
            inner = build(keep)
            builtForMs = keep
            inner.setEnabled(enabled)
            if (format != AudioFormat.NOT_SET) inner.configure(format)
        }
        inner.flush()
    }

    override fun reset() {
        bank()
        inner.reset()
        // Resetting turns ExoPlayer's processor off until the sink enables it again.
        enabled = false
        format = AudioFormat.NOT_SET
    }

    private fun build(keepMs: Int): SilenceSkippingAudioProcessor {
        val keepUs = keepMs * 1000L
        return SilenceSkippingAudioProcessor(
            // Pauses shorter than this pass through untouched.
            /* minimumSilenceDurationUs = */ keepUs,
            // Keep all of a pause up to the maximum, and none past it.
            /* silenceRetentionRatio = */ 1f,
            /* maxSilenceToKeepDurationUs = */ keepUs,
            SilenceSkippingAudioProcessor.DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE,
            SilenceSkippingAudioProcessor.DEFAULT_SILENCE_THRESHOLD_LEVEL,
        )
    }
}
