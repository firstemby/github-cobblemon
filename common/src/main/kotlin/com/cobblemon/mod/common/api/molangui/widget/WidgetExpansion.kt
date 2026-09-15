package com.cobblemon.mod.common.api.molangui.widget

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molangui.MolangUiPacketLimits
import com.cobblemon.mod.common.api.molangui.layout.SizeExpression
import com.google.gson.JsonElement
import com.google.gson.JsonParser

internal object WidgetExpansion {
    private data class Node(val definition: WidgetDefinition, val context: QueryStruct?, val children: List<Node>)

    fun refresh(definition: WidgetDefinition, runtime: MoLangRuntime, previous: WidgetInstance? = null): WidgetInstance {
        var count = 0
        fun plan(def: WidgetDefinition, context: QueryStruct?, depth: Int): Node = withItemContext(runtime, context) {
            require(depth <= WidgetTree.MAX_DEPTH && ++count <= WidgetTree.MAX_NODES) {
                "expanded widget tree exceeds ${WidgetTree.MAX_NODES} widgets or depth ${WidgetTree.MAX_DEPTH}"
            }
            val children = if (def.type == WidgetType.LIST) {
                val template = requireNotNull(def.itemTemplate) { "list '${def.id}' requires item_template" }
                items(def, runtime).mapIndexed { index, values ->
                    val item = QueryStruct(hashMapOf())
                    values.forEach { (key, value) -> item.addFunction(key) { value } }
                    item.addFunction("index") { DoubleValue(index.toDouble()) }
                    plan(template.copy(id = "${template.id}[$index]"), item, depth + 1)
                }
            } else def.children.map { plan(it, context, depth + 1) }
            Node(def, context, children)
        }
        val planned = plan(definition, null, 1)
        fun apply(node: Node, old: WidgetInstance?, parent: WidgetInstance?): WidgetInstance {
            val widget = old?.takeIf { it.definition == node.definition } ?: WidgetInstance(node.definition, parent).apply {
                val size = node.definition.getProperty("size")?.raw as? List<*>
                if (size?.size == 2) {
                    widthExpr = SizeExpression.parse(size[0] ?: 0)
                    heightExpr = SizeExpression.parse(size[1] ?: 0)
                }
                val offset = node.definition.getProperty("offset")?.raw as? List<*>
                if (offset?.size == 2) {
                    offsetXExpr = SizeExpression.parse(offset[0] ?: 0)
                    offsetYExpr = SizeExpression.parse(offset[1] ?: 0)
                }
            }
            widget.itemContext = node.context
            val children = node.children.mapIndexed { index, child -> apply(child, widget.children.getOrNull(index), widget) }
            widget.children.clear()
            widget.children.addAll(children)
            return widget
        }
        return apply(planned, previous, null)
    }

    private fun items(definition: WidgetDefinition, runtime: MoLangRuntime): List<Map<String, MoValue>> =
        when (definition.getStringProperty("source_type") ?: "data") {
            "count" -> {
                val count = definition.getProperty("source")?.resolveDouble(runtime) ?: 0.0
                require(count.isFinite() && count in 0.0..WidgetTree.MAX_NODES.toDouble() && count % 1.0 == 0.0) {
                    "list '${definition.id}' requires a whole count between 0 and ${WidgetTree.MAX_NODES}"
                }
                List(count.toInt()) { emptyMap() }
            }
            "data" -> {
                val source = definition.getProperty("source")?.resolveString(runtime) ?: "[]"
                require(source.length <= MolangUiPacketLimits.MAX_VALUE_LENGTH) { "list '${definition.id}' source is too large" }
                val array = JsonParser.parseString(source).asJsonArray
                require(array.size() <= WidgetTree.MAX_NODES) { "list '${definition.id}' has too many items" }
                array.map { element -> element.asJsonObject.entrySet().associate { (key, item) -> key to value(item) } }
            }
            else -> throw IllegalArgumentException("unsupported list source_type at '${definition.id}'")
        }

    private fun value(element: JsonElement): MoValue {
        if (!element.isJsonPrimitive) return StringValue(element.toString())
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isBoolean -> DoubleValue(primitive.asBoolean)
            primitive.isNumber -> DoubleValue(primitive.asDouble.also { require(it.isFinite()) { "non-finite list item value" } })
            else -> StringValue(primitive.asString)
        }
    }
}
