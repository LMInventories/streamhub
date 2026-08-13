package com.android.streamhub.feature.jellyfin.di

import com.android.streamhub.core.common.library.JellyfinLibraryFinder
import com.android.streamhub.core.common.library.LibraryTitleFinder
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryTitleFinder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class JellyfinLibraryModule {

    @Binds
    @JellyfinLibraryFinder
    abstract fun bindJellyfinLibraryTitleFinder(impl: JellyfinLibraryTitleFinder): LibraryTitleFinder
}
