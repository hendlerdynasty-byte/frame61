package de.hendler.frame61.ui

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * Current window size class, provided once by the app shell.
 *
 * TV (expanded) and phone (compact) share colors and typography; only spacing
 * and the number of visible columns change.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass?> { null }

/** User density override: 0 = compact, 1 = regular, 2 = roomy. */
val LocalUiDensity = compositionLocalOf { 1 }
