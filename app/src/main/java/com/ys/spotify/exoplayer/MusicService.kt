package com.ys.spotify.exoplayer

import android.app.PendingIntent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultDataSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import javax.inject.Inject

private const val SERVICE_TAG = "MusicService"

@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var dataSourceFactory: DefaultDataSource.Factory

    @Inject
    lateinit var exoplayer: ExoPlayer

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

    /**
     * 관련 변수들을 초기화
     */
    override fun onCreate() {
        super.onCreate()

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

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlayer(exoplayer)

    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        TODO("Not yet implemented")
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        TODO("Not yet implemented")
    }
}