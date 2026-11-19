package me.kavishdevar.librepods.data.updates

import me.kavishdevar.librepods.R

/** First-run help and the current release's visual changelog use the same page layout. */
val gettingStartedGuide = listOf(
    UpdateItem(
        titleRes = R.string.guide_page_battery_title,
        descriptionRes = R.string.guide_page_battery_description,
        screenshotRes = R.drawable.guide_battery,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_controls_title,
        descriptionRes = R.string.guide_page_controls_description,
        screenshotRes = R.drawable.guide_controls,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_android_ios_title,
        descriptionRes = R.string.guide_page_android_ios_description,
        screenshotRes = R.drawable.guide_design,
    ),
)

val updates = listOf(
    UpdateItem(
        titleRes = R.string.guide_page_update_reconnect_v0657_title,
        descriptionRes = R.string.guide_page_update_reconnect_v0657_description,
        screenshotRes = R.drawable.guide_reconnect_0657,
        detailScreenshotRes = R.drawable.guide_reconnect_0657_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.51f,
        highlightHeightFraction = 0.30f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_finder_v0657_title,
        descriptionRes = R.string.guide_page_update_finder_v0657_description,
        screenshotRes = R.drawable.guide_finder_0657,
        detailScreenshotRes = R.drawable.guide_finder_0657_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.45f,
        highlightHeightFraction = 0.42f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_conversation_v0657_title,
        descriptionRes = R.string.guide_page_update_conversation_v0657_description,
        screenshotRes = R.drawable.guide_conversation_fix_0657,
        detailScreenshotRes = R.drawable.guide_conversation_fix_0657_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.10f,
        highlightHeightFraction = 0.46f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_gestures_v0657_title,
        descriptionRes = R.string.guide_page_update_gestures_v0657_description,
        screenshotRes = R.drawable.guide_gestures_fix_0657,
        detailScreenshotRes = R.drawable.guide_gestures_fix_0657_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.30f,
        highlightHeightFraction = 0.47f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_history_v0657_title,
        descriptionRes = R.string.guide_page_update_history_v0657_description,
        screenshotRes = R.drawable.guide_history_0657,
        detailScreenshotRes = R.drawable.guide_history_0657_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.17f,
        highlightHeightFraction = 0.49f,
    ),
)
