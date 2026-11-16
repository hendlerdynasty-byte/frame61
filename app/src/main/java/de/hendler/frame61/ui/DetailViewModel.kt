package de.hendler.frame61.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.hendler.frame61.meta.CatalogRepository
import javax.inject.Inject

/** Exposes the catalog to the detail screen without duplicating loading logic. */
@HiltViewModel
class DetailViewModel @Inject constructor(
    val catalog: CatalogRepository
) : ViewModel()