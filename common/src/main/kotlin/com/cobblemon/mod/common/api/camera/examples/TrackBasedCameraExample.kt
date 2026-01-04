/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.examples

import com.cobblemon.mod.common.api.camera.builders.CameraTimelineBuilder
import com.cobblemon.mod.common.client.camera.CoordinateValue
import com.cobblemon.mod.common.client.camera.LookTarget
import com.cobblemon.mod.common.client.camera.PathInterpolationType
import com.cobblemon.mod.common.client.camera.PositionExpression
import com.cobblemon.mod.common.client.camera.tracks.EventTrack
import com.cobblemon.mod.common.client.camera.tracks.FocusKeyframe
import com.cobblemon.mod.common.client.camera.tracks.FocusTrack
import com.cobblemon.mod.common.client.camera.tracks.PositionKeyframe
import com.cobblemon.mod.common.client.camera.tracks.PositionTrack
import com.cobblemon.mod.common.client.camera.tracks.TimedEvent
import com.cobblemon.mod.common.util.asExpressionLike

/**
 * Example demonstrating how to create track-based camera timelines programmatically.
 *
 * The track-based system allows you to compose multiple tracks (position, focus, events)
 * similar to professional compositing tools like After Effects or Blender's NLA editor.
 *
 * This is the recommended approach for complex camera animations.
 *
 * @author Terrainwax
 * @since January 2025
 */
object TrackBasedCameraExample {

    /**
     * Example 1: Simple track-based camera with position and focus.
     *
     * Creates a camera that moves along a curved path while focusing on a target.
     */
    fun createSimpleTrackCamera() {
        // Create a position track with keyframes
        val positionTrack = PositionTrack(
            keyframes = listOf(
                PositionKeyframe(
                    time = 0f,
                    position = PositionExpression(
                        x = CoordinateValue.Dynamic("q.origin_x + 10".asExpressionLike()),
                        y = CoordinateValue.Dynamic("q.origin_y + 5".asExpressionLike()),
                        z = CoordinateValue.Dynamic("q.origin_z".asExpressionLike())
                    ),
                    easing = "ease_in_out_quad"
                ),
                PositionKeyframe(
                    time = 3f,
                    position = PositionExpression(
                        x = CoordinateValue.Dynamic("q.origin_x + 20".asExpressionLike()),
                        y = CoordinateValue.Dynamic("q.origin_y + 10".asExpressionLike()),
                        z = CoordinateValue.Dynamic("q.origin_z + 15".asExpressionLike())
                    ),
                    easing = "ease_out_cubic"
                )
            ),
            interpolationType = PathInterpolationType.BEZIER,
            easing = "linear",
            isRelative = false
        )

        // Create a focus track
        val focusTrack = FocusTrack(
            keyframes = listOf(
                FocusKeyframe(
                    time = 0f,
                    target = LookTarget.Point(
                        position = PositionExpression(
                            x = CoordinateValue.Dynamic("q.origin_x".asExpressionLike()),
                            y = CoordinateValue.Dynamic("q.origin_y + 2".asExpressionLike()),
                            z = CoordinateValue.Dynamic("q.origin_z".asExpressionLike())
                        )
                    )
                )
            ),
            easing = "linear"
        )

        // Build the timeline with tracks
        val timeline = CameraTimelineBuilder("simple_track_example")
            .startState("main")
            .state("main") {
                track(positionTrack)
                track(focusTrack)
                looping(false)
                speed(1.0f)
            }
            .build()

        // Register the timeline (would happen during mod init)
        // CameraTimelineRegistry.registerTimeline(timeline)
    }

