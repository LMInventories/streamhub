package com.android.streamhub.di

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.mock.MockMediaSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Registers MockMediaSource into the MediaSource multibinding Home/Search iterate over. Real
 * IPTV/Jellyfin/Emby modules add their own @Binds @IntoSet entry the same way - nothing else
 * in the app needs to change when they do.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MockSourceModule {

    @Binds
    @IntoSet
    abstract fun bindMockMediaSource(mockMediaSource: MockMediaSource): MediaSource
}
