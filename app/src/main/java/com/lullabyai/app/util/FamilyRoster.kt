package com.lullabyai.app.util

import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.Profile

/**
 * Who appears under ครอบครัว on the home screen, and what that card is called.
 *
 * Pure, so the one rule that matters can be tested: a nanny never appears here. `is_family` is set
 * deliberately when a membership is approved and is never derived from the role name - role is
 * partly free-form (custom roles live in `child_roles`), so a whitelist would hide a real aunt
 * filed under a custom role, and a blacklist would show a second nanny as family.
 *
 * Note this is NOT the same question as `is_caregiver`. A nanny genuinely cares for the child and
 * belongs in the caregiver chat row and the handover screens; she is simply not family.
 */
object FamilyRoster {

    /**
     * The family across every child.
     *
     * `list_child_access_members` is per child, so a parent who can see two children gets two
     * lists with the same people in them. First occurrence wins - the rows are identical apart
     * from which child they came from, and showing somebody twice is the obvious bug here.
     */
    fun of(perChild: List<List<ChildAccessMember>>): List<ChildAccessMember> {
        val seen = LinkedHashMap<String, ChildAccessMember>()
        perChild.forEach { members ->
            members.forEach { m ->
                if (m.isFamily && m.userId.isNotBlank() && m.userId !in seen) seen[m.userId] = m
            }
        }
        return seen.values.toList()
    }

    /**
     * What the card is called.
     *
     * A family that has not named itself gets a default built from the username rather than a
     * blank heading. Falls back again when there is no username either, so the card always has a
     * title - an empty heading above a list of people reads as a loading bug.
     */
    fun name(profile: Profile?, isThai: Boolean): String {
        profile?.familyName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val who = profile?.username?.trim()?.takeIf { it.isNotEmpty() }
        return if (isThai) {
            who?.let { "ครอบครัว$it" } ?: "ครอบครัวของเรา"
        } else {
            who?.let { "$it family" } ?: "Our family"
        }
    }
}
