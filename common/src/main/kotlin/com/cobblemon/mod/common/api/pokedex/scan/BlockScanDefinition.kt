package com.cobblemon.mod.common.api.pokedex.scan

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class BlockScanDefinition(
    val blockEntityType: ResourceLocation,
    val frames: List<Frame> = emptyList()
) {
    data class Frame(
        val slot: Int,                  // 1..4 matches your existing renderer counters
        val text: ScanText,
        val scale: Float? = null
    )

    /** A small “DSL” for text. No arbitrary code. */
    sealed interface ScanText {
        data class Literal(val text: String) : ScanText
        data class Key(val key: String) : ScanText                  // provider-resolved value
        data class Translate(val key: String, val with: List<ScanText> = emptyList()) : ScanText
    }

    companion object {
        private val SCAN_TEXT_CODEC: Codec<ScanText> = Codec.recursive("ScanText") { self ->
            Codec.either(
                Codec.STRING,
                RecordCodecBuilder.create<ScanText> { b ->
                    b.group(
                        Codec.STRING.optionalFieldOf("literal")
                            .forGetter { Optional.ofNullable((it as? ScanText.Literal)?.text) },

                        Codec.STRING.optionalFieldOf("key")
                            .forGetter { Optional.ofNullable((it as? ScanText.Key)?.key) },

                        Codec.STRING.optionalFieldOf("translate")
                            .forGetter { Optional.ofNullable((it as? ScanText.Translate)?.key) },

                        Codec.list(self).optionalFieldOf("with")
                            .forGetter { Optional.ofNullable((it as? ScanText.Translate)?.with) }
                    ).apply(b) { literal, key, tr, with ->
                        when {
                            literal.isPresent -> ScanText.Literal(literal.get())
                            key.isPresent -> ScanText.Key(key.get())
                            tr.isPresent -> ScanText.Translate(tr.get(), with.getOrNull() ?: emptyList())
                            else -> ScanText.Literal("")
                        }
                    }
                }
            ).xmap(
                { e ->
                    e.left().getOrNull()?.let { ScanText.Literal(it) }
                        ?: e.right().getOrNull()
                        ?: ScanText.Literal("")
                },
                { st ->
                    when (st) {
                        is ScanText.Literal -> com.mojang.datafixers.util.Either.left<String, ScanText>(st.text)
                        else -> com.mojang.datafixers.util.Either.right<String, ScanText>(st)
                    }
                }
            )
        }

        val FRAME_CODEC: Codec<Frame> = RecordCodecBuilder.create { b ->
            b.group(
                Codec.INT.fieldOf("slot").forGetter { it.slot },
                SCAN_TEXT_CODEC.fieldOf("text").forGetter { it.text },
                Codec.FLOAT.optionalFieldOf("scale").forGetter { Optional.ofNullable(it.scale) }
            ).apply(b) { slot, text, scale ->
                Frame(slot, text, scale.getOrNull())
            }
        }

        val CODEC: Codec<BlockScanDefinition> = RecordCodecBuilder.create { b ->
            b.group(
                ResourceLocation.CODEC.fieldOf("block_entity_type").forGetter { it.blockEntityType },
                FRAME_CODEC.listOf().optionalFieldOf("frames").forGetter { Optional.ofNullable(it.frames) }
            ).apply(b) { beType, frames ->
                BlockScanDefinition(beType, frames.getOrNull() ?: emptyList())
            }
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, BlockScanDefinition> =
            ByteBufCodecs.fromCodec(CODEC)
    }
}