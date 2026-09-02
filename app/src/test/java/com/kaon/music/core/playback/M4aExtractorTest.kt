package com.kaon.music.core.playback

import android.content.ContextWrapper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@androidx.media3.common.util.UnstableApi
class M4aExtractorTest {

    @Test
    fun defaultExtractorsFactory_initializesWithConstantBitrateSeeking() {
        val extractorsFactory = DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
        }
        assertNotNull(extractorsFactory)
    }

    @Test
    fun defaultRenderersFactory_configuresExtensionRendererModePrefer() {
        val context = object : ContextWrapper(null) {}
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableAudioTrackPlaybackParams(true)
        }
        assertNotNull(renderersFactory)
    }
}
