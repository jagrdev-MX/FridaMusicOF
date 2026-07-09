package com.jagr.fridamusic.domain.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayDiversityTest {
    @Test
    fun baseTitleKey_groupsCommonVariants() {
        val original = AutoplayDiversity.baseTitleKey("Neon Drive")
        val slowed = AutoplayDiversity.baseTitleKey("Neon Drive - Slowed + Reverb")
        val sped = AutoplayDiversity.baseTitleKey("Neon Drive (Sped Up Version)")
        val video = AutoplayDiversity.baseTitleKey("Neon Drive [Official Audio]")

        assertEquals(original, slowed)
        assertEquals(original, sped)
        assertEquals(original, video)
    }

    @Test
    fun variantTokens_detectsVariantLabels() {
        val variants = AutoplayDiversity.variantTokens("Midnight Signal - Slowed + Reverb Remix")

        assertTrue("Expected slowed token", "slowed" in variants)
        assertTrue("Expected reverb token", "reverb" in variants)
        assertTrue("Expected remix token", "remix" in variants)
    }

    @Test
    fun styleTokens_keepContextForRequestedGenres() {
        val styles = AutoplayDiversity.styleTokens("Brazilian Phonk Funk Anime Edit Slowed Pop Rock")

        assertTrue("Expected phonk context", "phonk" in styles)
        assertTrue("Expected funk context", "funk" in styles)
        assertTrue("Expected anime context", "anime" in styles)
        assertTrue("Expected slowed context", "slowed" in styles)
        assertTrue("Expected pop context", "pop" in styles)
        assertTrue("Expected rock context", "rock" in styles)
    }

    @Test
    fun diversifySequence_movesRepeatedVariantsBehindDifferentTitles() {
        val songs = listOf(
            "Neon Drive (Slowed)",
            "Neon Drive (Super Slowed)",
            "Neon Drive",
            "Night Run",
            "Skyline Funk",
            "Night Run Remix"
        )

        val diversified = AutoplayDiversity.diversifyByBaseTitle(songs, titleSelector = { it })

        assertEquals("Neon Drive (Slowed)", diversified[0])
        assertEquals("Night Run", diversified[1])
        assertEquals("Skyline Funk", diversified[2])
        assertEquals("Night Run Remix", diversified[3])
    }

    @Test
    fun autoplayTrackCandidate_rejectsRadiosAndLongContinuousContent() {
        assertFalse(AutoplayDiversity.isAutoplayTrackCandidate("24/7 jazz / funk / soul radio"))
        assertFalse(AutoplayDiversity.isAutoplayTrackCandidate("Funk mix", 80 * 60 * 1000L))
        assertTrue(AutoplayDiversity.isAutoplayTrackCandidate("Montagem Monarca - Radio Edit"))
    }

    @Test
    fun compatibleWithAnchor_rejectsGenreDriftFromFunkToSoulOrJazz() {
        assertTrue(AutoplayDiversity.isCompatibleWithAnchor(setOf("funk"), setOf("funk", "phonk")))
        assertFalse(AutoplayDiversity.isCompatibleWithAnchor(setOf("funk"), setOf("funk", "soul")))
        assertFalse(AutoplayDiversity.isCompatibleWithAnchor(setOf("funk"), setOf("jazz")))
    }

    @Test
    fun sameTitleFamily_groupsMashupsAndVersionsWithoutGroupingDifferentMontagens() {
        assertTrue(
            AutoplayDiversity.isSameTitleFamily(
                "MONTAGEM MONARCA",
                "MONTAGEM ALQUIMIA x MONTAGEM MONARCA - Mashup - h6itam"
            )
        )
        assertTrue(
            AutoplayDiversity.isSameTitleFamily(
                "MONTAGEM MONARCA",
                "h6itam - MONTAGEM MONARCA (SUPER SLOWED) | Visualizer"
            )
        )
        assertFalse(
            AutoplayDiversity.isSameTitleFamily(
                "MONTAGEM MONARCA",
                "MONTAGEM ALQUIMIA (SLOWED)"
            )
        )
    }
}
