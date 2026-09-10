package com.example.omni.data.repo

import com.example.omni.data.model.Comment
import com.example.omni.data.model.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The offline twin of [FirestoreFeedRepository] — what `@DevicePreviews` and the Phase 13 tests run
 * against. Two posts shaped like the design's own, with the like/comment state kept in memory so
 * the optimistic flips the ViewModel makes are visible in a preview.
 */
class PreviewFeedRepository : FeedRepository {

    private val posts = MutableStateFlow(
        listOf(
            Post(
                id = "preview-1",
                authorId = "dr-ben",
                authorName = "Dr.Ben",
                authorVerified = true,
                body = "Think of your meals as cellular fuel. Prioritizing whole foods and healthy " +
                    "fats supports your heart, brain, and immune system. Eat well today to protect " +
                    "your health tomorrow. \n#NutritionFacts #LifestyleMedicine",
                createdAt = System.currentTimeMillis() - 3 * 60_000L,
                likeCount = 1_000,
                commentCount = 100,
            ),
            Post(
                id = "preview-2",
                authorId = "dr-kelly",
                authorName = "Dr.Kelly",
                authorVerified = true,
                body = "A balanced plate is the best daily prescription. Fill half your plate with " +
                    "colorful veggies to reduce inflammation, and add lean protein for sustained " +
                    "energy. Simple, effective, and doctor-approved. 🥗🩺 #FoodIsMedicine #HealthyLiving",
                createdAt = System.currentTimeMillis() - 45 * 60_000L,
                likeCount = 900,
                commentCount = 80,
            ),
        ),
    )

    private val likes = MutableStateFlow<Set<String>>(emptySet())

    private val comments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())

    override fun observeFirstPage(uid: String, pageSize: Int): Flow<List<Post>> =
        posts.map { page -> page.take(pageSize).map { it.copy(likedByMe = it.id in likes.value) } }

    override fun observeByAuthor(authorId: String, limit: Int): Flow<List<Post>> =
        posts.map { all -> all.filter { it.authorId == authorId }.take(limit) }

    override suspend fun loadPage(uid: String, beforeCreatedAt: Long, pageSize: Int): List<Post> =
        emptyList()

    override suspend fun toggleLike(uid: String, postId: String) {
        if (postId in likes.value) likes.value -= postId else likes.value += postId
    }

    override fun observeComments(postId: String): Flow<List<Comment>> =
        comments.map { it[postId].orEmpty() }

    override suspend fun addComment(uid: String, postId: String, authorName: String, body: String) {
        val comment = Comment(id = "c-${nextId++}", authorId = uid, authorName = authorName, body = body.trim())
        comments.value = comments.value + (postId to (comments.value[postId].orEmpty() + comment))
    }

    override suspend fun createPost(uid: String, author: PostAuthor, body: String, imageUrl: String?) {
        val post = Post(
            id = "preview-${nextId++}",
            authorId = uid,
            authorName = author.name,
            authorPhotoUrl = author.photoUrl,
            authorVerified = author.verified,
            authorProfession = author.profession,
            body = body.trim(),
            imageUrl = imageUrl,
            createdAt = System.currentTimeMillis(),
        )
        posts.value = listOf(post) + posts.value
    }

    private var nextId = 3
}
