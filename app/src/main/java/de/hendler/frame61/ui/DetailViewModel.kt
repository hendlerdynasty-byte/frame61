package de.hendler.frame61.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.TvmazeEpisodeSource
import javax.inject.Inject

/** Exposes catalog and episode metadata to the detail screen. */
@HiltViewModel
class DetailViewModel @Inject constructor(
    val catalog: CatalogRepository,
    val episodes: TvmazeEpisodeSource
) : ViewModel()