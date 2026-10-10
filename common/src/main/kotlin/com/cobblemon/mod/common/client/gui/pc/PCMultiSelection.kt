package com.cobblemon.mod.common.client.gui.pc

import com.cobblemon.mod.common.pokemon.Pokemon

data class PCMultiSelection(
    private val grid: MutableList<MutableList<Pokemon?>> = mutableListOf(),
    val sourceBox: Int = 0,
    val sourceColumn: Int = 0,
    val sourceRow: Int = 0
) {
    fun getHorizontalSize(): Int = grid.firstOrNull()?.size ?: 0
    fun getVerticalSize(): Int = grid.size

    constructor(horizontalSize: Int, verticalSize: Int, sourceBox: Int, sourceColumn: Int, sourceRow: Int) : this(
        MutableList(verticalSize) { MutableList<Pokemon?>(horizontalSize) { null } },
        sourceBox,
        sourceColumn,
        sourceRow
    )

    fun getPokemon(x: Int, y: Int): Pokemon? {
        return grid[y][x]
    }

    fun setPokemon(x: Int, y: Int, pokemon: Pokemon?) {
        grid[y][x] = pokemon
    }

    fun pokemonEntries(): List<Triple<Int, Int, Pokemon>> {
        return grid.flatMapIndexed { y, row ->
            row.mapIndexedNotNull { x, pokemon -> pokemon?.let { Triple(x, y, it) } }
        }
    }

}
