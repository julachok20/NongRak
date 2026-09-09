package com.lullabyai.app.ui.mascot

import androidx.annotation.DrawableRes
import com.lullabyai.app.R

/**
 * What a companion can wear, and how far it has to be raised first.
 *
 * The catalogue lives in the app rather than the database on purpose: `mascot_bonds.equipped` is
 * free-form `{slot: itemId}`, so adding a garment is a drawable and a line here, never a
 * migration. The database only guarantees a carer can dress nobody else's companion.
 *
 * Art is per skin. The four mascots are drawn on different canvases (715, 680, 577 and 620 px
 * square), so one shirt bitmap cannot fit them all — an item holds one drawable per skin, and is
 * simply unavailable for a skin it has not been drawn for.
 */
enum class WardrobeSlot {
    /** Worn on the body. One at a time. */
    SHIRT,
    /** Around the neck. Draws over the shirt. */
    SCARF,
    /** On the head. Draws last so it sits above everything. */
    HAT,
    /** Pin, bow, badge. One at a time. */
    ACCESSORY
}

/**
 * One wearable.
 *
 * @param requiredLevel bond level, from `mascot_level(xp)` — the same number the companion screen
 *   already shows, so "level 3" means one thing everywhere.
 * @param art drawable per [MascotArt]; a skin missing from this map cannot wear the item.
 */
data class WardrobeItem(
    val id: String,
    val slot: WardrobeSlot,
    val nameTh: String,
    val nameEn: String,
    val requiredLevel: Int,
    val art: Map<MascotArt, Int>
) {
    fun nameFor(isThai: Boolean) = if (isThai) nameTh else nameEn

    /** The drawable to layer for [skin], or null when this item was never drawn for it. */
    @DrawableRes
    fun artFor(skin: MascotArt): Int? = art[skin]

    fun unlockedAt(level: Int) = level >= requiredLevel
}

object MascotWardrobe {

    private fun skins(
        @DrawableRes nongrak: Int,
        @DrawableRes bunny: Int,
        @DrawableRes coco: Int,
        @DrawableRes pingu: Int
    ) = mapOf(
        MascotArt.NONGRAK to nongrak,
        MascotArt.BUNNY to bunny,
        MascotArt.COCO to coco,
        MascotArt.PINGU to pingu
    )

    /**
     * Every wearable in the app.
     *
     * Overlays live in `drawable-nodpi/wear_<id>_<skin>.png`, same canvas as that skin's
     * `mascot_<skin>.png`. They go through the same skinned mesh as the body.
     *
     * Level 1 is on purpose: a new carer opening the closet should find something to put on,
     * not a locked wall. Higher pieces unlock as the bond climbs — same numbers as companion
     * unlocks (บันนี่ 2, โคโค่ 3, พิงกุ 4) so a level-up can hand over clothes and a friend together.
     */
    val items: List<WardrobeItem> = listOf(
        WardrobeItem(
            "bow", WardrobeSlot.ACCESSORY, "โบว์ชมพู", "Pink bow", 1,
            skins(R.drawable.wear_bow_nongrak, R.drawable.wear_bow_bunny, R.drawable.wear_bow_coco, R.drawable.wear_bow_pingu)
        ),
        WardrobeItem(
            "scarf", WardrobeSlot.SCARF, "ผ้าพันคอฟ้า", "Blue scarf", 2,
            skins(R.drawable.wear_scarf_nongrak, R.drawable.wear_scarf_bunny, R.drawable.wear_scarf_coco, R.drawable.wear_scarf_pingu)
        ),
        WardrobeItem(
            "flower", WardrobeSlot.HAT, "หมวกดอกไม้", "Flower hat", 3,
            skins(R.drawable.wear_flower_nongrak, R.drawable.wear_flower_bunny, R.drawable.wear_flower_coco, R.drawable.wear_flower_pingu)
        ),
        WardrobeItem(
            "star", WardrobeSlot.ACCESSORY, "ดาวติดอก", "Star pin", 4,
            skins(R.drawable.wear_star_nongrak, R.drawable.wear_star_bunny, R.drawable.wear_star_coco, R.drawable.wear_star_pingu)
        ),
        WardrobeItem(
            "party", WardrobeSlot.SHIRT, "เสื้องานเลี้ยง", "Party shirt", 5,
            skins(R.drawable.wear_party_nongrak, R.drawable.wear_party_bunny, R.drawable.wear_party_coco, R.drawable.wear_party_pingu)
        ),
        WardrobeItem(
            "crown", WardrobeSlot.HAT, "มงกุฎทอง", "Gold crown", 6,
            skins(R.drawable.wear_crown_nongrak, R.drawable.wear_crown_bunny, R.drawable.wear_crown_coco, R.drawable.wear_crown_pingu)
        )
    )

