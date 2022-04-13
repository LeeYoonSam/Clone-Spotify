package com.ys.spotify.exoplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.ys.spotify.common.Constants.MEDIA_ROOT_ID
import com.ys.spotify.common.Constants.NETWORK_ERROR
import com.ys.spotify.exoplayer.callbacks.MusicPlaybackPreparer
import com.ys.spotify.exoplayer.callbacks.MusicPlayerEventListener
import com.ys.spotify.exoplayer.callbacks.MusicPlayerNotificationListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val SERVICE_TAG = "MusicService"

@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var dataSourceFactory: DefaultDataSource.Factory

    @Inject
    lateinit var exoplayer: ExoPlayer

    @Inject
    lateinit var firebaseMusicSource: FirebaseMusicSource

    private lateinit var musicNotificationManager: MusicNotificationManager

    /**
     * Firebase 에서 데이터를 비동기로 가져올때 사용할 코루틴 Job
     */
    private val serviceJob = Job()

    /**
     * 서비스가 종료되면 아직 사용중인 취소되고 메모리 누수로 이어지지 않게 서비스가 종료되도록 처리
     * 코루틴 컨텍스트를 합쳐서 사용자 정의 서비스 Scope 을 생성
     */
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    /**
     * 미디어 세션에 대한 정보를 서비스와 통신하는데 사용할 수 있음
     */
    private lateinit var mediaSession: MediaSessionCompat

    /**
     * 미디어 세션에 연결하는데 사용
     */
    private lateinit var mediaSessionConnector: MediaSessionConnector

    var isForegroundService = false

    private var curPlayingSong: MediaMetadataCompat? = null

    private var isPlayerInitialized = false

    private lateinit var musicPlayerEventListener: MusicPlayerEventListener

    companion object {
        // 서비스내에서만 값을 변경할수 있도록 private set 으로 지정
        var curSongDuration = 0L
            private set
    }

    /**
     * 관련 변수들을 초기화
     */
    override fun onCreate() {
        super.onCreate()

        serviceScope.launch {
            firebaseMusicSource.fetchMediaData()
        }

        /**
         * 알림에 대한 액티비티를 얻고 클릭하면 액티비티를 실행
         */
        val activityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, 0)
        }

        mediaSession = MediaSessionCompat(this, SERVICE_TAG).apply {
            setSessionActivity(activityIntent)
            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        musicNotificationManager = MusicNotificationManager(
            this,
            mediaSession.sessionToken,
            MusicPlayerNotificationListener(this)
        ) {
            curSongDuration = exoplayer.duration
        }

        val musicPlaybackPreparer = MusicPlaybackPreparer(firebaseMusicSource) {
            curPlayingSong = it
            preparePlayer(
                songs = firebaseMusicSource.songs,
                itemToPlay = it,
                playNow = true
            )
        }

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(musicPlaybackPreparer)
        mediaSessionConnector.setQueueNavigator(MusicQueueNavigator())
        mediaSessionConnector.setPlayer(exoplayer)

        musicPlayerEventListener = MusicPlayerEventListener(this)
        exoplayer.addListener(MusicPlayerEventListener(this))
        musicNotificationManager.showNotification(exoplayer)
    }

    /**
     * 노래에 대한 정보를 알림에 전달 후 표시
     */
    private inner class MusicQueueNavigator : TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            return firebaseMusicSource.songs[windowIndex].description
        }
    }

    /**
     * exoPlayer 재생 준비
     *
     * @param songs 재생 목록
     * @param itemToPlay 현재 선택된 미디어
     * @param playNow 바로 재생 여부
     */
    private fun preparePlayer(
        songs: List<MediaMetadataCompat>,
        itemToPlay: MediaMetadataCompat?,
        playNow: Boolean
    ) {
        val curSongIndexId = if (curPlayingSong == null) 0 else songs.indexOf(itemToPlay)

        serviceScope.launch {
            exoplayer.prepare()
            exoplayer.setMediaSource(firebaseMusicSource.asMediaSource(dataSourceFactory))
            exoplayer.seekTo(curSongIndexId, 0L)
            exoplayer.playWhenReady = playNow
        }
    }


    /**
     * 서비스의 작업이 제거 되었을때
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        exoplayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        // 메모리 누수를 방지하기 위해 리스너 제거
        exoplayer.removeListener(musicPlayerEventListener)
        exoplayer.release()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        when (parentId) {
            MEDIA_ROOT_ID -> {
                val resultsSent = firebaseMusicSource.whenReady { isInitialized ->
                    if (isInitialized) {
                        result.sendResult(firebaseMusicSource.asMediaItems())

                        if (!isPlayerInitialized && firebaseMusicSource.songs.isNotEmpty()) {
                            // 앱이 열리면 재생준비를 하지만 바로 재생하지는 않도록 처리
                            preparePlayer(
                                songs = firebaseMusicSource.songs,
                                itemToPlay = firebaseMusicSource.songs.first(),
                                playNow = false
                            )

                            isPlayerInitialized = true
                        } else {
                            mediaSession.sendSessionEvent(NETWORK_ERROR, null )
//                            result.sendResult(null)
                        }
                    }
                }

                if (!resultsSent) {
                    result.detach()
                }
            }
        }
    }
}