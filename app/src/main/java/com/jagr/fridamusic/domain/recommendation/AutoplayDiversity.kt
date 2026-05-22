package com.jagr.fridamusic.domain.recommendation

import com.jagr.fridamusic.domain.model.Song
import java.text.Normalizer

object AutoplayDiversity {
    private val variantGroups = linkedMapOf(
        "slowed" to listOf("super slowed", "ultra slowed", "slowed and reverb", "slowed reverb", "slow reverb", "slowed"),
        "sped" to listOf("sped up", "speed up", "nightcore"),
        "remix" to listOf("remix", "rmx", "bootleg", "mashup", "vip mix"),
        "reverb" to listOf("reverb"),
        "edit" to listOf("radio edit", "edit", "version", "ver"),
        "bass" to listOf("bass boosted", "boosted bass"),
        "live" to listOf("live", "concert", "session"),
        "cover" to listOf("cover"),
        "instrumental" to listOf("instrumental", "karaoke"),
        "loop" to listOf("loop", "looped", "extended"),
        "official" to listOf("official audio", "official video", "music video", "visualizer", "lyrics", "lyric video"),
        "acoustic" to listOf("acoustic", "unplugged"),
        "remaster" to listOf("remaster", "remastered"),
        "quality" to listOf("8d", "hd", "hq")
    )

    private val variantWords = setOf(
        "slowed",
        "super",
        "ultra",
        "sped",
        "nightcore",
        "remix",
        "rmx",
        "reverb",
        "edit",
        "version",
        "ver",
        "bootleg",
        "mashup",
        "vip",
        "boosted",
        "live",
        "concert",
        "session",
        "cover",
        "instrumental",
        "karaoke",
        "loop",
        "looped",
        "extended",
        "official",
        "audio",
        "video",
        "visualizer",
        "lyrics",
        "lyric",
        "acoustic",
        "unplugged",
        "remaster",
        "remastered",
        "clean",
        "explicit",
        "hd",
        "hq",
        "8d"
    )

    private val styleGroups = linkedMapOf(
        "phonk" to listOf("phonk", "phonky", "drift phonk", "drift"),
        "funk" to listOf("funk", "brazilian funk", "brasil funk", "funk carioca", "mandelao", "montagem"),
        "slowed" to listOf("slowed", "slowed reverb", "slow reverb"),
        "sped" to listOf("sped up", "speed up", "nightcore"),
        "anime" to listOf("anime", "amv", "opening", "ending", "ost", "jpop", "j pop", "jrock", "j rock"),
        "lofi" to listOf("lofi", "lo fi", "chillhop"),
        "chill" to listOf("chill", "relax", "dreamy", "ambient"),
        "dark" to listOf("dark", "night", "sad", "melancholic", "melancholy"),
        "trap" to listOf("trap", "drill", "808"),
        "rap" to listOf("rap", "hip hop", "hiphop"),
        "rock" to listOf("rock", "alt rock", "indie rock", "punk"),
        "metal" to listOf("metal", "metalcore", "deathcore"),
        "electronic" to listOf("edm", "house", "techno", "electro", "synthwave", "wave"),
        "latin" to listOf("reggaeton", "salsa", "bachata", "corrido", "latin"),
        "pop" to listOf("pop", "dance pop", "synth pop"),
        "dance" to listOf("dance", "club", "party"),
        "aggressive" to listOf("aggressive", "rage", "hard", "gym", "workout")
    )

    private val variantPhrases = variantGroups.values.flatten().sortedByDescending { it.length }

    fun baseTitleKey(song: Song): String = baseTitleKey(song.title)

    fun baseTitleKey(title: String): String {
        var text = normalizeLoose(title)
            .replace(Regex("\\b(feat|ft|featuring)\\b\\.?"), " ")

        variantPhrases.forEach { phrase ->
            text = text.replace(phraseRegex(phrase), " ")
        }

        return text
            .split(" ")
            .filter { token -> token.isNotBlank() && token !in variantWords }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun variantTokens(song: Song): Set<String> = variantTokens("${song.title} ${song.album}")

    fun variantTokens(text: String): Set<String> {
        val normalized = normalizeLoose(text)
        return variantGroups.mapNotNullTo(linkedSetOf()) { (variant, phrases) ->
            variant.takeIf { phrases.any { phrase -> phraseRegex(phrase).containsMatchIn(normalized) } }
        }
    }

    fun styleTokens(song: Song): Set<String> = styleTokens("${song.title} ${song.artist} ${song.album}")

    fun styleTokens(text: String): Set<String> {
        val normalized = normalizeLoose(text)
        return styleGroups.mapNotNullTo(linkedSetOf()) { (style, aliases) ->
            style.takeIf { aliases.any { alias -> phraseRegex(alias).containsMatchIn(normalized) } }
        }
    }

    fun diversifySequence(
        songs: List<Song>,
        maxPerBaseTitle: Int = 2,
        maxForCurrentBaseTitle: Int = 1
    ): List<Song> = diversifyByBaseTitle(
        items = songs,
        titleSelector = { it.title },
        maxPerBaseTitle = maxPerBaseTitle,
        maxForCurrentBaseTitle = maxForCurrentBaseTitle
    )

    fun <T> diversifyByBaseTitle(
        items: List<T>,
        titleSelector: (T) -> String,
        maxPerBaseTitle: Int = 2,
        maxForCurrentBaseTitle: Int = 1
    ): List<T> {
        if (items.size <= 2) return items

        val first = items.first()
        val firstBase = baseTitleKey(titleSelector(first))
        val selected = mutableListOf(first)
        val delayed = mutableListOf<T>()
        val baseCounts = mutableMapOf<String, Int>()
        if (firstBase.isNotBlank()) baseCounts[firstBase] = 1
        var lastBase = firstBase

        items.drop(1).forEach { item ->
            val base = baseTitleKey(titleSelector(item))
            val count = baseCounts[base] ?: 0
            val maxForBase = if (base.isNotBlank() && base == firstBase) {
                maxForCurrentBaseTitle
            } else {
                maxPerBaseTitle
            }
            val isConsecutiveVariant = base.isNotBlank() && base == lastBase
            val canPromote = base.isBlank() || (count < maxForBase && !isConsecutiveVariant)

            if (canPromote) {
                selected += item
                if (base.isNotBlank()) {
                    baseCounts[base] = count + 1
                    lastBase = base
                }
            } else {
                delayed += item
            }
        }

        return selected + delayed
    }

    private fun phraseRegex(phrase: String): Regex =
        Regex("\\b${Regex.escape(phrase)}\\b")

    private fun normalizeLoose(value: String?): String {
        val withoutMarks = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return withoutMarks
            .lowercase()
            .replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
