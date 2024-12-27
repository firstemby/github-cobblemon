package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.spawning.IrlDateRange
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object IrlDateRangeAdapter:  JsonSerializer<IrlDateRange>, JsonDeserializer<IrlDateRange> {
    val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
    //parsing dates requires a year instead of just assuming this year so we do jank
    val dateFormatWithYear: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu/M/d")

    override fun serialize(
        src: IrlDateRange,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return if(src.stopDate != null){
            JsonPrimitive(src.startDate.format(dateFormat) + "-" + src.stopDate.format(dateFormat))
        } else {
            JsonPrimitive(src.startDate.format(dateFormat))
        }
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): IrlDateRange {
        val year = LocalDate.now().year.toString()

        return if(json.asString.contains("-")){
            val dateStrings = json.asString.split("-")
            IrlDateRange(
                startDate = LocalDate.parse(year + "/" + dateStrings[0], dateFormatWithYear),
                stopDate = LocalDate.parse(year + "/" + dateStrings[1], dateFormatWithYear)
            )
        } else {
            IrlDateRange(
                startDate = LocalDate.parse(year + "/" + json.asString, dateFormatWithYear)
            )
        }
    }

}