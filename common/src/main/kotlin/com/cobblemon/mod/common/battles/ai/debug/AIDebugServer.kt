/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai.debug

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBuildDetails
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors

/**
 * Simple HTTP server for serving AI debug data to the web viewer.
 * Runs on port 8Pokemon (8765) by default.
 */
object AIDebugServer {
    private var server: HttpServer? = null
    private val decisionHistory = ConcurrentLinkedDeque<AIDecisionLog>()
    private const val MAX_HISTORY = 100
    private const val DEFAULT_PORT = 8765

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    var isRunning = false
        private set

    fun start(port: Int = DEFAULT_PORT) {
        if (isRunning) {
            Cobblemon.LOGGER.warn("Server already running on port ${server?.address?.port}")
            return
        }

        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/", ::handleRoot)
                createContext("/api/decisions", ::handleDecisions)
                createContext("/api/decisions/latest", ::handleLatestDecision)
                createContext("/api/decisions/clear", ::handleClearDecisions)
                createContext("/api/status", ::handleStatus)
                executor = Executors.newFixedThreadPool(2)
                start()
            }
            isRunning = true
            Cobblemon.LOGGER.info("AI debug server started at http://localhost:$port")
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to start AI debug server: ${e.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        isRunning = false
        Cobblemon.LOGGER.info("AI debug server stopped")
    }

    fun recordDecision(decision: AIDecisionLog) {
        decisionHistory.addFirst(decision)
        while (decisionHistory.size > MAX_HISTORY) {
            decisionHistory.removeLast()
        }

        try {
            val debugDir = File("ai-debug")
            if (!debugDir.exists()) debugDir.mkdirs()

            val latestFile = File(debugDir, "latest.json")
            latestFile.writeText(decision.toJson())

            val historyFile = File(debugDir, "decision_${decision.timestamp}.json")
            historyFile.writeText(decision.toJson())

            debugDir.listFiles()?.filter { it.name.startsWith("decision_") }
                ?.sortedByDescending { it.name }
                ?.drop(20)
                ?.forEach { it.delete() }

        } catch (e: Exception) {
            if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to write decision file: ${e.message}")
        }
    }

    fun getLatestDecision(): AIDecisionLog? = decisionHistory.peekFirst()

    fun getAllDecisions(): List<AIDecisionLog> = decisionHistory.toList()

    fun clearHistory() {
        decisionHistory.clear()
    }

    private fun handleRoot(exchange: HttpExchange) {
        val html = getDebugViewerHtml()
        sendResponse(exchange, html, "text/html")
    }

    private fun handleDecisions(exchange: HttpExchange) {
        val json = gson.toJson(decisionHistory.toList())
        sendResponse(exchange, json, "application/json")
    }

    private fun handleLatestDecision(exchange: HttpExchange) {
        val latest = decisionHistory.peekFirst()
        val json = if (latest != null) gson.toJson(latest) else "{}"
        sendResponse(exchange, json, "application/json")
    }

    private fun handleClearDecisions(exchange: HttpExchange) {
        decisionHistory.clear()
        sendResponse(exchange, """{"status": "cleared"}""", "application/json")
    }

    private fun handleStatus(exchange: HttpExchange) {
        val status = mapOf(
            "running" to true,
            "decisionCount" to decisionHistory.size,
            "maxHistory" to MAX_HISTORY
        )
        sendResponse(exchange, gson.toJson(status), "application/json")
    }

