package com.lullabyai.app.util

import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen says who is in this family. Getting it wrong shows a hired nanny to the whole
 * household as a relative, so the rule is pinned here rather than trusted to a composable.
 */
class FamilyRosterTest {

    private fun member(
        id: String,
        role: String = "co_owner",
        isFamily: Boolean = true,
        isCaregiver: Boolean = false,
        name: String = id
    ) = ChildAccessMember(
        userId = id, role = role, displayName = name,
        isFamily = isFamily, isCaregiver = isCaregiver
    )

    @Test
    fun `a nanny never appears, however her role is spelled`() {
        val members = listOf(
            member("u1", role = "co_owner", isFamily = true),
            member("u2", role = "nanny", isFamily = false, isCaregiver = true),
            // A family that invented its own role for a second helper. A role blacklist would have
            // let this one through as family; the explicit flag does not.
            member("u3", role = "custom-7f3a", isFamily = false, isCaregiver = true)
        )
        assertEquals(listOf("u1"), FamilyRoster.of(listOf(members)).map { it.userId })
    }

    /** Being family and being a caregiver are different questions; a grandmother is often both. */
    @Test
    fun `a caregiver who is also family still appears`() {
        val granny = member("u9", role = "grandparent", isFamily = true, isCaregiver = true)
        assertEquals(listOf("u9"), FamilyRoster.of(listOf(listOf(granny))).map { it.userId })
    }

    /** A custom role nobody could have predicted is family when the flag says so. */
    @Test
    fun `an unknown role is family when the flag says it is`() {
        val aunt = member("u4", role = "custom-aunt", isFamily = true)
        assertEquals(listOf("u4"), FamilyRoster.of(listOf(listOf(aunt))).map { it.userId })
    }

    @Test
    fun `somebody on two children is listed once`() {
        val dadOnA = member("u1", name = "พ่อ")
        val dadOnB = member("u1", name = "พ่อ")
        val mum = member("u2", name = "แม่")
        val roster = FamilyRoster.of(listOf(listOf(dadOnA, mum), listOf(dadOnB)))
        assertEquals(listOf("u1", "u2"), roster.map { it.userId })
    }

    @Test
    fun `no children and no members produce an empty list, not a crash`() {
        assertTrue(FamilyRoster.of(emptyList()).isEmpty())
        assertTrue(FamilyRoster.of(listOf(emptyList())).isEmpty())
    }

    @Test
    fun `a blank user id is skipped rather than collapsing everyone into one row`() {
        val ghost = member("", name = "?")
        val real = member("u1")
        assertEquals(listOf("u1"), FamilyRoster.of(listOf(listOf(ghost, real))).map { it.userId })
    }

    @Test
    fun `a named family uses its own name`() {
        val p = Profile(id = "p", username = "มาลี", familyName = "บ้านสุขใจ")
        assertEquals("บ้านสุขใจ", FamilyRoster.name(p, isThai = true))
        assertEquals("บ้านสุขใจ", FamilyRoster.name(p, isThai = false))
    }

    @Test
    fun `an unnamed family falls back to the username, never to a blank heading`() {
        val p = Profile(id = "p", username = "มาลี")
        assertEquals("ครอบครัวมาลี", FamilyRoster.name(p, isThai = true))
        assertEquals("มาลี family", FamilyRoster.name(p, isThai = false))
    }

    @Test
    fun `whitespace is not a family name`() {
        val p = Profile(id = "p", username = "มาลี", familyName = "   ")
        assertEquals("ครอบครัวมาลี", FamilyRoster.name(p, isThai = true))
    }

    @Test
    fun `no profile at all still produces a heading`() {
        assertEquals("ครอบครัวของเรา", FamilyRoster.name(null, isThai = true))
        assertEquals("Our family", FamilyRoster.name(null, isThai = false))
    }
}
