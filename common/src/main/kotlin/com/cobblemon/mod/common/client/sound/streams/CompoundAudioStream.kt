package com.cobblemon.mod.common.client.sound.streams

import net.minecraft.client.sounds.AudioStream
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat

/**
 * An [AudioStream] composed of multiple streams stitched together.
 *
 * Bytes of audio are read according to the active stream's implementation of [AudioStream.read].
 *
 * @param streams The [AudioStream]s to chain sequentially.
 *
 * @author Segfault Guy
 * @since October 19th, 2025
 */
class CompoundAudioStream(vararg streams: AudioStream): AudioStream {

    private val streams = streams.iterator()

    /** The [AudioStream] currently playing. */
    private var runningStream: AudioStream = this.streams.next()

    private val format = this.runningStream.format

    override fun getFormat(): AudioFormat? {
        return format
    }

    /** Reads [size] bytes of PCM, from the current [runningStream], for 1 second worth of audio. */
    override fun read(size: Int): ByteBuffer? {
        var buffer = this.runningStream.read(size)
        if (!buffer.hasRemaining() && this.streams.hasNext()) {
            this.runningStream.close()
            this.runningStream = this.streams.next()
            buffer = this.runningStream.read(size)
        }
        return buffer   // it's fine if the buffer is not completely full - the stream will sound seamless
    }

    override fun close() {
        this.runningStream.close()
        while (streams.hasNext()) streams.next().close()
    }
}