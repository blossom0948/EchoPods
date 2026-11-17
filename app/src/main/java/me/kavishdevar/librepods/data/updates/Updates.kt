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
        titleRes = R.string.guide_page_compact_island_v064_title,
        descriptionRes = R.string.guide_page_compact_island_v064_description,
        screenshotRes = R.drawable.guide_island_size,
        detailScreenshotRes = R.drawable.guide_island_size_detail,
        highlightTopFraction = 0.0f,
        highlightHeightFraction = 0.10f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_conversation_v064_title,
        descriptionRes = R.string.guide_page_conversation_v064_description,
        screenshotRes = R.drawable.guide_slider_fix,
        detailScreenshotRes = R.drawable.guide_slider_fix_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.50f,
        highlightHeightFraction = 0.36f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_gesture_v064_title,
        descriptionRes = R.string.guide_page_gesture_v064_description,
        screenshotRes = R.drawable.guide_head_gesture_fix,
        detailScreenshotRes = R.drawable.guide_head_gesture_fix_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.12f,
        highlightHeightFraction = 0.28f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_head_tracking_retry_v0651_title,
        descriptionRes = R.string.guide_page_head_tracking_retry_v0651_description,
        screenshotRes = R.drawable.guide_head_gesture_fix,
        detailScreenshotRes = R.drawable.guide_head_gesture_fix_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.12f,
        highlightHeightFraction = 0.28f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_navigation_v065_title,
        descriptionRes = R.string.guide_page_navigation_v065_description,
        screenshotRes = R.drawable.guide_labs_fix,
        detailScreenshotRes = R.drawable.guide_labs_fix_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.42f,
        highlightHeightFraction = 0.30f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_health_v064_title,
        descriptionRes = R.string.guide_page_health_v064_description,
        screenshotRes = R.drawable.guide_health_workout,
        detailScreenshotRes = R.drawable.guide_health_workout_detail,
        highlightTopFraction = 0.48f,
        highlightHeightFraction = 0.28f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_zoom_v065_title,
        descriptionRes = R.string.guide_page_update_zoom_v065_description,
        screenshotRes = R.drawable.guide_health_workout,
        detailScreenshotRes = R.drawable.guide_health_workout_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.48f,
        highlightHeightFraction = 0.28f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_hr_retry_v0652_title,
        descriptionRes = R.string.guide_page_hr_retry_v0652_description,
        screenshotRes = R.drawable.guide_health_workout,
        detailScreenshotRes = R.drawable.guide_health_workout_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.48f,
        highlightHeightFraction = 0.28f,
    ),
)
