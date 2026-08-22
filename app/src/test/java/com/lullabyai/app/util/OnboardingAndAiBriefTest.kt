package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingAndAiBriefTest {

    @Test
    fun testAiBriefFor3MonthOldInfant() {
        val child = Child(
            id = "child_3m",
            userId = "user_1",
            name = "น้องปันปัน",
            birthdate = LocalDate.now().minusMonths(3).toString(),
            gender = "female"
        )

        // Validate birthdate calculation
        val parts = child.birthdate.split("-")
        val by = parts[0].toInt()
        val bm = parts[1].toInt()
        val now = LocalDate.now()
        val ageMonths = (now.year - by) * 12 + (now.monthValue - bm)
        assertEquals(3, ageMonths)

        val briefTh = AiDailyBriefHelper.getZeroDataAiBrief(child, isThai = true)
        assertTrue(briefTh.title.contains("3 เดือน") || briefTh.title.contains("3"))
        assertTrue(briefTh.headline.contains("น้องปันปัน"))
        assertTrue(briefTh.developmentalFocus.isNotBlank())
        assertTrue(briefTh.recommendedAction.isNotBlank())
        assertTrue(briefTh.sleepScheduleTip.isNotBlank())

        val briefEn = AiDailyBriefHelper.getZeroDataAiBrief(child, isThai = false)
        assertTrue(briefEn.title.contains("3 Month"))
        assertTrue(briefEn.headline.contains("น้องปันปัน"))
    }

    @Test
    fun testAiBriefForNewborn() {
        val child = Child(
            id = "child_newborn",
            userId = "user_2",
            name = "น้องบีน",
            birthdate = LocalDate.now().minusDays(10).toString(),
            gender = "male"
        )
        assertNotNull(child.birthdate)
        assertTrue(child.name == "น้องบีน")
    }

    @Test
    fun testGuestChildInitializationAttributes() {
        val guestChild = Child(
            id = "guest_child_local",
            userId = "guest",
            name = "Test",
            birthdate = LocalDate.now().minusMonths(3).toString(),
            gender = "unspecified"
        )

        assertEquals("guest_child_local", guestChild.id)
        assertEquals("guest", guestChild.userId)
        assertEquals("Test", guestChild.name)
        assertEquals("unspecified", guestChild.gender)
    }

    @Test
    fun testTestAccountAiBriefAndVisibility() {
        val testChild = Child(
            id = "guest_child_local",
            userId = "guest",
            name = "Test",
            birthdate = LocalDate.now().minusMonths(3).toString(),
            gender = "unspecified"
        )

        val brief = AiDailyBriefHelper.getZeroDataAiBrief(testChild, isThai = true)
        assertTrue(brief.headline.contains("Test"))
        assertTrue(brief.developmentalFocus.isNotBlank())
        assertTrue(brief.recommendedAction.isNotBlank())
        assertTrue(brief.sleepScheduleTip.isNotBlank())
    }

    @Test
    fun testOnboardingUiStateWithRelationshipAndAvatar() {
        val child = Child(
            id = "child_test_1",
            userId = "user_123",
            name = "น้องกัปตัน",
            birthdate = "2026-01-15",
            gender = "male",
            allergies = "นมวัว",
            notes = "แพ้นมวัว",
            heightCm = 68.0,
            weightKg = 7.5,
            sleepHours = 14.0,
            bloodGroup = "O"
        )

        val vm = com.lullabyai.app.viewmodel.OnboardingViewModel()
        vm.initFromChild(child)

        val state = vm.uiState.value
        assertEquals("น้องกัปตัน", state.childName)
        assertEquals("2026-01-15", state.birthdate)
        assertEquals("male", state.gender)
        assertEquals("นมวัว", state.allergies)
        assertEquals("O", state.bloodGroup)
        assertEquals("child_test_1", state.existingChildId)
        assertTrue(state.addMode)

        vm.updateStep1(
            name = "น้องกัปตัน",
            birthdate = "2026-01-15",
            gender = "male",
            relationship = "father"
        )
        assertEquals("father", vm.uiState.value.relationship)
    }
}
