package com.cobblemon.mod.common.battles.runner

import com.cobblemon.mod.common.Cobblemon
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Class responsible for logging debug messages for the showdown service.
 *
 * @author landonjw
 */
class ShowdownDebugLog {
    val log = mutableListOf<LogMessage>()

    /**
     * Adds a log message to the debug log.
     *
     * @param source The source of the log message.
     * @param message The log message.
     */
    fun add(source: String, message: String) {
        if (!Cobblemon.config.enableDebugKeys) return
        val now = Date()
        val timestamp = SimpleDateFormat("HH:mm:ss").format(now)
        log.add(LogMessage("[$source][${timestamp}]> $message", now))
    }

    /**
     * Clears all log messages from the debug log.
     */
    fun clear() {
        log.clear()
    }

    /**
     * Retrieves log messages after a specified date.
     *
     * @param after The date after which to retrieve log messages. If null, retrieves the last 250 messages.
     * @return A list of log messages.
     */
    fun get(after: Date?): List<String> {
        if (after == null) return log.take(250).map { it.message }
        return log.filter { it.time.after(after) }.map { it.message }
    }

    /**
     * Checks if the provided messages are the start of the log.
     *
     * @param messages The messages to check.
     * @return True if the messages are the start of the log, false otherwise.
     */
    fun isStartOfLog(messages: List<String>): Boolean {
        if (messages.isEmpty() && log.isEmpty()) return true
        return messages.size == log.size && messages.first() == log.first().message
    }

    /**
     * Data class representing a log message.
     *
     * @property message The log message.
     * @property time The time the log message was created.
     */
    data class LogMessage(val message: String, val time: Date)
}
