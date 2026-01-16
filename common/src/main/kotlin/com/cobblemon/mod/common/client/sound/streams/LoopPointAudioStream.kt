package com.cobblemon.mod.common.client.sound.streams

import com.cobblemon.mod.common.Cobblemon
import com.mojang.blaze3d.audio.Channel
import net.minecraft.Util
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.LoopingAudioStream
import org.lwjgl.BufferUtils
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
 * @param provider Provider for creating the internal [AudioStream] that decodes the audio.
 * @param primaryInputStream The initial [InputStream] of the encoded audio to play.
 * @param loopStart The starting point of the main audio loop in SAMPLES. If 0, loop will run from start until the [loopEnd].
 * @param loopEnd The ending point of the main audio loop in SAMPLES. If 0, loop will run to completion.
 *
 * @author Segfault Guy
 * @since October 19th, 2025
 */
class LoopPointAudioStream @Throws(IllegalArgumentException::class) constructor(
    private val provider: LoopingAudioStream.AudioStreamProvider,
    primaryInputStream: InputStream,
    loopStart: Int = 0,
    loopEnd: Int = 0
): AudioStream {

    private val DEBUG_FLAG = true

    /** Amount of buffers of decoded PCM, from the start of the loop, to cache. */
    private val CACHE_LIMIT = Channel.QUEUED_BUFFER_COUNT  // CACHE_LIMIT matches the number of buffers pre-read before feeding to OpenAL

    /** Buffers of PCM that start the loop. */
    private val bufferCache = mutableListOf<ByteBuffer>()  // cache size: 4 buffers, each of size ~176400 (70kb of cache) which is fine

    /** The total number of bytes saved in the [bufferCache]. */
    private var cacheSize = 0

    /** The number of buffers read from the [bufferCache] */
    private var bufferCounter = 0

    /** The bytes bytween the end of the cache and the start of the rewinded [decoderStream]. */
    private var preliminaryBuffer: ByteBuffer? = null

    /** Whether the [preliminaryBuffer] has been read on this pass. */
    private var readPrem: Boolean = false

    /** The OGG Vorbis. This is encoded audio. */
    private lateinit var encodedStream: BufferedInputStream

    /** The Vorbis decoder. This decodes PCM from the [encodedStream] on calls to [AudioStream.read]. */
    private lateinit var decoderStream: AudioStream

    /** The starting point of the loop in BYTES. */
    private var loopStart by Delegates.notNull<Int>()

    /** The ending point of the loop in BYTES. */
    private var loopEnd by Delegates.notNull<Int>()

    /** Whether this is the first pass of the [encodedStream] and we haven't looped yet. */
    private var firstPass = true

    /** Whether [decoderStream] has been rewinded to [loopStart]. */
    private var loopPrepared = false

    /** The total number of bytes that have been read from the decoded stream. */
    private var runningRead = 0

    init {
        if (loopStart > 0 && loopEnd > 0 && loopEnd < loopStart) throw IllegalArgumentException("Invalid loop points: non-zero end precedes non-zero start")
        if (loopStart < 0 || loopEnd < 0) throw IllegalArgumentException("Invalid loop points: only non-negative values allowed")

        this.encodedStream = BufferedInputStream(primaryInputStream)
        this.encodedStream.mark(Int.Companion.MAX_VALUE)
        this.buildDecoderStream()

        this.loopStart = this.samplesToBytes(loopStart)
        this.loopEnd = this.samplesToBytes(loopEnd)
    }

    override fun getFormat(): AudioFormat? {
        return this.decoderStream.format
    }

    /** Reads 1 second worth of PCM (bytes) for an OpenAL buffer (this will be AT LEAST 176400 bytes for stereo audio formats) */
    override fun read(size: Int): ByteBuffer? {

        // if decoderStream has not finished rewinding, there's a problem
        if (!this.firstPass && !this.loopPrepared) {
            Cobblemon.LOGGER.warn("Decoder not rewinded!")
        }

       this.debug("read " + size + " | runningRead: " + this.runningRead + " | loopStart: " + this.loopStart + " | loopEnd: " + this.loopEnd)

        val buffer = this.getCachedOrRawBuffer(size)
        val sizeAfterRead = this.runningRead + buffer.remaining()

        // if this read reaches the end loop point
        if (loopEnd > 0 && sizeAfterRead >= this.loopEnd) {
            val remainderBeforeEnd = this.loopEnd - this.runningRead
            // limit buffer if it goes past the loop point (cached buffers don't apply as they are always within the loop points)
            if (buffer.remaining() > remainderBeforeEnd)
            {
                this.debug("limit and reset read")
                buffer.limit(remainderBeforeEnd)
            }
            else {
                this.debug("reset read")
            }
            this.resetLoop()
        }
        // if loopEnd is 0 and the entire stream is read, or exaggerated and the stream is exhausted before reaching the point
        else if (!buffer.hasRemaining()) {
            this.debug("exhaustive read")
            this.resetLoop()
        }
        // standard read
        else {
            this.debug("standard read")
            this.runningRead += buffer.remaining();
        }

        return buffer
    }

    /**
     * Retrieves a [ByteBuffer] of varying size from the [bufferCache] if this is not the [firstPass] and a cached buffer
     * is available, or reads approximately [size] bytes from the [decoderStream].
     */
    private fun getCachedOrRawBuffer(size: Int): ByteBuffer {
        val cacheExists = CACHE_LIMIT > 0 && !this.bufferCache.isEmpty() && this.bufferCounter < this.bufferCache.size

        // get buffer from cache
        if (cacheExists && !this.firstPass) {
            val toReturn = this.bufferCache.get(this.bufferCounter++)
            this.debug("get cached buffer: " + toReturn.remaining())
            return toReturn
        }
        // TODO coalesce this with bufferCache
        else if (!this.readPrem && this.preliminaryBuffer != null && !this.firstPass) {
            this.readPrem = true
            val toReturn = this.preliminaryBuffer!!
            this.debug("read prem buffer: " + toReturn.remaining())
            return toReturn
        }
        // decode a buffer of approximate size
        else {
            val toReturn = this.decoderStream.read(size)
            this.debug("read raw: " + toReturn.remaining())
            this.cacheBuffer(toReturn)
            return toReturn
        }
    }

    /**
     * Caches [buffer] in the [bufferCache] during the [firstPass] if the [CACHE_LIMIT] has not been reached and the buffer
     * contains bytes within the [loopStart] and the [loopEnd] boundaries.
     */
    private fun cacheBuffer(buffer: ByteBuffer) {
        val canCacheBuffer = CACHE_LIMIT > 0 && loopStart > 0 && this.bufferCache.size < CACHE_LIMIT && this.firstPass
        val canStartCaching = this.runningRead + buffer.remaining() > this.loopStart
        if (!canCacheBuffer || !canStartCaching) return

        // offset used - so that only the tail of a buffer that contains the loopStart point is cached
        val remainderBeforeStart = Math.max(this.loopStart - this.runningRead, 0)
        var remainderBeforeEnd = 0

        // only cache the head of a buffer that goes past the loopEnd point
        if (loopEnd > 0 && buffer.remaining() + this.runningRead > this.loopEnd) {
            remainderBeforeEnd = this.loopEnd - this.runningRead - remainderBeforeStart
            // NOTE: read will resetLoop and stop caching if CACHE_LIMIT has not been reached but loopEnd has
        }
        // otherwise, cache the entire remainder of the buffer
        else {
            remainderBeforeEnd = buffer.remaining() - remainderBeforeStart
        }

        if (remainderBeforeEnd <= 0) return

        // copy to avoid mutating provided buffer
        val buffed = BufferUtils.createByteBuffer(remainderBeforeEnd)
        buffed.put(0, buffer, remainderBeforeStart, remainderBeforeEnd)
        buffed.rewind()

        this.debug("cache buffer | remainderBeforeStart: " + remainderBeforeStart + " | remainderBeforeEnd: " + remainderBeforeEnd + " | size: " + buffed.remaining())

        this.bufferCache.add(buffed)
        this.cacheSize += buffed.remaining()

        this.debug("cacheSize: " + cacheSize + " bufferCacheSize: " + this.bufferCache.size)
    }

    /** Converts PCM samples to bytes. */
    private fun samplesToBytes(sampleAmount: Int): Int {
        val format = this.format ?: return 0
        return sampleAmount * format.getSampleSizeInBits() / 8 * format.getChannels()
    }

    /**
     * Rewind [decoderStream] to start from [loopStart].
     *
     * NOTE: Assumes that [bufferCache] is finalized.
     */
    private fun resetLoop() {
        // reset state info
        this.loopPrepared = false
        this.runningRead = this.loopStart
        this.bufferCounter = 0
        this.firstPass = false

        this.debug("reset")

        // if the entire loop is small enough to fit the cache, skip rewinding the decoderStream
        if (this.bufferCache.size > 0 && this.bufferCache.size < CACHE_LIMIT) {
            this.debug("comprehensive cache")
            this.loopPrepared = true
            return
        }

        /*
         Seeking is implemented agnostic of the OGG Vorbis decoder. Seek to the loop start by decoding PCM in bulk from
         the beginning of the OGG Vorbis up until the desired sample offset is reached. Then for playing the loop, continue
         streaming decoded PCM into OpenAL buffers.
         */
        this.decoderStream.close()
        this.encodedStream.reset()
        this.buildDecoderStream()

        // if loopStart is 0, there's no cache, so loop starts by reading from the new decoderStream
        if (this.bufferCache.size == 0) {
            this.debug("nonexistent cache")
            this.loopPrepared = true
            return
        }

        /*
         After the first pass, encodedStream will have the entire Vorbis OGG buffered and there won't be any subsequent reads
         from disk. However, there's still latency in decoding a lot of PCM. This needs to be done asynchronously, otherwise
         the client will momentarily freeze each time the loop resets. In the unlikely case that the decoderStream is not
         rewinded by the time the Channel needs to read a buffer, it will be read from bufferCache.
         */
        CompletableFuture.supplyAsync<Any?>({
            try {
                val decoderStart = this.loopStart + this.cacheSize
                val buffer = decoderStream.read(decoderStart)

                // because the deecoderStream will only read an approximation, need to account for additional bytes
                if (this.preliminaryBuffer == null && buffer.remaining() > decoderStart) {
                    this.debug("rewind buffer size: " + buffer.remaining() + " overshot by: " + (buffer.remaining() - decoderStart))
                    this.preliminaryBuffer = BufferUtils.createByteBuffer(buffer.remaining() - decoderStart)
                    this.preliminaryBuffer!!.put(0, buffer, decoderStart, buffer.remaining() - decoderStart)
                    this.preliminaryBuffer!!.rewind()
                    // decoderStream.read is deterministic, so this should only be set once
                }
                this.readPrem = false
                this.loopPrepared = true
                this.debug("decoderStream rewinded")
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

    private fun debug(log: String) {
        if (this.DEBUG_FLAG) Cobblemon.LOGGER.error(log)
    }
}