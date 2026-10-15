/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
*/

package me.kavishdevar.librepods.utils

/** Removes identifiers and raw protocol material before diagnostics are shown or exported. */
internal object SensitiveLogRedactor {
    private val bluetoothAddressPattern =
        Regex("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![0-9a-f])")
    private val serialPattern =
        Regex("(?i)(serial(?:number)?|leftSerialNumber|rightSerialNumber|\\bSN)(\\s*[=:]\\s*)[^,\\s]+")
    private val proximityKeyPattern =
        Regex("(?i)(IRK|ENC_KEY|encryption[ _-]?key|proximity[ _-]?key)(\\s*[=:]\\s*)[A-Za-z0-9+/=_-]+")
    private val rawHexPayloadPattern =
        Regex("(?i)(?:(?:[0-9a-f]{2})\\s+){7,}[0-9a-f]{2}")

    fun redact(line: String): String = line
        .replace(bluetoothAddressPattern, "<redacted-bt-address>")
        .replace(serialPattern) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
        }
        .replace(proximityKeyPattern) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
        }
        .replace(rawHexPayloadPattern, "<redacted-binary-payload>")
}
