package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.spawning.IrlTimeRange
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object IrlTimeRangeAdapter: JsonSerializer<IrlTimeRange>, JsonDeserializer<IrlTimeRange> {
    val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    override fun serialize(
        src: IrlTimeRange,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return JsonPrimitive(src.startTime.format(timeFormat) + "-" + src.stopTime.format(timeFormat))
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): IrlTimeRange {
        val timeStrings = json.asString.split("-")
        return IrlTimeRange(
            startTime = LocalTime.parse(timeStrings[0], timeFormat),
            stopTime = LocalTime.parse(timeStrings[1], timeFormat)
        )
    }

}