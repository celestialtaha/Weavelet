/*
 * MainTileService.kt — Wear OS Tile for quick access to Wear Music
 *
 * Requires:
 *   implementation "androidx.wear.tiles:tiles:1.5.0"
 *   implementation "androidx.wear.protolayout:protolayout-material:1.3.0"
 *   // (optional) previews & tests
 *   debugImplementation "androidx.wear.tiles:tiles-renderer:1.5.0"
 *   testImplementation  "androidx.wear.tiles:tiles-testing:1.5.0"
 */

package com.wapp.wearmusic.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.PrimaryLayoutMargins.Companion.DEFAULT_PRIMARY_LAYOUT_MARGIN
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.MainActivity

private const val RESOURCES_VERSION = "1"
private const val PREFS_NAME = "music_player_settings"
private const val KEY_TILE_TRACK_TITLE = "tile_track_title"
private const val KEY_TILE_TRACK_ARTIST = "tile_track_artist"
private const val KEY_TILE_IS_PLAYING = "tile_is_playing"
private const val KEY_TILE_LIBRARY_KNOWN = "tile_library_known"
private const val KEY_TILE_LIBRARY_HAS_TRACKS = "tile_library_has_tracks"

@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {

    /* ------------------------------------------------------------------ */
    /* Requests ↔ responses                                               */
    /* ------------------------------------------------------------------ */

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ) = resources(requestParams)

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ) = tile(requestParams, this)

    /* Optional: batched enter/leave events (Tiles 1.5) */
    // temporarily commented since I got errors
//    override fun onRecentInteractionEvents(
//        interactionEvents: List<EventBuilders.TileInteractionEvent>
//    ): ListenableFuture<Void> = Futures.immediateVoidFuture()
}

/* ---------------------------------------------------------------------- */
/* Resources: still empty, but keep the version for future asset use      */
/* ---------------------------------------------------------------------- */
private fun resources(
    @Suppress("UNUSED_PARAMETER")
    requestParams: RequestBuilders.ResourcesRequest
) = ResourceBuilders.Resources.Builder()
    .setVersion(RESOURCES_VERSION)
    .build()

/* ---------------------------------------------------------------------- */
/* Main tile                                                              */
/* ---------------------------------------------------------------------- */
private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context
): TileBuilders.Tile {
    val timeline = TimelineBuilders.Timeline.Builder()
        .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(tileLayout(requestParams, context))
                        .build()
                )
                .build()
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timeline)
        .build()
}

