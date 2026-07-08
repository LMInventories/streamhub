package com.android.streamhub.feature.jellyfin.di

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.feature.jellyfin.data.JellyfinMediaSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class JellyfinSourceModule {

    @Binds
    @IntoSet
    abstract fun bindJellyfinMediaSource(source: JellyfinMediaSource): MediaSource
}
