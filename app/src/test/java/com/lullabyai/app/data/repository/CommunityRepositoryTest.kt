package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.CommunityComment
import com.lullabyai.app.data.model.CommunityCommentDto
import com.lullabyai.app.data.model.CommunityPost
import com.lullabyai.app.data.model.CommunityPostDto
import com.lullabyai.app.data.model.formatRelativeTime
import com.lullabyai.app.data.model.parseIsoToEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityRepositoryTest {

    private val testScope = CoroutineScope(Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testCommunityPostSerialization() {
        val comment = CommunityComment(
            id = "c1",
            postId = "p1",
            userId = "u1",
            authorName = "แม่น้องนีน่า",
            content = "ยินดีด้วยนะคะ",
            likesCount = 2,
            isLiked = true
        )
        val post = CommunityPost(
            id = "p1",
            userId = "u2",
            authorName = "แม่น้องกิกะ",
            content = "ลูกเริ่มเดินก้าวแรกแล้วค่ะ!",
            tags = listOf("#พัฒนาการวัยเตาะแตะ"),
            likesCount = 5,
            isLiked = false,
            commentsCount = 1,
            comments = listOf(comment)
        )

        val encoded = json.encodeToString(post)
        val decoded = json.decodeFromString<CommunityPost>(encoded)

        assertEquals(post.id, decoded.id)
        assertEquals(post.userId, decoded.userId)
        assertEquals(post.authorName, decoded.authorName)
        assertEquals(post.content, decoded.content)
        assertEquals(post.tags, decoded.tags)
        assertEquals(1, decoded.comments.size)
        assertEquals("แม่น้องนีน่า", decoded.comments[0].authorName)
        assertEquals(2, decoded.comments[0].likesCount)
    }

    @Test
    fun testCommunityPostDtoSerialization() {
        val dto = CommunityPostDto(
            id = "p123",
            userId = "u456",
            authorName = "พ่อกิฟท์",
            content = "ปรึกษาเรื่องฟันน้ำนมครับ",
            tags = listOf("#สุขภาพ"),
            imageUrl = "https://example.com/photo.jpg",
            likesCount = 10,
            commentsCount = 3,
            createdAt = "2026-09-08T08:00:00Z"
        )

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<CommunityPostDto>(encoded)

        assertEquals("p123", decoded.id)
        assertEquals("u456", decoded.userId)
        assertEquals("พ่อกิฟท์", decoded.authorName)
        assertEquals("https://example.com/photo.jpg", decoded.imageUrl)
        assertEquals(10, decoded.likesCount)
    }

    @Test
    fun testRelativeTimeFormatting() {
        val now = System.currentTimeMillis()
        assertEquals("เมื่อสักครู่", formatRelativeTime(now - 10_000, isThai = true))
        assertEquals("Just now", formatRelativeTime(now - 10_000, isThai = false))

        assertEquals("5 นาทีที่แล้ว", formatRelativeTime(now - 5 * 60 * 1000, isThai = true))
        assertEquals("5 min ago", formatRelativeTime(now - 5 * 60 * 1000, isThai = false))

        assertEquals("2 ชั่วโมงที่แล้ว", formatRelativeTime(now - 2 * 60 * 60 * 1000, isThai = true))
        assertEquals("2 hr ago", formatRelativeTime(now - 2 * 60 * 60 * 1000, isThai = false))

        assertEquals("3 วันที่แล้ว", formatRelativeTime(now - 3 * 24 * 60 * 60 * 1000, isThai = true))
        assertEquals("3 days ago", formatRelativeTime(now - 3 * 24 * 60 * 60 * 1000, isThai = false))
    }

    @Test
    fun testParseIsoToEpochMillis() {
        val iso = "2026-09-08T08:00:00Z"
        val millis = parseIsoToEpochMillis(iso)
        assertTrue(millis > 0)

        val fallback = parseIsoToEpochMillis(null)
        assertTrue(fallback > 0)
    }

    @Test
    fun testAiSafetyModeration() {
        val repo = CommunityRepository(testScope)

        // 1. Honey warning for infants
        val honeyResult = repo.checkAiSafety("ลองให้น้ำผึ้งกับทารกดูสิคะ จะช่วยให้ชุ่มคอ")
        assertFalse(honeyResult.isSafe)
        assertNotNull(honeyResult.warningMessage)
        assertTrue(honeyResult.warningMessage!!.contains("น้ำผึ้ง"))

        // 2. Aspirin warning
        val aspirinResult = repo.checkAiSafety("ถ้าลูกมีไข้ให้ทานยาแอสไพรินได้ไหมคะ")
        assertFalse(aspirinResult.isSafe)
        assertNotNull(aspirinResult.warningMessage)
        assertTrue(aspirinResult.warningMessage!!.contains("แอสไพริน"))

        // 3. Harsh chemical warning
        val chemicalResult = repo.checkAiSafety("ใช้แอลกอฮอล์เช็ดตัวลดไข้ดีไหม")
        assertFalse(chemicalResult.isSafe)

        // 4. Safe parenting question
        val safeResult = repo.checkAiSafety("ลูกอายุ 6 เดือน เริ่มให้อาหารเสริมมื้อแรกเป็นข้าวบดตับไก่ได้ไหมคะ")
        assertTrue(safeResult.isSafe)
    }

    @Test
    fun testAddPostAndCommentLocally() = runBlocking {
        val repo = CommunityRepository(this)

        val post = CommunityPost(
            id = "test-p1",
            authorName = "คุณแม่น้องบีม",
            content = "แชร์ไอเดียเมนูอาหารเสริม",
            tags = listOf("#อาหารเด็ก6เดือน")
        )

        repo.addPost(post)
        val currentPosts = repo.posts.first()
        assertEquals(1, currentPosts.size)
        assertEquals("test-p1", currentPosts[0].id)

        // Add comment
        repo.addComment(
            postId = "test-p1",
            authorName = "คุณแม่น้องปาล์ม",
            authorRole = "คุณแม่",
            content = "ขอบคุณสำหรับไอเดียค่ะ"
        )

        val updatedPosts = repo.posts.first()
        assertEquals(1, updatedPosts[0].commentsCount)
        assertEquals(1, updatedPosts[0].comments.size)
        assertEquals("คุณแม่น้องปาล์ม", updatedPosts[0].comments[0].authorName)
    }
}
