package com.android.streamhub.feature.emby.di

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.feature.emby.data.EmbyMediaSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbySourceModule {

    @Binds
    @IntoSet
    abstract fun bindEmbyMediaSource(source: EmbyMediaSource): MediaSource
}
