# Upstream & External Dependency Pins

This document tracks all external and ported upstream dependencies for Kaon Music.

---

## InnerTube & YouTube Streaming Core

| Component / Artifact | Group & Module | Pinned Version / Commit Hash | Upstream Repository & Source |
|:---|:---|:---|:---|
| **NewPipe Extractor** | `com.github.TeamNewPipe:NewPipeExtractor` | `v0.24.4` | [NewPipeExtractor GitHub](https://github.com/TeamNewPipe/NewPipeExtractor) |
| **InnerTubeX** | `com.github.MetrolistGroup.innertubex:innertubex` | `v0.1.2` | [innertubex GitHub](https://github.com/MetrolistGroup/innertubex) |
| **InnerTube Module** | `:innertube` (Internal Module) | Ported from `MetrolistGroup/Metrolist@main` | [Metrolist innertube](https://github.com/MetrolistGroup/Metrolist/tree/main/innertube) |
| **Ktor Client Core** | `io.ktor:ktor-client-core` | `3.1.0` | [Ktor Official](https://ktor.io/) |
| **Ktor OkHttp Engine** | `io.ktor:ktor-client-okhttp` | `3.1.0` | [Ktor OkHttp](https://ktor.io/docs/client-engines.html#okhttp) |
| **Kotlinx Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) |
| **Brotli Decoder** | `org.brotli:dec` | `0.1.2` | [Google Brotli](https://github.com/google/brotli) |
| **JDK Desugaring** | `com.android.tools:desugar_jdk_libs_nio` | `2.1.4` | Android Desugaring Tools |

---

## Architectural & ToS Notice
* YouTube streams are resolved dynamically on demand and played in volatile memory without permanent disk storage.
* Cipher decoding is performed via `MetrolistExtractor` / `NewPipeExtractor` algorithms.
