package com.android.streamhub.core.player.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloaderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the read-only CacheDataSource.Factory PlayerController uses for playback (never writes new content into the download cache) from the read-write one DownloadManager's downloader uses. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackCacheDataSource

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideDownloadDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    // App-private storage (context.filesDir), same convention RecordedItemEntity's filePath
    // comment already established - no MANAGE_EXTERNAL_STORAGE or scoped-storage dance needed
    // for a cache only this app ever reads, and it's cleaned up automatically on uninstall.
    // NoOpCacheEvictor - downloaded content is only ever removed by an explicit user delete
    // (DownloadTracker.removeDownload), never evicted automatically to make room.
    @Provides
    @Singleton
    fun provideDownloadCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): Cache {
        val downloadDir = File(context.filesDir, "downloads")
        return SimpleCache(downloadDir, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(): HttpDataSource.Factory = DefaultHttpDataSource.Factory()

    /** Read-write - this is what actually pulls bytes into the cache when a download runs. */
    @Provides
    @Singleton
    fun provideDownloaderCacheDataSourceFactory(cache: Cache, httpDataSourceFactory: HttpDataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)

    /**
     * Read-only - used by PlayerController for every playback, downloaded or not, so a
     * downloaded item plays back from the local cache transparently through the exact same
     * streamUri it would otherwise be streamed from. setCacheWriteDataSinkFactory(null) is what
     * makes this read-only: without it, CacheDataSource would also silently start caching
     * whatever the user merely streams (never downloaded), slowly filling storage with content
     * nobody asked to keep.
     */
    @Provides
    @Singleton
    @PlaybackCacheDataSource
    fun providePlaybackCacheDataSourceFactory(cache: Cache, httpDataSourceFactory: HttpDataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)

    @Provides
    @Singleton
    fun provideDownloaderFactory(downloaderCacheDataSourceFactory: CacheDataSource.Factory): DownloaderFactory =
        DefaultDownloaderFactory(downloaderCacheDataSourceFactory, Executors.newFixedThreadPool(3))

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: Cache,
        downloaderFactory: DownloaderFactory,
    ): DownloadManager =
        DownloadManager(context, databaseProvider, cache, downloaderFactory, Executors.newFixedThreadPool(3)).apply {
            maxParallelDownloads = 3
        }
}
