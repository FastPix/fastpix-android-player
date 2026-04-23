package io.fastpix.media3.core

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import java.util.UUID

/**
 * SDK-level DRM abstraction to avoid leaking Media3 DRM internals.
 */
data class DrmConfig(
    val uuid: UUID = C.WIDEVINE_UUID,
    val multiSession: Boolean = true,
)

/**
 * Internal manager that builds Media3 DRM config from SDK config.
 */
internal object DrmManager {

    fun buildMediaItemDrmConfiguration(
        drmConfig: DrmConfig?,
        playbackId: String,
        playbackToken: String?,
        streamType: StreamType?
    ): MediaItem.DrmConfiguration? {
        if (drmConfig == null || playbackToken == null) return null
        val drmLicenseUrl =
            "https://api.fastpix.co/v1/${streamType?.stream}/drm/license/widevine/$playbackId?token=$playbackToken"
        return MediaItem.DrmConfiguration.Builder(drmConfig.uuid)
            .setLicenseUri(drmLicenseUrl)
            .setMultiSession(drmConfig.multiSession)
            .build()
    }
}
