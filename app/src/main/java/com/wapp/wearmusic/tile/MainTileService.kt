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
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.wapp.wearmusic.R
import com.wapp.wearmusic.presentation.MainActivity

private const val RESOURCES_VERSION = "1"

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

    /* -------------------------- title text --------------------------- */
    val title = Text.Builder(context, context.getString(R.string.app_name))
        .setTypography(Typography.TYPOGRAPHY_DISPLAY1)      // uses new system font on Wear OS 6
        .setMaxLines(1)
        .build()

    /* -------------------------- primary chip ------------------------ */
    val openAppClickable = ModifiersBuilders.Clickable.Builder()
        .setId("open_music_player")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .addKeyToExtraMapping(
                            MainActivity.EXTRA_OPEN_DESTINATION,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(MainActivity.DESTINATION_PLAYER)
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()

    val openAppChip = CompactChip.Builder(context, openAppClickable, deviceParameters)
        .setChipColors(ChipColors.primaryChipColors(Colors.DEFAULT))
        .setTextContent(context.getString(R.string.open_player))
        .setContentDescription(context.getString(R.string.open_player_desc))
        .build()

    /* -------------------------- primary layout ---------------------- */
    return PrimaryLayout.Builder(deviceParameters)
        .setResponsiveContentInsetEnabled(true)
        .setContent(title)
        .setPrimaryChipContent(openAppChip)
        .build()
}
