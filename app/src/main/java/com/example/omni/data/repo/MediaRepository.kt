package com.example.omni.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * User-picked images on their way to the cloud.
 *
 * Three entry points, one pipeline: a post's photo, a story's photo and a profile photo differ only
 * in the folder they land in.
 *
 * **The folder is the only thing that differs, and it decides nothing.** Which entity a photo
 * becomes — post, story or avatar — is the *caller's* decision, made before it gets here; this
 * uploader returns a URL and has no opinion about what is written with it. That separation is why
 * [uploadStoryImage] exists rather than stories borrowing [uploadPostImage]: they were borrowing it,
 * and while it happened to work, it left the two flows sharing the one place a reader would look to
 * tell them apart.
 */
interface MediaRepository {

    /**
     * Compresses [image] and uploads it, returning the public delivery URL to store on the post.
     *
     * [uid] tags the upload's Cloudinary `public_id` prefix so any given account's uploads are
     * findable in the dashboard — it is a label, not an authorisation: Cloudinary's unsigned preset
     * is the thing that decides what may be uploaded, and it accepts images only.
     *
     * Throws on a failed read, a failed upload, or a picked file that is not a decodable image. The
     * caller is expected to catch and report: an upload can fail for a dozen ordinary reasons and
     * losing the user's photo silently is worse than saying so.
     */
    suspend fun uploadPostImage(uid: String, image: Uri): String

    /**
     * Compresses [image] and uploads it as a story's photo.
     *
     * Its own folder, for the reason [uploadAvatar] has one: a story is gone in 24 hours and a post
     * is not, so mixing them in the media library makes the ephemeral half impossible to find or
     * sweep. Same compression, same unsigned preset, same failure contract.
     */
    suspend fun uploadStoryImage(uid: String, image: Uri): String

    /**
     * Compresses [image] and uploads it as [uid]'s profile photo, returning the delivery URL for
     * `users/{uid}.photoUrl`.
     *
     * Separate from [uploadPostImage] only in where it lands: avatars go to their own folder so the
     * dashboard's media library does not mix a user's face into their posts. Same compression, same
     * unsigned preset, same failure contract.
     */
    suspend fun uploadAvatar(uid: String, image: Uri): String
}

/**
 * [MediaRepository] over Cloudinary's unsigned upload API.
 *
 * ## Why Cloudinary and not Firebase Storage
 *
 * Firebase Storage now requires the Blaze plan on new projects, and the project's only upload need
 * is social images — not worth a billing account. The agreed architecture keeps Firebase Auth and
 * Firestore exactly where they are, and moves only the media bytes:
 *
 * ```
 * Android → Cloudinary → optimized image URL → Firestore (`posts.imageUrl` stays a plain string)
 * ```
 *
 * **No API secret ships in the APK.** The upload uses an *unsigned preset* (`omni_mobile`) — the
 * one Cloudinary flow designed for clients that cannot keep secrets. The preset itself is locked
 * down in the dashboard (images only) and is the single thing to revoke if it is ever abused.
 *
 * ## The compression pipeline is kept from the Storage version, deliberately
 *
 * Cloudinary can transform images on delivery, but the bytes still have to cross the user's data
 * plan to reach it. A modern phone camera writes 8–12 MB per photo; re-encoding to a 1080px long
 * edge at JPEG 85 lands a typical photo at 150–400 kB, which uploads on a poor connection. The
 * decode stays two-pass (`inJustDecodeBounds` first, then a power-of-two subsample) because
 * decoding a 12-megapixel photo at full size is 48 MB of heap for something about to be thrown
 * away — which is how an image picker OOMs a phone. EXIF orientation is applied by hand because
 * [BitmapFactory] ignores the tag, and skipping it is what makes half of everyone's portrait
 * photos appear sideways in a feed.
 *
 * ## Delivery optimisation
 *
 * The returned URL is `.../image/upload/...`; the feed and stories draw it through Coil. The
 * delivery-side transformations (`f_auto,q_auto` and friends) are configured on the preset in the
 * Cloudinary dashboard, so they apply without the app building special URLs.
 */
