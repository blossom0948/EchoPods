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
        titleRes = R.string.guide_page_island_size_update_title,
        descriptionRes = R.string.guide_page_island_size_update_description,
        screenshotRes = R.drawable.guide_island_size,
        detailScreenshotRes = R.drawable.guide_island_size_detail,
        highlightTopFraction = 0.015f,
        highlightHeightFraction = 0.15f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_slider_fix_update_title,
        descriptionRes = R.string.guide_page_slider_fix_update_description,
        screenshotRes = R.drawable.guide_slider_fix,
        detailScreenshotRes = R.drawable.guide_slider_fix_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.7f,
        highlightHeightFraction = 0.28f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_labs_fix_update_title,
        descriptionRes = R.string.guide_page_labs_fix_update_description,
        screenshotRes = R.drawable.guide_labs_fix,
        detailScreenshotRes = R.drawable.guide_labs_fix_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.08f,
        highlightHeightFraction = 0.26f,
    ),
)
