# Kaon Music — Vision & Feature Specification

Based on the UI redesign plan and the interactive feature expansion plans.  

---

# Project Goal

Kaon Music is a modern, premium, offline-first Android music player built with Jetpack Compose and Media3. The application focuses on clean architecture, smooth animations, Material 3 design, high-performance playback, and a polished user experience comparable to Poweramp, Oto Music, Musicolet, and Apple Music.

The application follows a microkernel/plugin architecture where the media engine, library, and UI remain loosely coupled.

---

# Design Philosophy

* Pixel-perfect modern UI
* Material 3 + Material You support
* Dynamic album-based colors
* Smooth animations everywhere
* Responsive on phones and tablets
* Lightweight and fast
* Offline-first
* Premium without unnecessary complexity

---

# Core Architecture

```
App
│
├── Core
│     ├── Kernel
│     ├── Plugin System
│     ├── Event Bus
│     └── Services
│
├── Media
│     ├── Playback Engine
│     ├── Queue Manager
│     ├── Artwork Loader
│     ├── Palette Cache
│     └── Library Scanner
│
├── UI Plugin
│     ├── Discover
│     ├── Library
│     ├── Search
│     ├── Player
│     └── Settings
│
└── Database
```

---

# Navigation

Bottom Navigation

```
Discover
Library
Search
Settings
```

The queue is accessed from the player instead of occupying a bottom navigation tab.

---

# Main Screens

## Discover

Future recommendation page.

Planned sections:

* Recently Added
* Recently Played
* Most Played
* Favorite Songs
* Random Mix
* Albums
* Artists
* Continue Listening

---

## Library

Tabs

```
Songs
Albums
Artists
Folders
Playlists (future)
```

---

## Search

Searches across:

* Songs
* Albums
* Artists
* Folders

Results are grouped by category.

---

## Player

Contains

* Dynamic gradient background
* Album artwork
* Song information
* Progress bar
* Playback controls
* Queue
* Shuffle
* Repeat
* Share
* More menu

---

## Settings

Contains

* Theme selection
* Material You toggle
* Playback preferences
* About
* Version
* Future settings

---

# Album Screen

Displays

* Album artwork
* Album title
* Artist
* Release year
* Genre
* Song count
* Total duration

Buttons

```
Play Album
Shuffle Album
```

Song list appears underneath.

---

# Artist Screen

Displays

* Artist artwork
* Artist name
* Album count
* Song count
* Total duration

Contains

* Play All
* Shuffle All
* Albums
* Songs

---

# Folder Browser

Works like a normal file explorer.

```
Music
   ↓
Rock
   ↓
Classic Rock
   ↓
Songs
```

Opening a folder displays its contents instead of immediately starting playback.

---

# Queue

The queue contains

* Current song
* Up Next
* Queue duration
* Queue length

Supports

* Jump to song
* Drag to reorder
* Swipe to remove
* Clear queue
* Save as playlist (future)

---

# Song Context Menu

Available from every song.

Options

```
Play Next
Add to Queue
Add to Playlist
Go to Album
Go to Artist
Share
Song Info
```

Song Info displays

* Duration
* Bitrate
* Codec
* Sample Rate
* File Size
* File Path

---

# Now Playing Menu

Options

```
Playback Speed
Sleep Timer
Go to Album
Go to Artist
```

Playback speed

```
0.5x
0.75x
1.0x
1.25x
1.5x
2.0x
```

Sleep timer

```
15 min
30 min
45 min
60 min
End of Current Song
Cancel
```

---

# Favorites

Favorites are stored permanently in the database.

Each song contains

```
favorite : Boolean
```

LibraryController exposes

```
toggleFavorite(songId)
```

---

# Dynamic Colors

Each album artwork generates a palette.

```
Artwork
      ↓
Palette Extraction
      ↓
ArtworkColors
      ↓
PlaybackState
      ↓
UI
```

Colors include

* Dominant
* Vibrant
* Muted
* Text contrast

The player background automatically adapts to the current song.

---

# UI Components

Reusable components

* GradientBackground
* PlayButton
* SlimProgressBar
* TrackListItem
* AsyncImage
* BottomNavigation
* SongContextSheet
* NowPlayingMenu

---

# Animations

Animated elements

* Background gradient
* Album artwork
* Shared mini player transition
* Queue animations
* Progress updates
* Button presses
* Screen transitions

Animations should remain smooth (approximately 60 FPS).

---

# Audio Support

Supported formats

```
MP3
AAC
M4A
FLAC
WAV
OGG
OPUS
```

Future

```
ALAC
DSD (optional)
```

---

# Playback Features

Supports

* Play
* Pause
* Previous
* Next
* Seek
* Shuffle
* Repeat One
* Repeat All
* Repeat Off
* Play Next
* Queue Management
* Playback Speed
* Sleep Timer

---

# Metadata

Display whenever available

* Title
* Artist
* Album
* Album Artist
* Genre
* Year
* Track Number
* Disc Number
* Composer
* Bitrate
* Codec
* Sample Rate
* Duration
* File Size

---

# Future Features

## Playlist System

* Create Playlist
* Rename Playlist
* Delete Playlist
* Smart Playlists
* Import/Export Playlists

---

## Smart Collections

* Liked Songs
* Recently Played
* Most Played
* Never Played
* Recently Added

---

## Discover

Future recommendation engine

* Recently Added
* Most Played
* Random Albums
* Continue Listening

---

## Audio Enhancements

* Equalizer
* Bass Boost
* Loudness
* ReplayGain
* Gapless Playback
* Crossfade

---

## Lyrics

* Embedded lyrics
* Local LRC support
* Synced lyrics
* Karaoke mode (future)

---

## Android Integration

* Media notifications
* Lock screen controls
* Android Auto
* Bluetooth controls
* Headset controls
* Home screen widgets

---

## Performance Goals

* Fast startup
* Smooth scrolling
* Lazy loading everywhere
* Cached artwork
* Cached palettes
* Efficient queue updates
* Minimal memory usage
* Responsive on low-end devices

---

# Overall Vision

Kaon Music aims to be a polished, modern, offline music player with a clean Material 3 interface, dynamic album-based theming, excellent playback performance, rich library management, and an extensible plugin architecture. The experience should feel premium through thoughtful design, smooth interactions, and comprehensive music management rather than feature overload.