    fun slot(slot: WardrobeSlot): List<WardrobeItem> = items.filter { it.slot == slot }

    fun byId(id: String?): WardrobeItem? = id?.let { wanted -> items.firstOrNull { it.id == wanted } }

    /** Pieces that become wearable when the bond climbs from [fromLevel] to [toLevel], exclusive of [fromLevel]. */
    fun itemsUnlockedBetween(fromLevel: Int, toLevel: Int): List<WardrobeItem> =
        items.filter { it.requiredLevel in (fromLevel + 1)..toLevel }

    /** Companions that become switchable when the best bond climbs from [fromBest] to [toBest]. */
    fun skinsUnlockedBetween(fromBest: Int, toBest: Int): List<MascotArt> =
        MascotArt.entries.filter { skinRequiredLevel(it) in (fromBest + 1)..toBest }

    /**
     * Resolves what is actually worn into drawables to layer.
     *
     * Silently drops anything unknown, not drawn for this skin, or above the current level. That
     * matters: a level is per companion, so switching to a less-raised companion must not leave
     * it wearing something it has not earned, and an item removed from a future build must not
     * crash a save that still names it.
     */
    fun garmentsFor(
        skin: MascotArt,
        equipped: Map<String, String>,
        level: Int
    ): List<Int> = WardrobeSlot.entries.mapNotNull { slot ->
        val item = byId(equipped[slot.name.lowercase()]) ?: return@mapNotNull null
        if (item.slot != slot || !item.unlockedAt(level)) return@mapNotNull null
        item.artFor(skin)
    }

    /**
     * Which companions the carer may switch to.
     *
     * Levels are per companion, so gating a companion behind ITS OWN level would lock every one
     * of them at level 1 forever. The gate is the best bond the carer has raised anywhere:
     * raising one companion is what earns the next.
     */
    fun skinRequiredLevel(skin: MascotArt): Int = when (skin) {
        MascotArt.NONGRAK -> 1
        MascotArt.BUNNY -> 2
        MascotArt.COCO -> 3
        MascotArt.PINGU -> 4
    }

    fun skinUnlocked(skin: MascotArt, bestLevel: Int): Boolean =
        bestLevel >= skinRequiredLevel(skin)

    fun skinName(skin: MascotArt, isThai: Boolean): String = when (skin) {
        MascotArt.NONGRAK -> if (isThai) "น้องรัก" else "NongRak"
        MascotArt.BUNNY -> if (isThai) "บันนี่" else "Bunny"
        MascotArt.COCO -> if (isThai) "โคโค่" else "Coco"
        MascotArt.PINGU -> if (isThai) "พิงกุ" else "Pingu"
    }

    /**
     * Total XP needed to be at [level].
     *
     * Mirrors Postgres `mascot_level(xp) = floor(sqrt(xp/25)) + 1`, inverted. The database stays
     * the authority on what level something IS; this only says how far the next one is, so a
     * progress bar can be drawn without a round trip. MascotWardrobeTest checks the two agree.
     */
    fun xpForLevel(level: Int): Int {
        val l = (level - 1).coerceAtLeast(0)
        return 25 * l * l
    }

    /** 0f..1f through the current level. */
    fun progressWithinLevel(xp: Int, level: Int): Float {
        val floor = xpForLevel(level)
        val ceil = xpForLevel(level + 1)
        if (ceil <= floor) return 0f
        return ((xp - floor).toFloat() / (ceil - floor)).coerceIn(0f, 1f)
    }
}
