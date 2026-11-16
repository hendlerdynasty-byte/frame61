package de.hendler.frame61.scraper.engine

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScraperEngineModule {

    @Provides
    @Singleton
    fun providePackHttpClient(): PackHttpClient = PackHttpClient()

    @Provides
    @Singleton
    fun providePackLoader(@ApplicationContext context: Context): PackLoader = PackLoader(context)

    @Provides
    @Singleton
    fun providePackInterpreter(http: PackHttpClient): PackInterpreter = PackInterpreter(http)
}