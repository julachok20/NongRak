package com.lullabyai.app.viewmodel

import com.lullabyai.app.data.repository.ChildRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The one check that matters on this path: a backend failure must never reach the scanner's screen
 * with the child id, the requester's user id, or the invite token still in it.
 */
class AccessRequestErrorTest {

    @Test
    fun `known backend failures map to stable keys`() {
        assertEquals(
            AccessRequestViewModel.ERR_REQUEST_REJECTED,
            AccessRequestViewModel.safeError(RuntimeException("request_rejected"))
        )
        assertEquals(
            AccessRequestViewModel.ERR_REQUEST_REJECTED,
            AccessRequestViewModel.safeError(RuntimeException("request rejected"))
        )
        assertEquals(
            AccessRequestViewModel.ERR_ALREADY_PENDING,
            AccessRequestViewModel.safeError(RuntimeException("already_pending"))
        )
        assertEquals(
            AccessRequestViewModel.ERR_ALREADY_PENDING,
            AccessRequestViewModel.safeError(RuntimeException("already pending"))
        )
        assertEquals(
            AccessRequestViewModel.ERR_INVALID_CODE,
            AccessRequestViewModel.safeError(RuntimeException("invalid or expired code"))
        )
        assertEquals(
            AccessRequestViewModel.ERR_ALREADY_MEMBER,
            AccessRequestViewModel.safeError(RuntimeException("already a member"))
        )
        assertEquals(
            AccessRequestViewModel.ERR_ALREADY_RESOLVED,
            AccessRequestViewModel.safeError(RuntimeException("already resolved"))
        )
        // 'not authorized' must not tell the caller the request exists.
        assertEquals(
            AccessRequestViewModel.ERR_NOT_FOUND,
            AccessRequestViewModel.safeError(RuntimeException("not authorized"))
        )
    }

    @Test
    fun `rejection lock and already pending messages are properly localized`() {
        val rejectedTh = AccessRequestStrings.error(AccessRequestViewModel.ERR_REQUEST_REJECTED, isThai = true)
        val rejectedEn = AccessRequestStrings.error(AccessRequestViewModel.ERR_REQUEST_REJECTED, isThai = false)
        assertNotNull(rejectedTh)
        assertNotNull(rejectedEn)
        assertEquals("คำขอเชื่อมข้อมูลลูกคนนี้ถูกปฏิเสธโดยผู้ปกครองแล้ว ไม่สามารถส่งคำขอซ้ำได้", rejectedTh)
        assertEquals("This sync request was declined by the parent and cannot be requested again", rejectedEn)

        val pendingTh = AccessRequestStrings.error(AccessRequestViewModel.ERR_ALREADY_PENDING, isThai = true)
        val pendingEn = AccessRequestStrings.error(AccessRequestViewModel.ERR_ALREADY_PENDING, isThai = false)
        assertNotNull(pendingTh)
        assertNotNull(pendingEn)
        assertEquals("คุณได้ส่งคำขอเชื่อมข้อมูลสำหรับเด็กคนนี้ไปแล้ว อยู่ระหว่างรอการอนุมัติ", pendingTh)
        assertEquals("A sync request for this child is already pending parent approval", pendingEn)
    }

    @Test
    fun `identifiers in a raw failure never survive into user-facing text`() {
        val leaky = RuntimeException(
            "PGRST202: child_id=6f1f7e2a-0000-4c1a-9d55-1b2c3d4e5f60 " +
                "user_id=aa11bb22-cc33-dd44-ee55-ff6677889900 code=A1B2C3"
        )
        val key = AccessRequestViewModel.safeError(leaky)
        assertEquals(AccessRequestViewModel.ERR_GENERIC, key)

        listOf(true, false).forEach { isThai ->
            val shown = AccessRequestStrings.error(key, isThai)
            assertNotNull(shown)
            listOf("6f1f7e2a", "aa11bb22", "A1B2C3", "child_id", "user_id", "PGRST").forEach { secret ->
                assertFalse("leaked '$secret'", shown!!.contains(secret, ignoreCase = true))
            }
        }
    }

    @Test
    fun `no error key means no error text`() {
        assertEquals(null, AccessRequestStrings.error(null, true))
    }

    @Test
    fun `qr payloads and codes both reduce to the bare invite code`() {
        assertEquals("A1B2C3", ChildRepository.extractInviteCode("nongrak://access?code=a1b2c3"))
        assertEquals("A1B2C3", ChildRepository.extractInviteCode("  a1b2c3 "))
        assertEquals("", ChildRepository.extractInviteCode("   "))
    }
}
