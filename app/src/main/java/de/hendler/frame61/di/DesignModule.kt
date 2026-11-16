package de.hendler.frame61.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hendler.frame61.design.DefaultAssetSkinManager
import de.hendler.frame61.design.SkinJsonParser
import de.hendler.frame61.design.SkinManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DesignModule {

    @Provides
    @Singleton
    fun provideSkinManager(@ApplicationContext context: Context): SkinManager =
        DefaultAssetSkinManager(context, SkinJsonParser())
}