package com.example.omni.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.data.model.Post
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.theme.OmniAuthError
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedPostBody
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniFeedVerified
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNutriChipCarbs
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.data.model.relativeTimeOf

/**
 * One user's public page — the Facebook shape: their posts under a profile header, a follow
 * button, and a message button that opens the existing chat.
 *
 * Invented UI under `UI_ARCHITECTURE.md` §6 rule 11, assembled from parts the design owns: the
 * header row is the settings screen's profile row (55 avatar, name and email), the follow/message
 * buttons are the auth pages' ink commitment and the healthcare card's apply pill, and the posts
 * are the feed's own card bodies — a profile is what it shows.
 *
 * No bottom bar, like every inner page: this is reached from a post or a comment, and the back
 * button (plus the system back) returns to wherever that was.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit = {},
    onFollowToggle: () -> Unit = {},
    /** Opens the chat with this user — `MainActivity` hands the uid to the messages ViewModel. */
    onMessage: () -> Unit = {},
    /** Opens this post's comments — the same sheet the feed hosts. */
    onOpenPost: (Post) -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding),
        ) {
            Spacer(Modifier.height(HeaderTopGap))

            // The back row — the guides screen's own construction.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BackRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(BackButtonSize)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_set_arrow_right),
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f),
                    )
                }

                Text(
                    text = state.user?.name.orEmpty().ifBlank { "Profile" },
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Spacer(Modifier.height(ProfileTop))

            // The identity row: the settings screen's own shape — 55 avatar, name, and the count
            // line where the email sits on settings (a public page shows activity, not contact).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(55.dp)
                        .clip(CircleShape)
                        .background(OmniNutriChipCarbs.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.user?.photoUrl != null) {
                        AsyncImage(
                            model = state.user.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = state.user?.name?.take(1)?.uppercase().orEmpty().ifBlank { "?" },
                            style = HomeType.SectionTitle,
                            color = OmniCardInk,
                            maxLines = 1,
                        )
                    }
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            // Three different facts, three different words. "Someone" for all of
                            // them read as a real account called Someone — and, before the state
                            // carried its own uid, it was often the *previous* profile's name that
                            // stood here instead.
                            text = state.user?.name.orEmpty().ifBlank {
                                when {
                                    state.isLoading -> "Loading…"
                                    state.notFound -> "Account unavailable"
                                    else -> "Someone"
                                }
                            },
                            style = SettingsType.ProfileName,
                            color = OmniCardInk,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (state.user?.verified == true) {
                            Image(
                                painter = painterResource(R.drawable.ic_feed_badge_check),
                                contentDescription = "Verified",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        // Nothing rather than zeroes while the counts are in flight: "0 posts · 0
                        // followers" is a statement, and it is usually the wrong one.
                        text = if (state.isLoading) {
                            "…"
                        } else {
                            "${state.postsCount} posts · ${state.followers} followers"
                        },
                        style = SettingsType.ProfileEmail,
                        color = OmniFeedHint,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // The actions. Mine shows neither — my own page's settings live behind the header
            // avatar, and following or messaging myself is not a thing the product does. Held back
            // while the page loads too: `isMe` is known from the uids before the document arrives,
            // but a deleted account has nobody to follow.
            if (!state.isMe && !state.notFound) {
                Spacer(Modifier.height(ActionsTop))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Follow: the healthcare card's apply pill, filled when following — the same
                    // on/off the liked pill and the nav bar's selection teach.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(if (state.iFollowThem) OmniNutriChipCarbs else OmniInk)
                            .clickable(onClick = onFollowToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.iFollowThem) "Following" else "Follow",
                            style = HomeType.HeroButton,
                            color = OmniOnInk,
                            maxLines = 1,
                        )
                    }

                    // Message: the outline twin.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(OmniNutriChipCarbs.copy(alpha = 0.3f))
                            .clickable(onClick = onMessage),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Message",
                            style = HomeType.HeroButton,
                            color = OmniCardInk,
                            maxLines = 1,
                        )
                    }
                }

                // Why the last tap did not take. Only drawn when there is something to say, so the
                // ordinary page keeps Figma's spacing exactly; a refused follow is worth the 20dp it
                // borrows from the list below it, because the alternative is a button that appears
                // to do nothing at all.
                if (state.followError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.followError,
                        style = FeedType.Meta12,
                        color = OmniAuthError,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(PostsTop))

            if (state.posts.isEmpty()) {
                // The honest empty state, aligned to the page's own gutter — and honest about
                // *which* empty it is. One sentence for all three used to make a page still
                // loading look exactly like a person who has never posted.
                Text(
                    text = when {
                        state.isLoading -> "Loading posts…"
                        state.notFound -> "This account is no longer available."
                        else -> "No posts yet."
                    },
                    style = FeedType.Hint16,
                    color = OmniFeedHint,
                    modifier = Modifier.padding(start = 2.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(state.posts, key = { _, post -> post.id }) { _, post ->
                        ProfilePostCard(post = post, onOpen = { onOpenPost(post) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * One of the profile's posts — the feed's card without its actions: this page is a reading
 * surface, and every action on a post belongs to the feed where the post lives.
 */
@Composable
private fun ProfilePostCard(post: Post, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniFeedTimestamp.copy(alpha = 0.06f))
            .clickable(onClick = onOpen)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = relativeTimeOf(post.createdAt),
                style = FeedType.Meta12,
                color = OmniFeedTimestamp,
                maxLines = 1,
            )
        }
        Text(
            text = post.body,
            style = FeedType.PostBody,
            color = OmniFeedPostBody,
            maxLines = 4,
        )
        if (post.imageUrl != null) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
    }
}

// ---- Geometry ----------------------------------------------------------------------------------

private val ScreenPadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val ProfileTop = 20.dp
private val ActionsTop = 18.dp
private val PostsTop = 22.dp

@DevicePreviews
@Composable
private fun ProfileScreenPreview() {
    OmniTheme {
        ProfileScreen(
            state = ProfileUiState(
                uid = "ben",
                user = com.example.omni.data.model.User(
                    uid = "ben",
                    name = "Dr.Ben",
                    email = "ben@omni.health",
                    verified = true,
                ),
                posts = listOf(
                    Post(
                        id = "p1",
                        authorId = "ben",
                        authorName = "Dr.Ben",
                        body = "Think of your meals as cellular fuel.",
                        createdAt = System.currentTimeMillis() - 60_000,
                    ),
                ),
                postsCount = 1,
                iFollowThem = true,
                isLoading = false,
            ),
        )
    }
}

/**
 * The refused follow — the one state that cannot be reached in a preview by tapping, and the one
 * worth looking at, since it is what every tap does until the follow rules are deployed.
 */
@DevicePreviews
@Composable
private fun ProfileScreenFollowRefusedPreview() {
    OmniTheme {
        ProfileScreen(
            state = ProfileUiState(
                uid = "ben",
                user = com.example.omni.data.model.User(
                    uid = "ben",
                    name = "Dr.Ben",
                    email = "ben@omni.health",
                ),
                isLoading = false,
                followError = "That didn't go through — PERMISSION_DENIED: " +
                    "Missing or insufficient permissions.",
            ),
        )
    }
}
