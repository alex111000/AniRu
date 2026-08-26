package ru.radiationx.anilibria.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderCoreTest {
    @Test
    fun providerIdsAreStableAndUnique() {
        val ids = ProviderId.entries.map { it.wireId }
        assertEquals(ids.size, ids.toSet().size)
        ProviderId.entries.forEach { assertEquals(it, ProviderId.fromWireId(it.wireId)) }
    }

    @Test
    fun titleMatcherHandlesRussianNormalization() {
        assertEquals(100, ProviderTitleMatcher.score("Берсерк", "БЕРСЕРК"))
        assertEquals(100, ProviderTitleMatcher.score("Ёрмунганд", "Ермунганд"))
    }

    @Test
    fun titleMatcherAcceptsStrongAlternateTitleAndRejectsUnrelatedTitle() {
        assertTrue(ProviderTitleMatcher.score("One Piece", "Ван-Пис", "One Piece") >= 85)
        assertTrue(ProviderTitleMatcher.score("One Piece", "Наруто", "Naruto") < 60)
    }

    @Test
    fun providerStreamStableKeyIncludesQualityAndUrl() {
        val a = ProviderStream("https://example.test/a.m3u8", 720, StreamType.HLS)
        val b = ProviderStream("https://example.test/a.m3u8", 1080, StreamType.HLS)
        assertTrue(a.stableKey != b.stableKey)
    }
}
