package com.cobblemon.mod.common.api.ai

/**
 * A classification of what a behaviour is related to, used for organisational purposes. It doesn't have any technical
 * implications beyond how they are displayed to players modifying an entity's Behaviours.
 *
 * @author Hiroku
 * @since April 25th, 2026
 */
enum class BehaviourCategory {
    /** Relates to movement, reactions, general entity behaviour. */
    AI,
    /** Relates to how the entity appears. */
    APPEARANCE,
    /** Relates to the entity's interactions with players or other entities. */
    INTERACTION,
    /** Relates to the entity's role in gameplay. */
    ROLE,
    /** Relates to the entity's ability to do Pokémon battles. */
    BATTLE,
    /** Relates to invisible, technical configuration. */
    TECHNICAL,
    /** Doesn't fit other categories. */
    OTHER,
}