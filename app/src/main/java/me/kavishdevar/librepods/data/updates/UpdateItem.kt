package me.kavishdevar.librepods.data.updates

data class UpdateItem(
    val titleRes: Int,
    val descriptionRes: Int,
    val screenshotRes: Int,
    /** Optional close-up of the feature shown inside a full-screen phone capture. */
    val detailScreenshotRes: Int? = null,
    /** Lets release notes distinguish features from fixes. */
    val labelRes: Int = me.kavishdevar.librepods.R.string.guide_new_feature_label,
    /** Normalized vertical target for the animated phone-screen highlight. */
    val highlightTopFraction: Float = 0.58f,
    val highlightHeightFraction: Float = 0.38f,
)
