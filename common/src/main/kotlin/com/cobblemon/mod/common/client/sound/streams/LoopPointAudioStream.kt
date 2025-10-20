package com.cobblemon.mod.common.client.sound.streams

import com.cobblemon.mod.common.Cobblemon
import com.mojang.blaze3d.audio.Channel
import net.minecraft.Util
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.LoopingAudioStream
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import kotlin.properties.Delegates

/**
 * An [AudioStream] divided into an intro substream and a loop substream, determined by loop points provided with the Sound resource.
 *
 * Similar to [LoopingAudioStream], the loop will play indefinitely until stopped.
 *
 * @author Segfault Guy
 * @since October 19th, 2025
 */
class LoopPointAudioStream(
    /** Provider for creating the internal [AudioStream] that decodes the audio. */
    private val provider: LoopingAudioStream.AudioStreamProvider,
    /** The initial [InputStream] of the encoded audio to played. */
    primaryInputStream: InputStream,
    /** The starting point of the main audio loop in SAMPLES. If 0, loop will run until the [loopEnd]. */
    loopStart: Int = 0,
    /** The ending point of the main audio loop in SAMPLES. If 0, loop will run to completion. */
    loopEnd: Int = 0
): AudioStream {

    /** Amount of buffers of decoded PCM, from the start of the loop, to cache. */
    private val CACHE_LIMIT = Channel.QUEUED_BUFFER_COUNT  // CACHE_LIMIT to match the number of buffers pre-read before feeding to OpenAL

    /** Buffers of PCM that start the loop. */
    private val bufferCache = arrayOf<ByteBuffer>()  // cache size: 4 buffers, each of size 176400 (70kb of cache) which is fine

    /** */
    private lateinit var encodedStream: BufferedInputStream

    /** */
    private lateinit var decodedStream: AudioStream

    /** The starting point of the loop in BYTES */
    private var loopStart by Delegates.notNull<Int>()

    /** The ending point of the loop in BYTES */
    private var loopEnd by Delegates.notNull<Int>()

    /** Whether the [decodedStream] has fin exhausted */
    private var firstPass = true

    private var loopPrepared = false

    /** The PCM (bytes) that have been read from the decoded stream. */
    private var runningRead = 0

    init {
        this.encodedStream = BufferedInputStream(primaryInputStream)
        this.encodedStream.mark(Int.Companion.MAX_VALUE)
        this.decodedStream = provider.create(LoopingAudioStream.NoCloseBuffer(this.encodedStream))

        this.loopStart = this.samplesToBytes(loopStart)
        this.loopEnd = this.samplesToBytes(loopEnd)

        // TODO this is for testing, remove
        this.decodedStream.read(this.loopStart);
        this.runningRead = this.loopStart

        this.loopPrepared = true
    }

    override fun getFormat(): AudioFormat? {
        return this.decodedStream.format
    }

    /** Reads 1 second worth of PCM (bytes) for an OpenAL buffer (this will typically be 176400 bytes for stereo audio formats) */
    override fun read(size: Int): ByteBuffer? {

        var buffer: ByteBuffer? = null
        if (!this.loopPrepared) {
            Cobblemon.LOGGER.error("LOOP NOT PREPARED")
        }

        if ((this.runningRead + size) >= this.loopEnd) {
            if (this.loopPrepared) {
                buffer = this.decodedStream.read(this.loopEnd - this.runningRead);

                //
                this.loopPrepared = false
                this.runningRead = this.loopStart
                this.prepareLoop()
            }
        }
        else {
            buffer = this.decodedStream.read(size);
            this.runningRead += size;
        }

        return buffer
    }

    /** Converts PCM samples to bytes */
    private fun samplesToBytes(sampleAmount: Int): Int {
        val format = this.format ?: return 0
        return sampleAmount * format.getSampleSizeInBits() / 8 * format.getChannels()
    }

    /** */
    private fun prepareLoop() {
        //
        this.decodedStream.close()
        this.encodedStream.reset()
        this.decodedStream = provider.create(LoopingAudioStream.NoCloseBuffer(this.encodedStream))

        //
        CompletableFuture.supplyAsync<Any?>({
            try {
                decodedStream.read(this.loopStart)
                this.loopPrepared = true
                return@supplyAsync null
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }, Util.nonCriticalIoPool())
    }

    override fun close() {
        this.decodedStream.close()
        this.encodedStream.close()
    }
}