    /**
     * Example 2: Advanced track-based camera with events.
     *
     * Creates a camera with position, focus, and timed events for a cinematic sequence.
     */
    fun createAdvancedTrackCamera() {
        // Position track - camera movement
        val positionTrack = PositionTrack(
            keyframes = listOf(
                PositionKeyframe(0f, PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 5".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z + 10".asExpressionLike())
                )),
                PositionKeyframe(2f, PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x + 5".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 8".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z + 12".asExpressionLike())
                )),
                PositionKeyframe(5f, PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x + 10".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 10".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z + 5".asExpressionLike())
                ))
            ),
            interpolationType = PathInterpolationType.HERMITE,
            easing = "ease_in_out_sine"
        )

        // Focus track - what the camera looks at
        val focusTrack = FocusTrack(
            keyframes = listOf(
                FocusKeyframe(0f, LookTarget.Point(PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z".asExpressionLike())
                ))),
                FocusKeyframe(3f, LookTarget.Point(PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x + 5".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 2".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z + 3".asExpressionLike())
                )))
            ),
            easing = "ease_out_quad"
        )

        // Event track - timed actions
        val eventTrack = EventTrack(
            events = listOf(
                TimedEvent(0.5f, "q.play_sound('cobblemon:poke_ball.open')".asExpressionLike(), "intro_sound"),
                TimedEvent(2.0f, "q.spawn_particles('cobblemon:sparkle', q.origin_x, q.origin_y, q.origin_z, 20)".asExpressionLike(), "particles"),
                TimedEvent(4.0f, "q.camera_shake(0.2, 10)".asExpressionLike(), "camera_shake")
            )
        )

        // Build timeline
        val timeline = CameraTimelineBuilder("advanced_track_example")
            .startState("cinematic")
            .state("cinematic") {
                tracks(positionTrack, focusTrack, eventTrack)
                looping(false)
                speed(1.0f)
            }
            .build()

        // CameraTimelineRegistry.registerTimeline(timeline)
    }

    /**
     * Example 3: Multi-state timeline with track transitions.
     *
     * Demonstrates state machine with different track compositions per state.
     */
    fun createMultiStateTrackCamera() {
        // Intro state tracks
        val introPosition = PositionTrack(
            keyframes = listOf(
                PositionKeyframe(0f, PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x + 15".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 10".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z".asExpressionLike())
                )),
                PositionKeyframe(3f, PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x + 10".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 5".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z + 5".asExpressionLike())
                ))
            ),
            interpolationType = PathInterpolationType.BEZIER
        )

        val introFocus = FocusTrack(
            keyframes = listOf(
                FocusKeyframe(0f, LookTarget.Point(PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 2".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z".asExpressionLike())
                )))
            )
        )

        // Orbit state tracks
        val orbitPosition = PositionTrack(
            keyframes = listOf(
                PositionKeyframe(
                    0f,
                    PositionExpression(
                        CoordinateValue.Dynamic("q.origin_x + 12 * math.cos(q.time * 0.5)".asExpressionLike()),
                        CoordinateValue.Dynamic("q.origin_y + 8".asExpressionLike()),
                        CoordinateValue.Dynamic("q.origin_z + 12 * math.sin(q.time * 0.5)".asExpressionLike())
                    )
                )
            ),
            interpolationType = PathInterpolationType.LINEAR
        )

        val orbitFocus = FocusTrack(
            keyframes = listOf(
                FocusKeyframe(0f, LookTarget.Point(PositionExpression(
                    CoordinateValue.Dynamic("q.origin_x".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_y + 2".asExpressionLike()),
                    CoordinateValue.Dynamic("q.origin_z".asExpressionLike())
                )))
            )
        )

        // Build timeline with multiple states
        val timeline = CameraTimelineBuilder("multi_state_track_example")
            .startState("intro")
            .state("intro") {
                tracks(introPosition, introFocus)
                looping(false)
                speed(1.0f)
            }
            .state("orbit") {
                tracks(orbitPosition, orbitFocus)
                looping(true)
                speed(0.8f)
            }
            .transition {
                from("intro")
                to("orbit")
                condition("q.time >= 3.0".asExpressionLike())
                blendDuration(0.5f)
                blend()
            }
            .build()

        // CameraTimelineRegistry.registerTimeline(timeline)
    }

    /**
     * Example 4: Creating a custom track type (for third-party mods).
     *
     * This shows how external developers can extend the system with custom tracks.
     */
    fun registerCustomTrackType() {
        // Example custom track for field of view animation
        // (Implementation details omitted for brevity)

        /*
        CameraTrackRegistry.registerTrackType("fov") { json ->
            // Deserialize custom FOV track from JSON
            val keyframes = json.getAsJsonArray("keyframes").map { ... }
            FovTrack(keyframes)
        }
        */

        // Then use it in timelines:
        /*
        val fovTrack = FovTrack(
            keyframes = listOf(
                FovKeyframe(0f, 70f),
                FovKeyframe(2f, 90f),
                FovKeyframe(4f, 70f)
            )
        )

        CameraTimelineBuilder("with_fov")
            .startState("main")
            .state("main") {
                track(positionTrack)
                track(focusTrack)
                track(fovTrack)  // Custom track!
            }
            .build()
        */
    }
}
