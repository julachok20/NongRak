package com.lullabyai.app.data.repository

import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.mascot.MascotArt
import com.lullabyai.app.ui.mascot.MascotWardrobe
import com.lullabyai.app.ui.mascot.WardrobeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * One thing the carer just earned — a garment or a companion — to slide in from the top.
 *
 * Queued rather than stacked: two unlocks in one level-up (a scarf and บันนี่) should each get
 * their own moment, not fight for the same banner.
 */
data class UnlockToast(
    val mascotKey: String,
    val headlineTh: String,
    val headlineEn: String,
    val detailTh: String,
    val detailEn: String
) {
    fun headline(isThai: Boolean) = if (isThai) headlineTh else headlineEn
    fun detail(isThai: Boolean) = if (isThai) detailTh else detailEn
}

/**
 * Turns a level-up into banners, once.
 *
 * The first reading of a bond only seeds "we have already seen this level" so a carer who
 * already earned level 4 is not flooded with four toasts the next time they open chat. After
 * that, only the gap between the last announced level and the new one is offered.
 */
object UnlockAnnouncer {

    private val queue = ArrayDeque<UnlockToast>()
    private val _current = MutableStateFlow<UnlockToast?>(null)
    val current: StateFlow<UnlockToast?> = _current.asStateFlow()

    fun offer(toast: UnlockToast) {
        synchronized(this) {
            if (_current.value == null) _current.value = toast
            else queue.addLast(toast)
        }
    }

    fun dismiss() {
        synchronized(this) {
            _current.value = queue.removeFirstOrNull()
        }
    }

    /**
     * Compare [newLevel] to what was last shown for [mascot] and enqueue anything in between.
     *
     * Safe to call after every feed or award: a level that has not moved is a no-op.
     */
    suspend fun consider(settings: SettingsManager, mascot: String, newLevel: Int) {
        val last = settings.announcedUnlockLevel(mascot)
        if (last == null) {
            settings.setAnnouncedUnlockLevel(mascot, newLevel)
        } else if (newLevel > last) {
            itemsBetween(last, newLevel).forEach { offer(garmentToast(mascot, it)) }
            settings.setAnnouncedUnlockLevel(mascot, newLevel)
        }

        val lastBest = settings.announcedBestLevel()
        val best = max(lastBest ?: newLevel, newLevel)
        if (lastBest == null) {
            settings.setAnnouncedBestLevel(best)
        } else if (best > lastBest) {
            MascotWardrobe.skinsUnlockedBetween(lastBest, best).forEach { offer(companionToast(it)) }
            settings.setAnnouncedBestLevel(best)
        }
    }

    private fun itemsBetween(from: Int, to: Int): List<WardrobeItem> =
        MascotWardrobe.itemsUnlockedBetween(from, to)

    private fun garmentToast(mascot: String, item: WardrobeItem) = UnlockToast(
        mascotKey = mascot,
        headlineTh = "ปลดล็อกแล้ว!",
        headlineEn = "You unlocked this!",
        detailTh = "${item.nameTh} · ระดับ ${item.requiredLevel}",
        detailEn = "${item.nameEn} · Lv ${item.requiredLevel}"
    )

    private fun companionToast(skin: MascotArt) = UnlockToast(
        mascotKey = skin.key,
        headlineTh = "เพื่อนใหม่!",
        headlineEn = "New companion!",
        detailTh = "${MascotWardrobe.skinName(skin, true)} พร้อมคุยแล้ว · ระดับ ${MascotWardrobe.skinRequiredLevel(skin)}",
        detailEn = "${MascotWardrobe.skinName(skin, false)} is ready · Lv ${MascotWardrobe.skinRequiredLevel(skin)}"
    )
}