class CloudinaryMediaRepository(
    private val context: Context,
    private val cloudName: String,
    private val unsignedPreset: String,
    private val client: OkHttpClient = defaultClient(),
) : MediaRepository {

    override suspend fun uploadPostImage(uid: String, image: Uri): String =
        upload(uid, image, PostsFolder)

    override suspend fun uploadStoryImage(uid: String, image: Uri): String =
        upload(uid, image, StoriesFolder)

    override suspend fun uploadAvatar(uid: String, image: Uri): String =
        upload(uid, image, AvatarsFolder)

    /** The one upload both entry points are: compress, POST, return the delivery URL. */
    private suspend fun upload(uid: String, image: Uri, folder: String): String =
        withContext(Dispatchers.IO) {
            val bytes = compress(image)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.jpg", bytes.toRequestBody(Jpeg))
                .addFormDataPart("upload_preset", unsignedPreset)
                // The uid prefix keeps one account's uploads together in the dashboard's media
                // library. `folder` groups them; the returned `secure_url` already points at it.
                .addFormDataPart("folder", "$folder/$uid")
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Cloudinary's error body is JSON with a `message`; keep it in the thrown line
                    // so the composer's failure state can show something better than "failed".
                    val message = runCatching { JSONObject(text).getString("message") }
                        .getOrDefault(text.take(120))
                    error("the upload was rejected: $message")
                }
                val url = JSONObject(text).optString("secure_url")
                    .takeIf { it.isNotBlank() }
                    ?: error("the upload returned no URL")
                url
            }
        }

    /** Reads [image] twice — once for its size, once for the pixels — and returns the JPEG bytes. */
    private fun compress(image: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(image) { BitmapFactory.decodeStream(it, null, bounds) }

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        require(longest > 0) { "that file could not be read as an image" }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longest) }
        val decoded = read(image) { BitmapFactory.decodeStream(it, null, options) }
            ?: error("that image could not be decoded")

        val upright = decoded.upright(image)
        val scaled = upright.scaledToFit(MaxEdge)

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)
            scaled.recycle()
            out.toByteArray()
        }
    }

    /**
     * Opens [image] and hands the stream to [block], closing it either way.
     *
     * A `null` stream means the content provider refused or the Uri is stale — the read grant a
     * photo picker hands back is scoped to this process, so a Uri that outlives it lands here.
     *
     * The stream is checked on its own line, deliberately. Written as
     * `openInputStream(image)?.use(block) ?: error(…)` the elvis cannot tell a null *stream* from a
     * block that legitimately *returned* null — and [compress]'s first call is the bounds pass,
     * where [BitmapFactory.decodeStream] returns null by design. That folded every upload onto the
     * stale-Uri message before a byte reached the network, and the message being a plausible lie is
     * what sent the fix hunting the picker's read grant instead of this line.
     */
    private fun <T> read(image: Uri, block: (java.io.InputStream) -> T): T {
        val stream = context.contentResolver.openInputStream(image)
            ?: error("that image is no longer available — pick it again")
        return stream.use(block)
    }

    /** Applies the EXIF orientation tag, recycling the original when it produced a new bitmap. */
    private fun Bitmap.upright(image: Uri): Bitmap {
        val orientation = runCatching {
            read(image) {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            // Everything else, including the two transposes nobody's camera writes, is left alone.
            else -> return this
        }

        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated !== this) recycle()
        return rotated
    }

    /** The exact scale after the subsample got it close, so the long edge is [maxEdge] and no more. */
    private fun Bitmap.scaledToFit(maxEdge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return this
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== this) recycle()
        return scaled
    }

    /**
     * The largest power-of-two subsample that still leaves the long edge at or above [MaxEdge].
     *
     * `inSampleSize` only honours powers of two, so this deliberately stops one step early and lets
     * [scaledToFit] do the remainder — subsampling past the target and scaling back up would be
     * cheaper and visibly soft.
     */
    private fun sampleSizeFor(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= MaxEdge) sample *= 2
        return sample
    }

    companion object {
        private const val PostsFolder = "posts"

        /** Stories land apart from posts: they expire, and posts do not — see [uploadStoryImage]. */
        private const val StoriesFolder = "stories"

        /** Avatars land beside the posts, not among them — see [uploadAvatar]. */
        private const val AvatarsFolder = "avatars"

        /** BACKEND_PLAN §11's own figure: the long edge a post image is stored at. */
        private const val MaxEdge = 1080

        /** High enough that the feed's crop shows no artefacts, low enough to halve the bytes. */
        private const val JpegQuality = 85

        private val Jpeg = "image/jpeg".toMediaType()

        /**
         * Uploads want longer patience than routing calls: a 400 kB body on a poor Bangladeshi
         * mobile connection is not the same ask as a JSON reply. Two minutes, and no retries —
         * the composer already shows a failure state with a retry of the whole post.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
