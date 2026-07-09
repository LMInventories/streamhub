package com.android.streamhub.feature.iptv.di

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.feature.iptv.data.IptvMediaSource
import com.android.streamhub.feature.iptv.data.RecordingsMediaSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class IptvSourceModule {

    @Binds
    @IntoSet
    abstract fun bindIptvMediaSource(source: IptvMediaSource): MediaSource

    @Binds
    @IntoSet
    abstract fun bindRecordingsMediaSource(source: RecordingsMediaSource): MediaSource
}
