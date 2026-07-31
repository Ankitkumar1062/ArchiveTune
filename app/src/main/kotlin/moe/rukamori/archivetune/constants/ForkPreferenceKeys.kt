package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey

/*
 * Preference keys for this fork's own features, kept OUT of PreferenceKeys.kt on purpose.
 *
 * The mirror workflow replaces every file upstream also ships, so a key appended to
 * PreferenceKeys.kt is silently dropped by the next sync and the fork code referencing it stops
 * compiling. That already happened once at 21247770f. This file is listed in .mirror-keep, so it
 * survives.
 *
 * Only keys used by fork-only code belong here. Anything upstream also declares must NOT be
 * repeated -- two declarations of the same name is a Kotlin redeclaration error. The four Deezer
 * keys are a case in point: upstream merged this fork's Deezer support and now declares
 * DeezerEnabledKey, DeezerArlKey, DeezerAccountNameKey and DeezerAccountPremiumKey themselves, so
 * they stay in their file and are deliberately absent here.
 *
 * The string passed to booleanPreferencesKey is the on-disk DataStore name. Renaming one orphans
 * the stored setting on every existing install, so treat these as permanent.
 */

/** Whether a lossless download should be tagged with metadata and artwork after it completes. */
val LosslessDownloadTagKey = booleanPreferencesKey("losslessDownloadTag")
