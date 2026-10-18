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
        titleRes = R.string.guide_page_wear_control_update_title,
        descriptionRes = R.string.guide_page_wear_control_update_description,
        screenshotRes = R.drawable.guide_wear_control,
    ),
)
