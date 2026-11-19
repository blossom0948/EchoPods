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
        titleRes = R.string.guide_page_update_volume_v0658_title,
        descriptionRes = R.string.guide_page_update_volume_v0658_description,
        screenshotRes = R.drawable.guide_volume_0658,
        detailScreenshotRes = R.drawable.guide_volume_0658_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.57f,
        highlightHeightFraction = 0.16f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_finder_v0658_title,
        descriptionRes = R.string.guide_page_update_finder_v0658_description,
        screenshotRes = R.drawable.guide_finder_0658,
        detailScreenshotRes = R.drawable.guide_finder_0658_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.08f,
        highlightHeightFraction = 0.50f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_widget_v0658_title,
        descriptionRes = R.string.guide_page_update_widget_v0658_description,
        screenshotRes = R.drawable.guide_widget_0658,
        detailScreenshotRes = R.drawable.guide_widget_0658_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.39f,
        highlightHeightFraction = 0.27f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_translation_v0658_title,
        descriptionRes = R.string.guide_page_update_translation_v0658_description,
        screenshotRes = R.drawable.guide_translation_0658,
        detailScreenshotRes = R.drawable.guide_translation_0658_detail,
        labelRes = R.string.guide_bug_fix_label,
        highlightTopFraction = 0.09f,
        highlightHeightFraction = 0.46f,
    ),
    UpdateItem(
        titleRes = R.string.guide_page_update_prompt_v0658_title,
        descriptionRes = R.string.guide_page_update_prompt_v0658_description,
        screenshotRes = R.drawable.guide_update_prompt_0658,
        detailScreenshotRes = R.drawable.guide_update_prompt_0658_detail,
        labelRes = R.string.guide_new_feature_label,
        highlightTopFraction = 0.35f,
        highlightHeightFraction = 0.34f,
    ),
)
