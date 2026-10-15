package com.galaxyairpods.ui.components

import com.galaxyairpods.domain.model.AirPodsModel

/**
 * Selects a visual family independently from the model label. If Android only
 * reports the generic "AirPods Pro" name, use the existing Pro bitmap render
 * while keeping the generation explicitly unknown in the UI.
 */
internal enum class AirPodsArtworkTechnology {
    BITMAP_LAYERS,
    VECTOR_FALLBACK,
}

internal fun AirPodsModel.artworkTechnology(): AirPodsArtworkTechnology = when (this) {
    AirPodsModel.AIRPODS_PRO_UNKNOWN,
    AirPodsModel.AIRPODS_PRO2_USBC,
    -> AirPodsArtworkTechnology.BITMAP_LAYERS
    else -> AirPodsArtworkTechnology.VECTOR_FALLBACK
}
