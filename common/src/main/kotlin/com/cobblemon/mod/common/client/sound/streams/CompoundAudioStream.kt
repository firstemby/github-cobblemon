package com.cobblemon.mod.common.client.sound.streams

import net.minecraft.client.sounds.AudioStream
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat
import kotlin.jvm.Throws

/**
 * An [AudioStream] composed of multiple streams stitched together.
 *
 * Bytes of audio are read according to the active stream's implementation of [AudioStream.read].
 *
 * @param streams The [AudioStream]s to chain sequentially. Each stream must share the same [AudioFormat].
 *
 * @author Segfault Guy
 * @since October 19th, 2025
 */
class CompoundAudioStream @Throws(IllegalArgumentException::class) constructor(vararg streams: AudioStream): AudioStream {

    private val streams = streams.iterator()

    /** The [AudioStream] currently playing. */
    private var runningStream: AudioStream = this.streams.next()

    private val format = this.runningStream.format

    override fun getFormat() = format

    init {
        /* Enqueuing buffers of different formats to the same source is not permissible in OpenAL 1.1 standard. */
        streams.filterNot { runningStream.format.matches(it.format) }.takeIf { it.isNotEmpty() }?.let { incompatibleStreams ->
            val message = StringBuilder("Cannot compose CompoundAudioStream from streams of different formats:\n${runningStream.format}")
            incompatibleStreams.forEach {
                message.append("\n${it.format}")
            }
            throw IllegalArgumentException(message.toString())
        }
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