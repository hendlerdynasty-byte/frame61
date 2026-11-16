package de.hendler.frame61.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hendler.frame61.meta.BundledCatalogSource
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.ChainedMetadataSource
import de.hendler.frame61.meta.MetadataSource
import de.hendler.frame61.meta.TvmazeEpisodeSource
import de.hendler.frame61.meta.TvmazeMetadataSource
import de.hendler.frame61.meta.WikipediaMetadataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MetaModule {

    @Provides
    @Singleton
    fun provideCatalog(@ApplicationContext context: Context): CatalogRepository =
        CatalogRepository.fromAsset(context)

    /**
     * Bundled catalog first (always answers offline), then the two keyless live
     * sources. No API key anywhere; if both live sources fail the catalog stands.
     */
    @Provides
    @Singleton
    fun provideMetadataSource(catalog: CatalogRepository): MetadataSource =
        ChainedMetadataSource(
            listOf(
                BundledCatalogSource(catalog),
                TvmazeMetadataSource(),
                WikipediaMetadataSource()
            )
        )

    /** Keyless TVmaze episode lists for the series detail screen. */
    @Provides
    @Singleton
    fun provideEpisodeSource(): TvmazeEpisodeSource = TvmazeEpisodeSource()

    /**
     * Individual sources for screens that merge results themselves (search).
     * `SearchViewModel` collects this list; the single [MetadataSource] above is
     * the chained façade for everyone else.
     */
    @Provides
    @Singleton
    fun provideMetadataSources(catalog: CatalogRepository): List<MetadataSource> =
        listOf(
            BundledCatalogSource(catalog),
            TvmazeMetadataSource(),
            WikipediaMetadataSource()
        )
}