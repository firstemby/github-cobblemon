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
    private val bufferCache = mutableListOf<ByteBuffer>()  // cache size: 4 buffers, each of size 176400 (70kb of cache) which is fine

    /** The OGG Vorbis. This is encoded audio. */
    private lateinit var encodedStream: BufferedInputStream

    /** The Vorbis decoder. This decodes PCM from the [encodedStream] on calls to [AudioStream.read]. */
    private lateinit var decoderStream: AudioStream

    /** The starting point of the loop in BYTES */
    private var loopStart by Delegates.notNull<Int>()

    /** The ending point of the loop in BYTES */
    private var loopEnd by Delegates.notNull<Int>()

    /** Whether this is the first pass of the [encodedStream] and we haven't looped yet. */
    private var firstPass = true

    /** Whether [decoderStream] has been rewinded to [loopStart]. */
    private var loopPrepared = false

    /** The PCM (bytes) that have been read from the decoded stream. */
    private var runningRead = 0

    private var bufferCounter = 0

    init {
        this.encodedStream = BufferedInputStream(primaryInputStream)
        this.encodedStream.mark(Int.Companion.MAX_VALUE)
        this.buildDecoderStream()

        this.loopStart = this.samplesToBytes(loopStart)
        this.loopEnd = this.samplesToBytes(loopEnd)

        // TODO this is for testing, remove
        this.decoderStream.read(this.loopStart);
        this.runningRead = this.loopStart

        this.loopPrepared = true
    }

    override fun getFormat(): AudioFormat? {
        return this.decoderStream.format
    }

    /** Reads 1 second worth of PCM (bytes) for an OpenAL buffer (this will typically be 176400 bytes for stereo audio formats) */
    override fun read(size: Int): ByteBuffer? {

        var buffer: ByteBuffer? = null
        if (!this.loopPrepared) {
            Cobblemon.LOGGER.error("LOOP NOT PREPARED")
        }

        // if we reach the end loop point
        if (loopEnd > 0 && (this.runningRead + size) >= this.loopEnd) {
            buffer = this.getCachedBuffer() ?: this.decoderStream.read(this.loopEnd - this.runningRead)
            this.cacheBuffer(buffer)
            this.resetLoop()
        }
        // standard read
        else {
            buffer = this.getCachedBuffer() ?: this.decoderStream.read(size);

            // if loopEnd is 0 and the entire stream is read, or exaggerated and the stream is exhausted before reaching the point
            if (!buffer.hasRemaining()) {
                this.resetLoop()
            }
            // standard read
            else {
                this.cacheBuffer(buffer)
                this.runningRead += size;
            }
        }

        return buffer
    }

    private fun getCachedBuffer(): ByteBuffer? {
        if (CACHE_LIMIT < 1 || this.bufferCounter >= CACHE_LIMIT || this.firstPass) return null
        return this.bufferCache.get(this.bufferCounter++)
    }

    private fun cacheBuffer(buffer: ByteBuffer) {
        // only cache up to the limit and only cache on the first pass
        if (CACHE_LIMIT < 1 || this.bufferCache.size >= CACHE_LIMIT || !this.firstPass) return
        this.bufferCache.add(buffer)
    }

    /** Converts PCM samples to bytes. */
    private fun samplesToBytes(sampleAmount: Int): Int {
        val format = this.format ?: return 0
        return sampleAmount * format.getSampleSizeInBits() / 8 * format.getChannels()
    }

    /** Rewind [decoderStream] to start from [loopStart]. */
    private fun resetLoop() {
        // reset state info
        this.loopPrepared = false
        this.runningRead = this.loopStart
        this.bufferCounter = 0

        /*
         JOrbis does not support seeking. Instead, we'll replicate seeking to the loop start by decoding PCM in bulk from
         the beginning of the OGG Vorbis up until we reach the desired sample offset. Then for playing the loop, continue
         streaming decoded PCM into OpenAL buffers.
         */
        this.decoderStream.close()
        this.encodedStream.reset()
        this.buildDecoderStream()

        /*
         After the first pass, encodedStream will have the entire Vorbis OGG cached, so there won't be any subsequent reads
         from disk. However, there's still some milliseconds of latency in decoding a lot of PCM. This needs to be done
         asynchronously, or else the client will freeze during the loop reset. In the UNLIKELY case that the decoder stream
         is not rewinded by the time the Channel needs to read a new buffer - it will be read from bufferCache.
         */
        CompletableFuture.supplyAsync<Any?>({
            try {
                decoderStream.read(this.loopStart)
                this.loopPrepared = true
                return@supplyAsync null
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }, Util.nonCriticalIoPool())
    }

    private fun buildDecoderStream() {
        this.decoderStream = provider.create(LoopingAudioStream.NoCloseBuffer(this.encodedStream))
    }

    override fun close() {
        this.decoderStream.close()
        this.encodedStream.close()  // closes the InputStream used
    }
}