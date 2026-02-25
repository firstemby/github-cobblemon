/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

var timer = 0;
var POKEBALL_SPRITE = 16;
var POKEBALL_DISPLAY = 12;
var POKEBALL_GAP = 3;
var PANEL_HEIGHT = 44;
var BAR_HEIGHT = 28;

// Animation timing (seconds)
var BG_FADE_START = 0.0;
var BG_FADE_END = 0.4;
var PLAYER_SLIDE_START = 0.2;
var PLAYER_SLIDE_END = 0.8;
var OPPONENT_SLIDE_START = 0.4;
var OPPONENT_SLIDE_END = 1.0;
var VS_FADE_START = 0.8;
var VS_FADE_END = 1.3;
var NAME_SLIDE_START = 0.8;
var NAME_SLIDE_END = 3.0;
var BALL_START = 0.6;
var BALL_STAGGER = 0.08;

// Colors
var PLAYER_PANEL_COLOR = 0xDD1A3A6B;   // Dark blue
var OPPONENT_PANEL_COLOR = 0xDD6B1A1A;  // Dark red
var PLAYER_ACCENT = 0xFF3366CC;        // Blue accent
var OPPONENT_ACCENT = 0xFFCC3333;      // Red accent
var NAME_COLOR = 0xFFFFFFFF;           // White
var VS_COLOR = 0xFFFFDD44;            // Gold
var VS_SHADOW = 0xFF886611;           // Dark gold shadow
var BAR_COLOR = 0xFF111111;           // Near-black bars

function init(ctx, args) {
    ctx.state.timer = 0;
}

function easeOut(t) {
    t = Math.min(Math.max(t, 0), 1);
    var inv = 1 - t;
    return 1 - inv * inv * inv;
}

function progress(t, start, finish) {
    if (t < start) return 0;
    if (t >= finish) return 1;
    return (t - start) / (finish - start);
}

