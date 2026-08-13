package com.android.streamhub.feature.emby.di

import com.android.streamhub.core.common.library.EmbyLibraryFinder
import com.android.streamhub.core.common.library.LibraryTitleFinder
import com.android.streamhub.feature.emby.data.EmbyLibraryTitleFinder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbyLibraryModule {

    @Binds
    @EmbyLibraryFinder
    abstract fun bindEmbyLibraryTitleFinder(impl: EmbyLibraryTitleFinder): LibraryTitleFinder
}
