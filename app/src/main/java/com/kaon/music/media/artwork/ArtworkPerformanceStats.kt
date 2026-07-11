package com.kaon.music.media.artwork

data class ArtworkPerformanceStats(
    val totalRequests: Long,
    val memoryHits: Long,
    val memoryEvictions: Long,
    val diskHits: Long,
    val diskWrites: Long,
    val diskReads: Long,
    val negativeCacheHits: Long,
    val failedDecodes: Long,
    val decodeCount: Long,
    val paletteCacheHits: Long,
    val paletteExtractions: Long,
    val pendingJobDeduplications: Long,
    val averageDecodeMs: Double,
    val averagePaletteExtractionMs: Double,
    val memoryHitRate: Double,
    val diskHitRate: Double,
    val decodeMissRate: Double,
    val lastDecodeTimestamp: Long? = null
)