function render(ctx, args, delta) {
    if (ctx.state.timer == null) ctx.state.timer = 0;
    ctx.state.timer = ctx.state.timer + delta;
    var timer = ctx.state.timer;

    var w = ctx.screenWidth;
    var h = ctx.screenHeight;
    var cx = Math.floor(w / 2);
    var cy = Math.floor(h / 2);

    var barAlpha = Math.floor(easeOut(progress(timer, BG_FADE_START, BG_FADE_END)) * 255);
    var barColor = barAlpha * 0x1000000 + 0x111111;
    ctx.fill(0, 0, w, BAR_HEIGHT, barColor);
    ctx.fill(0, h - BAR_HEIGHT, w, h, barColor);

    var accentAlpha = Math.floor(easeOut(progress(timer, 0.3, 0.7)) * 255);
    var playerLineColor = accentAlpha * 0x1000000 + 0x3366CC;
    var opponentLineColor = accentAlpha * 0x1000000 + 0xCC3333;
    ctx.fill(0, BAR_HEIGHT, w, BAR_HEIGHT + 2, playerLineColor);
    ctx.fill(0, h - BAR_HEIGHT - 2, w, h - BAR_HEIGHT, opponentLineColor);

    var playerSlide = easeOut(progress(timer, PLAYER_SLIDE_START, PLAYER_SLIDE_END));
    var panelWidth = Math.floor(w * 0.45);
    var playerX = Math.floor(-panelWidth + playerSlide * panelWidth);
    var playerY = cy - PANEL_HEIGHT - 8;

    drawPanel(ctx, playerX, playerY, panelWidth, PANEL_HEIGHT,
              PLAYER_PANEL_COLOR, PLAYER_ACCENT, true);

    var nameSlide1 = easeOut(progress(timer, NAME_SLIDE_START, NAME_SLIDE_END));
    if (nameSlide1 > 0) {
        var nameAlpha = Math.floor(Math.min(nameSlide1 / 0.4, 1) * 255);
        var nameColor = nameAlpha * 0x1000000 + 0xFFFFFF;
        var playerName = args.player_name || "Player";
        var playerNameWidth = ctx.stringWidth(playerName);
        var nameFinalX = playerX + panelWidth - playerNameWidth - 16;
        var nameStartX = playerX - playerNameWidth;
        var nameDrawX = Math.floor(nameStartX + (nameFinalX - nameStartX) * nameSlide1);
        ctx.enableScissor(playerX, playerY, playerX + panelWidth - 3, playerY + PANEL_HEIGHT);
        ctx.drawString(playerName, nameDrawX, playerY + 8, nameColor);
        ctx.disableScissor();
    }

    var playerCount = args.player_party_count || 6;
    var ballY = playerY + 24;
    for (var i = 1; i <= playerCount; i++) {
        var ballProgress = easeOut(progress(timer, BALL_START + (i - 1) * BALL_STAGGER,
                                                    BALL_START + (i - 1) * BALL_STAGGER + 0.2));
        if (ballProgress > 0) {
            var ballAlpha = Math.floor(ballProgress * 255);
            var ballX = playerX + 16 + (i - 1) * (POKEBALL_DISPLAY + POKEBALL_GAP);
            var ballScale = 0.6 + ballProgress * 0.4;
            drawPokeball(ctx, ballX, ballY, ballAlpha, ballScale);
        }
    }

    var opponentSlide = easeOut(progress(timer, OPPONENT_SLIDE_START, OPPONENT_SLIDE_END));
    var opponentX = Math.floor(w - opponentSlide * panelWidth);
    var opponentY = cy + 8;

    drawPanel(ctx, opponentX, opponentY, panelWidth, PANEL_HEIGHT,
              OPPONENT_PANEL_COLOR, OPPONENT_ACCENT, false);

    var nameSlide2 = easeOut(progress(timer, NAME_SLIDE_START + 0.2, NAME_SLIDE_END + 0.2));
    if (nameSlide2 > 0) {
        var nameAlpha2 = Math.floor(Math.min(nameSlide2 / 0.4, 1) * 255);
        var nameColor2 = nameAlpha2 * 0x1000000 + 0xFFFFFF;
        var opponentName = args.opponent_name || "Opponent";
        var opponentNameWidth = ctx.stringWidth(opponentName);
        var nameFinalX2 = opponentX + 16;
        var nameStartX2 = opponentX + panelWidth + opponentNameWidth;
        var nameDrawX2 = Math.floor(nameStartX2 + (nameFinalX2 - nameStartX2) * nameSlide2);
        ctx.enableScissor(opponentX + 3, opponentY, opponentX + panelWidth, opponentY + PANEL_HEIGHT);
        ctx.drawString(opponentName, nameDrawX2, opponentY + 8, nameColor2);
        ctx.disableScissor();
    }

    var opponentCount = args.opponent_party_count || 6;
    var oppBallY = opponentY + 24;
    for (var j = 1; j <= opponentCount; j++) {
        var ballProgress2 = easeOut(progress(timer, BALL_START + 0.3 + (j - 1) * BALL_STAGGER,
                                                     BALL_START + 0.3 + (j - 1) * BALL_STAGGER + 0.2));
        if (ballProgress2 > 0) {
            var ballAlpha2 = Math.floor(ballProgress2 * 255);
            var ballX2 = opponentX + panelWidth - 16 - j * (POKEBALL_DISPLAY + POKEBALL_GAP);
            var ballScale2 = 0.6 + ballProgress2 * 0.4;
            drawPokeball(ctx, ballX2, oppBallY, ballAlpha2, ballScale2);
        }
    }

    var vsProgress = easeOut(progress(timer, VS_FADE_START, VS_FADE_END));
    if (vsProgress > 0) {
        var vsAlpha = Math.floor(vsProgress * 255);
        var vsScale = 0.8 + vsProgress * 0.2;

        ctx.pushPose();
        ctx.translate(cx, cy);
        ctx.scale(vsScale * 2.5, vsScale * 2.5);


        var shadowColor = vsAlpha * 0x1000000 + 0x886611;
        ctx.drawCenteredString("VS", 1, -4 + 1, shadowColor);


        var vsColor = vsAlpha * 0x1000000 + 0xFFDD44;
        ctx.drawCenteredString("VS", 0, -4, vsColor);

        ctx.popPose();


        var lineAlpha = Math.floor(vsProgress * 200);
        var lineColor = lineAlpha * 0x1000000 + 0xFFDD44;
        var lineLen = Math.floor(vsProgress * 40);
        ctx.fill(cx - 30 - lineLen, cy - 1, cx - 30, cy + 1, lineColor);
        ctx.fill(cx + 30, cy - 1, cx + 30 + lineLen, cy + 1, lineColor);
    }
}

function drawPanel(ctx, x, y, width, height, bgColor, accentColor, isLeft) {
    ctx.fill(x, y, x + width, y + height, bgColor);
    if (isLeft) {
        ctx.fill(x + width - 3, y, x + width, y + height, accentColor);
    } else {
        ctx.fill(x, y, x + 3, y + height, accentColor);
    }
}

function drawPokeball(ctx, x, y, alpha, scale) {
    if (alpha < 10) return;
    var displayScale = scale * (POKEBALL_DISPLAY / POKEBALL_SPRITE);
    ctx.pushPose();
    var centerX = x + POKEBALL_DISPLAY / 2;
    var centerY = y + POKEBALL_DISPLAY / 2;
    ctx.translate(centerX, centerY);
    ctx.scale(displayScale, displayScale);
    ctx.translate(-POKEBALL_SPRITE / 2, -POKEBALL_SPRITE / 2);
    ctx.drawTexture("cobblemon:textures/gui/ball/poke_ball.png", 0, 0, 18, 22, 0, 0, 18, 44);
    ctx.popPose();
}
