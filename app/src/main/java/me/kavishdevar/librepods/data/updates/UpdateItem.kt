package me.kavishdevar.librepods.data.updates

data class UpdateItem(
    val titleRes: Int,
    val descriptionRes: Int,
    val screenshotRes: Int,
    /** Optional close-up of the feature shown inside a full-screen phone capture. */
    val detailScreenshotRes: Int? = null,
)