    private fun sendResponse(exchange: HttpExchange, response: String, contentType: String) {
        exchange.responseHeaders.set("Content-Type", "$contentType; charset=utf-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        val bytes = response.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun getDebugViewerHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cobblemon AI Debug Viewer</title>
    <style>
        :root {
            --bg-dark: #1a1a2e;
            --bg-card: #16213e;
            --bg-hover: #1f3460;
            --accent-blue: #4a9eff;
            --accent-green: #4ade80;
            --accent-red: #f87171;
            --accent-yellow: #fbbf24;
            --accent-purple: #a78bfa;
            --accent-pink: #f472b6;
            --accent-cyan: #22d3ee;
            --text-primary: #f1f5f9;
            --text-secondary: #94a3b8;
            --text-muted: #64748b;
            --border-color: #334155;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
            background: linear-gradient(135deg, var(--bg-dark) 0%, #0f0f23 100%);
            color: var(--text-primary);
            min-height: 100vh;
            line-height: 1.5;
        }

        .container {
            max-width: 1600px;
            margin: 0 auto;
            padding: 20px;
        }

        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 20px 0;
            border-bottom: 1px solid var(--border-color);
            margin-bottom: 20px;
        }

        .logo {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .logo h1 {
            font-size: 1.8rem;
            font-weight: 700;
            background: linear-gradient(90deg, var(--accent-blue), var(--accent-purple));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .status-badge {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 8px 16px;
            background: var(--bg-card);
            border-radius: 20px;
            font-size: 0.9rem;
        }

        .status-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: var(--accent-green);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }

        .controls {
            display: flex;
            gap: 10px;
        }

        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 0.9rem;
            font-weight: 500;
            transition: all 0.2s;
        }

        .btn-primary {
            background: var(--accent-blue);
            color: white;
        }

        .btn-primary:hover {
            background: #3b8bdb;
            transform: translateY(-1px);
        }

        .btn-secondary {
            background: var(--bg-card);
            color: var(--text-primary);
            border: 1px solid var(--border-color);
        }

        .btn-secondary:hover {
            background: var(--bg-hover);
        }

        .main-grid {
            display: grid;
            grid-template-columns: 350px 1fr;
            gap: 20px;
        }

        .card {
            background: var(--bg-card);
            border-radius: 12px;
            border: 1px solid var(--border-color);
            overflow: hidden;
        }

        .card-header {
            padding: 16px 20px;
            border-bottom: 1px solid var(--border-color);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .card-title {
            font-size: 1rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .card-body {
            padding: 20px;
        }

        /* Pokemon Display */
        .pokemon-card {
            display: flex;
            gap: 16px;
            padding: 16px;
            background: rgba(0, 0, 0, 0.2);
            border-radius: 10px;
            margin-bottom: 12px;
        }

        .pokemon-sprite {
            width: 80px;
            height: 80px;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }

        .pokemon-sprite img {
            max-width: 100%;
            max-height: 100%;
            image-rendering: pixelated;
        }

        .pokemon-info {
            flex: 1;
        }

        .pokemon-name {
            font-size: 1.1rem;
            font-weight: 600;
            margin-bottom: 4px;
        }

        .pokemon-details {
            font-size: 0.85rem;
            color: var(--text-secondary);
        }

        .hp-bar-container {
            margin-top: 8px;
        }

        .hp-bar {
            height: 8px;
            background: rgba(0, 0, 0, 0.3);
            border-radius: 4px;
            overflow: hidden;
        }

        .hp-bar-fill {
            height: 100%;
            border-radius: 4px;
            transition: width 0.3s ease;
        }

        .hp-bar-fill.high { background: var(--accent-green); }
        .hp-bar-fill.medium { background: var(--accent-yellow); }
        .hp-bar-fill.low { background: var(--accent-red); }

        .hp-text {
            font-size: 0.75rem;
            color: var(--text-muted);
            margin-top: 2px;
        }

        /* Type badges */
        .type-badges {
            display: flex;
            gap: 6px;
            margin-top: 6px;
        }

        .type-badge {
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 0.7rem;
            font-weight: 600;
            text-transform: uppercase;
        }

        .type-normal { background: #A8A878; }
        .type-fire { background: #F08030; }
        .type-water { background: #6890F0; }
        .type-electric { background: #F8D030; color: #333; }
        .type-grass { background: #78C850; }
        .type-ice { background: #98D8D8; color: #333; }
        .type-fighting { background: #C03028; }
        .type-poison { background: #A040A0; }
        .type-ground { background: #E0C068; color: #333; }
        .type-flying { background: #A890F0; }
        .type-psychic { background: #F85888; }
        .type-bug { background: #A8B820; }
        .type-rock { background: #B8A038; }
        .type-ghost { background: #705898; }
        .type-dragon { background: #7038F8; }
        .type-dark { background: #705848; }
        .type-steel { background: #B8B8D0; color: #333; }
        .type-fairy { background: #EE99AC; color: #333; }

        /* Thinking Flow */
        .thinking-flow {
            position: relative;
        }

        .thinking-step {
            display: flex;
            gap: 16px;
            padding: 16px 0;
            border-bottom: 1px solid var(--border-color);
        }

        .thinking-step:last-child {
            border-bottom: none;
        }

        .step-icon {
            width: 40px;
            height: 40px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.2rem;
            flex-shrink: 0;
        }

        .step-icon.context { background: rgba(74, 158, 255, 0.2); }
        .step-icon.scoring { background: rgba(74, 222, 128, 0.2); }
        .step-icon.decision { background: rgba(251, 191, 36, 0.2); }

        .step-content {
            flex: 1;
        }

        .step-title {
            font-weight: 600;
            margin-bottom: 4px;
        }

        .step-details {
            font-size: 0.9rem;
            color: var(--text-secondary);
        }

        /* Move/Switch Evaluations */
        .evaluation-list {
            max-height: 500px;
            overflow-y: auto;
        }

        .evaluation-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 16px;
            background: rgba(0, 0, 0, 0.2);
            border-radius: 8px;
            margin-bottom: 8px;
            cursor: pointer;
            transition: all 0.2s;
        }

        .evaluation-item:hover {
            background: var(--bg-hover);
        }

        .evaluation-item.selected {
            background: var(--bg-hover);
            border: 1px solid var(--accent-blue);
        }

        .evaluation-item.chosen {
            background: rgba(74, 222, 128, 0.15);
            border: 1px solid var(--accent-green);
        }

        .eval-info {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .eval-icon {
            font-size: 1.4rem;
        }

        .eval-name {
            font-weight: 500;
        }

        .eval-details {
            font-size: 0.8rem;
            color: var(--text-secondary);
        }

        .eval-score {
            font-size: 1.4rem;
            font-weight: 700;
            min-width: 60px;
            text-align: right;
        }

        .eval-score.high { color: var(--accent-green); }
        .eval-score.medium { color: var(--accent-yellow); }
        .eval-score.low { color: var(--accent-red); }

        /* Score breakdown */
        .score-breakdown {
            background: rgba(0, 0, 0, 0.2);
            border-radius: 8px;
            padding: 16px;
            margin-top: 12px;
        }

        .score-step {
            padding: 12px 0;
            border-bottom: 1px solid var(--border-color);
        }

        .score-step:last-child {
            border-bottom: none;
        }

        .score-step-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 4px;
        }

        .score-step-type {
            font-weight: 600;
            font-size: 0.85rem;
            padding: 2px 8px;
            border-radius: 4px;
            background: rgba(74, 158, 255, 0.2);
            color: var(--accent-blue);
        }

        .score-step-type.damage { background: rgba(248, 113, 113, 0.2); color: var(--accent-red); }
        .score-step-type.personality { background: rgba(167, 139, 250, 0.2); color: var(--accent-purple); }
        .score-step-type.status { background: rgba(251, 191, 36, 0.2); color: var(--accent-yellow); }
        .score-step-type.rule { background: rgba(74, 222, 128, 0.2); color: var(--accent-green); }
        .score-step-type.matchup { background: rgba(34, 211, 238, 0.2); color: var(--accent-cyan); }

        .score-step-description {
            font-size: 0.85rem;
            color: var(--text-secondary);
            margin: 6px 0;
            line-height: 1.4;
            padding-left: 8px;
            border-left: 2px solid var(--border-color);
        }

        .score-change {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 0.9rem;
            font-weight: 500;
        }

        .score-change.positive { color: var(--accent-green); }
        .score-change.negative { color: var(--accent-red); }
        .score-change.neutral { color: var(--text-secondary); }

        .score-modifier {
            font-size: 0.8rem;
            padding: 2px 6px;
            background: rgba(0, 0, 0, 0.3);
            border-radius: 4px;
            color: var(--text-muted);
        }

        /* Personality display */
        .personality-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
        }

        .trait {
            background: rgba(0, 0, 0, 0.2);
            border-radius: 8px;
            padding: 12px;
        }

        .trait-name {
            font-size: 0.8rem;
            color: var(--text-secondary);
            margin-bottom: 4px;
        }

        .trait-bar {
            height: 6px;
            background: rgba(255, 255, 255, 0.1);
            border-radius: 3px;
            overflow: hidden;
        }

        .trait-fill {
            height: 100%;
            border-radius: 3px;
            background: var(--accent-purple);
        }

        .trait-value {
            font-size: 0.85rem;
            font-weight: 600;
            margin-top: 4px;
        }

        /* Final decision display */
        .final-decision {
            background: linear-gradient(135deg, rgba(74, 222, 128, 0.1), rgba(74, 158, 255, 0.1));
            border: 2px solid var(--accent-green);
            border-radius: 12px;
            padding: 20px;
            margin-top: 16px;
        }

        .decision-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 12px;
        }

        .decision-title {
            font-size: 1.2rem;
            font-weight: 700;
            color: var(--accent-green);
        }

        .decision-action {
            display: flex;
            align-items: center;
            gap: 12px;
            font-size: 1.3rem;
            font-weight: 600;
        }

        .decision-reasoning {
            color: var(--text-secondary);
            font-size: 0.95rem;
            margin-top: 8px;
            padding-top: 12px;
            border-top: 1px solid var(--border-color);
        }

        /* Decision history sidebar */
        .history-list {
            max-height: 400px;
            overflow-y: auto;
        }

        .history-item {
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 8px;
            background: rgba(0, 0, 0, 0.2);
            cursor: pointer;
            transition: all 0.2s;
        }

        .history-item:hover {
            background: var(--bg-hover);
        }

        .history-item.active {
            background: var(--bg-hover);
            border: 1px solid var(--accent-blue);
        }

        .history-pokemon {
            font-weight: 500;
            margin-bottom: 4px;
        }

        .history-action {
            font-size: 0.85rem;
            color: var(--accent-green);
        }

        .history-time {
            font-size: 0.75rem;
            color: var(--text-muted);
        }

        /* Empty state */
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: var(--text-muted);
        }

        .empty-state-icon {
            font-size: 4rem;
            margin-bottom: 16px;
        }

        /* Scrollbar styling */
        ::-webkit-scrollbar {
            width: 8px;
        }

        ::-webkit-scrollbar-track {
            background: var(--bg-dark);
        }

        ::-webkit-scrollbar-thumb {
            background: var(--border-color);
            border-radius: 4px;
        }

        ::-webkit-scrollbar-thumb:hover {
            background: var(--text-muted);
        }

        /* Responsive */
        @media (max-width: 1200px) {
            .main-grid {
                grid-template-columns: 1fr;
            }
        }

        /* Effectiveness labels */
        .effectiveness {
            font-size: 0.75rem;
            padding: 2px 6px;
            border-radius: 4px;
            font-weight: 600;
        }
        .effectiveness.immune { background: #374151; color: #9ca3af; }
        .effectiveness.not-very { background: rgba(248, 113, 113, 0.2); color: var(--accent-red); }
        .effectiveness.neutral { background: rgba(148, 163, 184, 0.2); color: var(--text-secondary); }
        .effectiveness.super { background: rgba(74, 222, 128, 0.2); color: var(--accent-green); }
        .effectiveness.ultra { background: rgba(74, 222, 128, 0.4); color: var(--accent-green); }

        .damage-preview {
            font-size: 0.8rem;
            color: var(--text-secondary);
            margin-top: 4px;
        }

        .ohko-badge {
            background: var(--accent-red);
            color: white;
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 0.7rem;
            font-weight: 700;
            margin-left: 8px;
        }

        .twohko-badge {
            background: var(--accent-yellow);
            color: #333;
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 0.7rem;
            font-weight: 700;
            margin-left: 8px;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo">
                <h1>Cobblemon AI Debugger</h1>
            </div>
            <div class="controls">
                <div class="status-badge">
                    <div class="status-dot"></div>
                    <span>Connected</span>
                </div>
                <button class="btn btn-secondary" onclick="clearHistory()">Clear History</button>
                <button class="btn btn-primary" onclick="refreshData()">Refresh</button>
            </div>
        </header>

        <div class="main-grid">
            <!-- Left Sidebar: Decision History -->
            <div class="card">
                <div class="card-header">
                    <span class="card-title">Decision History</span>
                    <span id="decisionCount" class="text-muted">0 decisions</span>
                </div>
                <div class="card-body">
                    <div id="historyList" class="history-list">
                        <div class="empty-state">
                            <div class="empty-state-icon">🎮</div>
                            <p>No AI decisions yet</p>
                            <p style="font-size: 0.85rem; margin-top: 8px;">Start a battle with an NPC to see AI thinking!</p>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Main Content -->
            <div id="mainContent">
                <div class="empty-state card">
                    <div class="empty-state-icon">🧠</div>
                    <h2>Waiting for AI Decisions...</h2>
                    <p style="margin-top: 12px;">When a ConfigurableBattleAI makes a decision, you'll see the complete thinking process here.</p>
                </div>
            </div>
        </div>
    </div>

    <script>
        let decisions = [];
        let selectedIndex = 0;
        let autoRefresh = true;
        let expandedBreakdowns = new Set(); // Track which breakdowns are expanded
        let lastDataHash = ''; // Track if data changed

        // Sprite URL helper
        function getSpriteUrl(species, form) {
            let path = species.toLowerCase();
            if (form && form.toLowerCase() !== 'normal' && form.toLowerCase() !== 'base') {
                path += '-' + form.toLowerCase();
            }
            return `https://cobblemon.tools/pokedex/pokemon/${'$'}{path}/sprite.png`;
        }

        // Format time
        function formatTime(timestamp) {
            const date = new Date(timestamp);
            return date.toLocaleTimeString();
        }

        // Get HP bar class
        function getHpClass(percent) {
            if (percent > 50) return 'high';
            if (percent > 25) return 'medium';
            return 'low';
        }

        // Get score class
        function getScoreClass(score, maxScore) {
            const ratio = score / maxScore;
            if (ratio > 0.7) return 'high';
            if (ratio > 0.3) return 'medium';
            return 'low';
        }

        // Get effectiveness label
        function getEffectivenessLabel(effectiveness) {
            if (effectiveness === 0) return { text: 'Immune', class: 'immune' };
            if (effectiveness < 1) return { text: `${'$'}{effectiveness}x`, class: 'not-very' };
            if (effectiveness === 1) return { text: '1x', class: 'neutral' };
            if (effectiveness === 2) return { text: '2x', class: 'super' };
            return { text: `${'$'}{effectiveness}x`, class: 'ultra' };
        }

        // Get step type CSS class
        function getStepTypeClass(type) {
            if (!type) return '';
            const t = type.toUpperCase();
            if (t.includes('DAMAGE') || t.includes('BASE_POWER')) return 'damage';
            if (t.includes('PERSONALITY')) return 'personality';
            if (t.includes('STATUS')) return 'status';
            if (t.includes('RULE') || t.includes('PRIORITY')) return 'rule';
            if (t.includes('MATCHUP') || t.includes('HP')) return 'matchup';
            return '';
        }

        // Format step type for display
        function formatStepType(type) {
            if (!type) return 'Unknown';
            const typeMap = {
                'BASE_POWER': '📊 Base Power',
                'DAMAGE_CALC': '💥 Damage Calc',
                'TYPE_EFFECTIVENESS': '⚡ Type Effectiveness',
                'STAB': '✨ STAB Bonus',
                'STATUS_BIAS': '🎭 Status Bias',
                'MOVE_RULE': '📜 Config Rule',
                'SWITCH_CONDITION': '🔄 Switch Condition',
                'ITEM_RULE': '🎒 Item Rule',
                'ACTION_PRIORITY': '⚡ Priority Rule',
                'PERSONALITY': '🧠 Personality',
                'BIAS_MULTIPLIER': '⚖️ Bias Multiplier',
                'HP_BONUS': '❤️ HP Bonus',
                'MATCHUP_BONUS': '🎯 Matchup',
                'CATEGORY_BONUS': '🏷️ Category',
                'URGENCY': '⏰ Urgency'
            };
            return typeMap[type] || type.replace(/_/g, ' ');
        }

        // Render decision history
        function renderHistory() {
            const container = document.getElementById('historyList');
            const countEl = document.getElementById('decisionCount');

            countEl.textContent = `${'$'}{decisions.length} decisions`;

            if (decisions.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <div class="empty-state-icon">🎮</div>
                        <p>No AI decisions yet</p>
                        <p style="font-size: 0.85rem; margin-top: 8px;">Start a battle with an NPC to see AI thinking!</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = decisions.map((d, i) => `
                <div class="history-item ${'$'}{i === selectedIndex ? 'active' : ''}" onclick="selectDecision(${'$'}{i})">
                    <div class="history-pokemon">
                        ${'$'}{d.activePokemon?.nickname || d.activePokemon?.species || 'Unknown'}'s Turn
                    </div>
                    <div class="history-action">
                        ${'$'}{d.finalDecision ? `${'$'}{d.finalDecision.type}: ${'$'}{d.finalDecision.actionName}` : 'Processing...'}
                    </div>
                    <div class="history-time">Turn ${'$'}{d.turn} • ${'$'}{formatTime(d.timestamp)}</div>
                </div>
            `).join('');
        }

        // Render main decision view
        function renderDecision(decision) {
            if (!decision) {
                document.getElementById('mainContent').innerHTML = `
                    <div class="empty-state card">
                        <div class="empty-state-icon">🧠</div>
                        <h2>Waiting for AI Decisions...</h2>
                        <p style="margin-top: 12px;">When a ConfigurableBattleAI makes a decision, you'll see the complete thinking process here.</p>
                    </div>
                `;
                return;
            }

            const maxScore = Math.max(
                ...decision.moveEvaluations.map(e => e.finalScore),
                ...decision.switchEvaluations.map(e => e.finalScore),
                ...decision.itemEvaluations.map(e => e.finalScore),
                1
            );

            document.getElementById('mainContent').innerHTML = `
                <!-- Battle Context -->
                <div class="card" style="margin-bottom: 20px;">
                    <div class="card-header">
                        <span class="card-title">🎯 Battle Context</span>
                        <span style="color: var(--text-muted);">Turn ${'$'}{decision.turn} • Config: ${'$'}{decision.configId}</span>
                    </div>
                    <div class="card-body">
                        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px;">
                            <!-- Active Pokemon -->
                            <div>
                                <h3 style="margin-bottom: 12px; color: var(--accent-blue);">Your Active Pokemon</h3>
                                ${'$'}{renderPokemonCard(decision.activePokemon, true)}
                            </div>

                            <!-- Opponent -->
                            <div>
                                <h3 style="margin-bottom: 12px; color: var(--accent-red);">Opponent${'$'}{decision.opponents.length > 1 ? 's' : ''}</h3>
                                ${'$'}{decision.opponents.map(p => renderPokemonCard(p, false)).join('')}
                            </div>
                        </div>

                        <!-- Battle State -->
                        ${'$'}{renderBattleState(decision.battleState)}

                        <!-- Personality -->
                        ${'$'}{renderPersonality(decision.personality)}
                    </div>
                </div>

                <!-- Thinking Flow -->
                <div class="card" style="margin-bottom: 20px;">
                    <div class="card-header">
                        <span class="card-title">🧠 AI Thinking Process</span>
                    </div>
                    <div class="card-body">
                        ${'$'}{renderThinkingSteps(decision.thinkingSteps)}
                    </div>
                </div>

                <!-- Evaluations Grid -->
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 20px;">
                    <!-- Move Evaluations -->
                    <div class="card">
                        <div class="card-header">
                            <span class="card-title">⚔️ Move Evaluations</span>
                            <span style="color: var(--text-muted);">${'$'}{decision.moveEvaluations.length} moves</span>
                        </div>
                        <div class="card-body evaluation-list">
                            ${'$'}{decision.moveEvaluations.length > 0 ?
                                decision.moveEvaluations
                                    .sort((a, b) => b.finalScore - a.finalScore)
                                    .map(e => renderMoveEvaluation(e, maxScore, decision.finalDecision)).join('')
                                : '<p class="text-muted">No moves available</p>'}
                        </div>
                    </div>

                    <!-- Switch Evaluations -->
                    <div class="card">
                        <div class="card-header">
                            <span class="card-title">🔄 Switch Evaluations</span>
                            <span style="color: var(--text-muted);">${'$'}{decision.switchEvaluations.length} options</span>
                        </div>
                        <div class="card-body evaluation-list">
                            ${'$'}{decision.switchEvaluations.length > 0 ?
                                decision.switchEvaluations
                                    .sort((a, b) => b.finalScore - a.finalScore)
                                    .map(e => renderSwitchEvaluation(e, maxScore, decision.finalDecision)).join('')
                                : '<p class="text-muted">No switches available</p>'}
                        </div>
                    </div>
                </div>

                <!-- Final Decision -->
                ${'$'}{decision.finalDecision ? renderFinalDecision(decision.finalDecision) : ''}
            `;
        }

        function renderPokemonCard(pokemon, isActive) {
            if (!pokemon) return '<p class="text-muted">Unknown Pokemon</p>';

            const spriteUrl = getSpriteUrl(pokemon.species, pokemon.form);
            const hpClass = getHpClass(pokemon.hpPercent);

            return `
                <div class="pokemon-card">
                    <div class="pokemon-sprite">
                        <img src="${'$'}{spriteUrl}" alt="${'$'}{pokemon.species}" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 96 96%22><text y=%2250%%22 x=%2250%%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 font-size=%2240%22>?</text></svg>'">
                    </div>
                    <div class="pokemon-info">
                        <div class="pokemon-name">${'$'}{pokemon.nickname || pokemon.species}</div>
                        <div class="pokemon-details">
                            Lv. ${'$'}{pokemon.level} ${'$'}{pokemon.status ? `• <span style="color: var(--accent-yellow);">${'$'}{pokemon.status}</span>` : ''}
                        </div>
                        <div class="type-badges">
                            ${'$'}{pokemon.types.map(t => `<span class="type-badge type-${'$'}{t.toLowerCase()}">${'$'}{t}</span>`).join('')}
                        </div>
                        <div class="hp-bar-container">
                            <div class="hp-bar">
                                <div class="hp-bar-fill ${'$'}{hpClass}" style="width: ${'$'}{pokemon.hpPercent}%"></div>
                            </div>
                            <div class="hp-text">${'$'}{pokemon.currentHp} / ${'$'}{pokemon.maxHp} HP (${'$'}{Math.round(pokemon.hpPercent)}%)</div>
                        </div>
                    </div>
                </div>
            `;
        }

        function renderBattleState(state) {
            if (!state) return '';

            const conditions = [];
            if (state.weather) conditions.push(`🌤️ ${'$'}{state.weather}`);
            if (state.terrain) conditions.push(`🏔️ ${'$'}{state.terrain}`);
            if (state.trickRoom) conditions.push(`🔮 Trick Room`);

            if (conditions.length === 0) return '';

            return `
                <div style="margin-top: 16px; padding: 12px; background: rgba(0,0,0,0.2); border-radius: 8px;">
                    <strong>Field Conditions:</strong> ${'$'}{conditions.join(' • ')}
                </div>
            `;
        }

        function renderPersonality(personality) {
            if (!personality) return '';

            return `
                <div style="margin-top: 16px;">
                    <h4 style="margin-bottom: 12px; color: var(--accent-purple);">AI Personality</h4>
                    <div class="personality-grid">
                        ${'$'}{renderTrait('Aggression', personality.aggression)}
                        ${'$'}{renderTrait('Risk Tolerance', personality.riskTolerance)}
                        ${'$'}{renderTrait('Setup Preference', personality.setupPreference)}
                        ${'$'}{renderTrait('Switchiness', personality.switchiness)}
                    </div>
                </div>
            `;
        }

        function renderTrait(name, value) {
            return `
                <div class="trait">
                    <div class="trait-name">${'$'}{name}</div>
                    <div class="trait-bar">
                        <div class="trait-fill" style="width: ${'$'}{value * 100}%"></div>
                    </div>
                    <div class="trait-value">${'$'}{(value * 100).toFixed(0)}%</div>
                </div>
            `;
        }

        function renderThinkingSteps(steps) {
            if (!steps || steps.length === 0) {
                return '<p class="text-muted">No thinking steps recorded</p>';
            }

            return `
                <div class="thinking-flow">
                    ${'$'}{steps.map(step => `
                        <div class="thinking-step">
                            <div class="step-icon ${'$'}{step.phase.toLowerCase().includes('scoring') ? 'scoring' : step.phase.toLowerCase().includes('decision') ? 'decision' : 'context'}">
                                ${'$'}{step.icon || '🔍'}
                            </div>
                            <div class="step-content">
                                <div class="step-title">${'$'}{step.title}</div>
                                <div class="step-details">${'$'}{step.details}</div>
                            </div>
                        </div>
                    `).join('')}
                </div>
            `;
        }

        function renderMoveEvaluation(eval, maxScore, finalDecision) {
            const isChosen = finalDecision && finalDecision.type === 'MOVE' && finalDecision.actionId === eval.moveId;
            const scoreClass = getScoreClass(eval.finalScore, maxScore);

            let damageInfo = '';
            if (eval.damageCalc) {
                const dc = eval.damageCalc;
                const effLabel = getEffectivenessLabel(dc.effectiveness);
                damageInfo = `
                    <div class="damage-preview">
                        <span class="effectiveness ${'$'}{effLabel.class}">${'$'}{effLabel.text}</span>
                        ${'$'}{dc.stab ? '<span style="color: var(--accent-yellow); margin-left: 4px;">STAB</span>' : ''}
                        • ${'$'}{dc.minPercent.toFixed(0)}-${'$'}{dc.maxPercent.toFixed(0)}% dmg
                        ${'$'}{dc.isOHKO ? '<span class="ohko-badge">OHKO</span>' : dc.is2HKO ? '<span class="twohko-badge">2HKO</span>' : ''}
                    </div>
                `;
            }

            const breakdownId = 'move-' + eval.moveId;
            const isExpanded = expandedBreakdowns.has(breakdownId);

            return `
                <div class="evaluation-item ${'$'}{isChosen ? 'chosen' : ''}" onclick="toggleScoreBreakdown(this, '${'$'}{breakdownId}')">
                    <div class="eval-info">
                        <span class="eval-icon type-badge type-${'$'}{eval.moveType.toLowerCase()}" style="padding: 4px 8px;">${'$'}{eval.moveType}</span>
                        <div>
                            <div class="eval-name">${'$'}{eval.moveName} ${'$'}{isExpanded ? '▼' : '▶'}</div>
                            <div class="eval-details">
                                ${'$'}{eval.moveCategory} • Target: ${'$'}{eval.targetName || 'N/A'}
                                ${'$'}{eval.categories.length > 0 ? ` • [${'$'}{eval.categories.join(', ')}]` : ''}
                            </div>
                            ${'$'}{damageInfo}
                        </div>
                    </div>
                    <div class="eval-score ${'$'}{scoreClass}">${'$'}{eval.finalScore.toFixed(1)}</div>
                </div>
                <div id="breakdown-${'$'}{breakdownId}" class="score-breakdown" style="display: ${'$'}{isExpanded ? 'block' : 'none'};">
                    ${'$'}{eval.scoringSteps && eval.scoringSteps.length > 0 ? eval.scoringSteps.map(step => `
                        <div class="score-step">
                            <div class="score-step-header">
                                <span class="score-step-type ${'$'}{getStepTypeClass(step.type)}">${'$'}{formatStepType(step.type)}</span>
                                <span class="score-change ${'$'}{step.scoreAfter > step.scoreBefore ? 'positive' : step.scoreAfter < step.scoreBefore ? 'negative' : 'neutral'}">
                                    ${'$'}{step.scoreBefore.toFixed(1)} → ${'$'}{step.scoreAfter.toFixed(1)}
                                </span>
                            </div>
                            <div class="score-step-description">${'$'}{step.description}</div>
                            <div style="text-align: right;">
                                <span class="score-modifier">${'$'}{step.modifier}</span>
                            </div>
                        </div>
                    `).join('') : '<div class="score-step"><em style="color: var(--text-muted);">No scoring steps recorded</em></div>'}
                    <div class="score-step" style="font-weight: 600; border-top: 2px solid var(--border-color); margin-top: 8px; padding-top: 12px;">
                        <div class="score-step-header">
                            <span style="font-size: 1rem;">Final Score</span>
                            <span class="eval-score ${'$'}{scoreClass}" style="font-size: 1.2rem;">${'$'}{eval.finalScore.toFixed(1)}</span>
                        </div>
                    </div>
                </div>
            `;
        }

        function renderSwitchEvaluation(eval, maxScore, finalDecision) {
            const isChosen = finalDecision && finalDecision.type === 'SWITCH' && finalDecision.actionId === eval.pokemonUuid;
            const scoreClass = getScoreClass(eval.finalScore, maxScore);
            const spriteUrl = getSpriteUrl(eval.species, null);
            const breakdownId = 'switch-' + eval.pokemonUuid;
            const isExpanded = expandedBreakdowns.has(breakdownId);

            return `
                <div class="evaluation-item ${'$'}{isChosen ? 'chosen' : ''}" onclick="toggleScoreBreakdown(this, '${'$'}{breakdownId}')">
                    <div class="eval-info">
                        <img src="${'$'}{spriteUrl}" alt="${'$'}{eval.species}" style="width: 40px; height: 40px; image-rendering: pixelated;" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 40 40%22><text y=%2250%%22 x=%2250%%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 font-size=%2220%22>?</text></svg>'">
                        <div>
                            <div class="eval-name">${'$'}{eval.pokemonName} ${'$'}{isExpanded ? '▼' : '▶'}</div>
                            <div class="eval-details">
                                HP: ${'$'}{eval.hpPercent.toFixed(0)}% • Matchup: ${'$'}{eval.matchupScore >= 0 ? '+' : ''}${'$'}{eval.matchupScore.toFixed(1)}
                            </div>
                            <div class="type-badges" style="margin-top: 4px;">
                                ${'$'}{eval.types.map(t => `<span class="type-badge type-${'$'}{t.toLowerCase()}">${'$'}{t}</span>`).join('')}
                            </div>
                        </div>
                    </div>
                    <div class="eval-score ${'$'}{scoreClass}">${'$'}{eval.finalScore.toFixed(2)}</div>
                </div>
                <div id="breakdown-${'$'}{breakdownId}" class="score-breakdown" style="display: ${'$'}{isExpanded ? 'block' : 'none'};">
                    ${'$'}{eval.scoringSteps && eval.scoringSteps.length > 0 ? eval.scoringSteps.map(step => `
                        <div class="score-step">
                            <div class="score-step-header">
                                <span class="score-step-type ${'$'}{getStepTypeClass(step.type)}">${'$'}{formatStepType(step.type)}</span>
                                <span class="score-change ${'$'}{step.scoreAfter > step.scoreBefore ? 'positive' : step.scoreAfter < step.scoreBefore ? 'negative' : 'neutral'}">
                                    ${'$'}{step.scoreBefore.toFixed(3)} → ${'$'}{step.scoreAfter.toFixed(3)}
                                </span>
                            </div>
                            <div class="score-step-description">${'$'}{step.description}</div>
                            <div style="text-align: right;">
                                <span class="score-modifier">${'$'}{step.modifier}</span>
                            </div>
                        </div>
                    `).join('') : '<div class="score-step"><em style="color: var(--text-muted);">No scoring steps recorded</em></div>'}
                    <div class="score-step" style="font-weight: 600; border-top: 2px solid var(--border-color); margin-top: 8px; padding-top: 12px;">
                        <div class="score-step-header">
                            <span style="font-size: 1rem;">Final Score</span>
                            <span class="eval-score ${'$'}{scoreClass}" style="font-size: 1.2rem;">${'$'}{eval.finalScore.toFixed(2)}</span>
                        </div>
                    </div>
                </div>
            `;
        }

        function renderFinalDecision(decision) {
            const iconMap = {
                'MOVE': '⚔️',
                'SWITCH': '🔄',
                'ITEM': '🎒',
                'PASS': '⏭️',
                'RANDOM': '🎲'
            };

            return `
                <div class="final-decision" style="margin-top: 20px;">
                    <div class="decision-header">
                        <span class="decision-title">Final Decision</span>
                        <span style="color: var(--text-muted);">Score: ${'$'}{decision.score.toFixed(1)} • ${'$'}{decision.alternativesConsidered} alternatives considered</span>
                    </div>
                    <div class="decision-action">
                        <span style="font-size: 2rem;">${'$'}{iconMap[decision.type] || '❓'}</span>
                        <span>${'$'}{decision.type}: ${'$'}{decision.actionName}</span>
                        ${'$'}{decision.targetName ? `<span style="color: var(--text-secondary);">→ ${'$'}{decision.targetName}</span>` : ''}
                    </div>
                    <div class="decision-reasoning">
                        <strong>Reasoning:</strong> ${'$'}{decision.reasoning}
                    </div>
                </div>
            `;
        }

        function toggleScoreBreakdown(element, id) {
            const breakdown = document.getElementById('breakdown-' + id);
            if (breakdown) {
                const isHidden = breakdown.style.display === 'none';
                breakdown.style.display = isHidden ? 'block' : 'none';
                if (isHidden) {
                    expandedBreakdowns.add(id);
                } else {
                    expandedBreakdowns.delete(id);
                }
            }
        }

        function selectDecision(index) {
            selectedIndex = index;
            renderHistory();
            renderDecision(decisions[index]);
        }

        async function refreshData() {
            try {
                const response = await fetch('/api/decisions');
                const newDecisions = await response.json();

                // Create a simple hash to detect changes
                const newHash = JSON.stringify(newDecisions.map(d => d.timestamp));

                // Only re-render if data actually changed
                if (newHash !== lastDataHash) {
                    lastDataHash = newHash;
                    decisions = newDecisions;
                    renderHistory();
                    if (decisions.length > 0 && selectedIndex >= decisions.length) {
                        selectedIndex = 0;
                    }
                    if (decisions.length > 0) {
                        renderDecision(decisions[selectedIndex]);
                    } else {
                        renderDecision(null);
                    }
                }
            } catch (e) {
                console.error('Failed to fetch decisions:', e);
            }
        }

        async function clearHistory() {
            try {
                await fetch('/api/decisions/clear');
                decisions = [];
                selectedIndex = 0;
                renderHistory();
                renderDecision(null);
            } catch (e) {
                console.error('Failed to clear history:', e);
            }
        }

        // Auto-refresh every 2 seconds
        setInterval(() => {
            if (autoRefresh) {
                refreshData();
            }
        }, 2000);

        // Initial load
        refreshData();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
