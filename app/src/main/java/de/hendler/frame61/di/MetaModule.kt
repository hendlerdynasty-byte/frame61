package de.hendler.frame61.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.meta.CatalogRepository
import de.hendler.frame61.meta.MetadataSource
import de.hendler.frame61.meta.SnapshotEpisodeSource
import de.hendler.frame61.meta.SnapshotMetadataSource
import de.hendler.frame61.meta.SnapshotRepository
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.search.AggregatedIndexSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MetaModule {

    @Provides
    @Singleton
    fun provideCatalog(@ApplicationContext context: Context): CatalogRepository =
        CatalogRepository.fromAsset(context)

    /**
     * The client reads only bundled snapshots (`assets/meta/`). The live
     * TVmaze/Wikipedia classes still exist for the fetch worker, but they are
     * deliberately not provided here: a screen cannot trigger a live call.
     */
    @Provides
    @Singleton
    fun provideSnapshotRepository(@ApplicationContext context: Context): SnapshotRepository =
        SnapshotRepository.fromAssets(context)

    @Provides
    @Singleton
    fun provideSnapshotMetadataSource(
        snapshots: SnapshotRepository,
        catalog: CatalogRepository
    ): SnapshotMetadataSource = SnapshotMetadataSource(snapshots, catalog)

    @Provides
    @Singleton
    fun provideMetadataSource(source: SnapshotMetadataSource): MetadataSource = source

    @Provides
    @Singleton
    fun provideAggregatedIndexSource(
        packLoader: PackLoader,
        interpreter: PackInterpreter,
        catalog: CatalogRepository,
        prefs: ProfileDataStore
    ): AggregatedIndexSource = AggregatedIndexSource(packLoader, interpreter, catalog, prefs)

    @Provides
    @Singleton
    fun provideEpisodeSource(snapshots: SnapshotRepository): SnapshotEpisodeSource =
        SnapshotEpisodeSource(snapshots)
}