/* ---------------------------------------------------------------------- */
/* Actual layout                                                          */
/* ---------------------------------------------------------------------- */
private fun tileLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context
): LayoutElementBuilders.LayoutElement {
    val deviceParameters = requestParams.deviceConfiguration
    val nowPlaying = readNowPlaying(context)
    val hasTrack = nowPlaying.title != null
    val openTileClickable = if (hasTrack) {
        mainActivityClickable(
            context = context,
            id = "open_now_playing",
            destination = MainActivity.DESTINATION_PLAYER
        )
    } else {
        mainActivityClickable(
            context = context,
            id = "open_library",
            destination = MainActivity.DESTINATION_LIBRARY
        )
    }
    val edgeClickable = if (hasTrack) {
        mainActivityClickable(
            context = context,
            id = if (nowPlaying.isPlaying) "pause_from_tile" else "resume_from_tile",
            destination = MainActivity.DESTINATION_PLAYER,
            tileAction = MainActivity.TILE_ACTION_TOGGLE_PLAYBACK
        )
    } else {
        openTileClickable
    }
    val status = when {
        hasTrack && nowPlaying.isPlaying -> context.getString(R.string.tile_now_playing)
        hasTrack -> context.getString(R.string.tile_paused)
        nowPlaying.libraryKnown && !nowPlaying.libraryHasTracks -> context.getString(R.string.tile_library_empty)
        nowPlaying.libraryHasTracks -> context.getString(R.string.tile_ready)
        else -> context.getString(R.string.tile_ready)
    }
    val title = when {
        hasTrack -> nowPlaying.title
        nowPlaying.libraryKnown && !nowPlaying.libraryHasTracks -> context.getString(R.string.tile_add_music)
        else -> context.getString(R.string.tile_choose_track)
    }
    val artist = when {
        hasTrack -> nowPlaying.artist ?: context.getString(R.string.app_name)
        nowPlaying.libraryKnown && !nowPlaying.libraryHasTracks -> context.getString(R.string.open_library)
        else -> context.getString(R.string.app_name)
    }
    val edgeLabel = when {
        nowPlaying.isPlaying -> context.getString(R.string.pause)
        hasTrack -> context.getString(R.string.play)
        else -> context.getString(R.string.open_library)
    }
    val edgeDescription = when {
        nowPlaying.isPlaying -> context.getString(R.string.pause)
        hasTrack -> context.getString(R.string.play)
        else -> context.getString(R.string.open_library_desc)
    }

    return materialScope(context, deviceParameters) {
        primaryLayout(
            titleSlot = {
                text(
                    text = context.getString(R.string.app_name).layoutString,
                    typography = Typography.TITLE_SMALL,
                    maxLines = 1
                )
            },
            mainSlot = {
                LayoutElementBuilders.Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Column.Builder()
                            .setWidth(expand())
                            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                            .addContent(
                                text(
                                    text = status.layoutString,
                                    typography = Typography.LABEL_SMALL,
                                    color = colorScheme.primary,
                                    maxLines = 1,
                                    alignment = TEXT_ALIGN_CENTER
                                )
                            )
                            .addContent(
                                LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build()
                            )
                            .addContent(
                                text(
                                    text = title.layoutString,
                                    typography = Typography.TITLE_MEDIUM,
                                    color = colorScheme.onBackground,
                                    maxLines = 2,
                                    alignment = TEXT_ALIGN_CENTER,
                                    overflow = TEXT_OVERFLOW_ELLIPSIZE,
                                    incrementsForTypographySize = listOf(-2f, -4f)
                                )
                            )
                            .addContent(
                                LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build()
                            )
                            .addContent(
                                text(
                                    text = artist.layoutString,
                                    typography = Typography.BODY_SMALL,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    alignment = TEXT_ALIGN_CENTER,
                                    overflow = TEXT_OVERFLOW_ELLIPSIZE
                                )
                            )
                            .build()
                        )
                    .build()
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = edgeClickable,
                    modifier = LayoutModifier.contentDescription(
                        edgeDescription
                    )
                ) {
                    text(edgeLabel.layoutString)
                }
            },
            onClick = openTileClickable,
            margins = DEFAULT_PRIMARY_LAYOUT_MARGIN
        )
    }
}

private data class TileNowPlaying(
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val libraryKnown: Boolean,
    val libraryHasTracks: Boolean
)

private fun readNowPlaying(context: Context): TileNowPlaying {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val rawTitle = prefs.getString(KEY_TILE_TRACK_TITLE, null)?.takeIf { it.isNotBlank() }
    return TileNowPlaying(
        title = rawTitle,
        artist = prefs.getString(KEY_TILE_TRACK_ARTIST, null)?.takeIf { it.isNotBlank() },
        isPlaying = prefs.getBoolean(KEY_TILE_IS_PLAYING, false),
        libraryKnown = prefs.getBoolean(KEY_TILE_LIBRARY_KNOWN, false),
        libraryHasTracks = prefs.getBoolean(KEY_TILE_LIBRARY_HAS_TRACKS, false)
    )
}

private fun mainActivityClickable(
    context: Context,
    id: String,
    destination: String,
    tileAction: String? = null
): ModifiersBuilders.Clickable {
    val activityBuilder = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(MainActivity::class.java.name)
        .addKeyToExtraMapping(
            MainActivity.EXTRA_OPEN_DESTINATION,
            ActionBuilders.AndroidStringExtra.Builder()
                .setValue(destination)
                .build()
        )

    if (tileAction != null) {
        activityBuilder.addKeyToExtraMapping(
            MainActivity.EXTRA_TILE_ACTION,
            ActionBuilders.AndroidStringExtra.Builder()
                .setValue(tileAction)
                .build()
        )
    }

    return ModifiersBuilders.Clickable.Builder()
        .setId(id)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(activityBuilder.build())
                .build()
        )
        .build()
}
