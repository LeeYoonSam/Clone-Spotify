package com.ys.spotify.exoplayer

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.net.toUri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.ys.spotify.data.remote.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FirebaseMusicSource @Inject constructor(
    private val musicDatabase: MusicDatabase
){

    var songs = emptyList<MediaMetadataCompat>()

    suspend fun fetchMediaData() = withContext(Dispatchers.IO) {

        state = State.STATE_INITIALIZING

        // 노래 데이터를 가져와서 메타데이터 추가
        val allSongs = musicDatabase.getAllSongs()
        songs = allSongs.map { song ->
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, song.subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, song.subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, song.imageUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, song.imageUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, song.songUrl)
                .build()

        }

        // 데이터를 가져온 후 상태 설정
        state = State.STATE_INITIALIZED
    }

    /**
     * 여러 MediaSource를 연결합니다.
     * MediaSources 목록은 재생 중에 수정할 수 있습니다.
     * 동일한 MediaSource 인스턴스가 연결에 두 번 이상 존재하는 것은 유효합니다.
     * 이 클래스에 대한 액세스는 스레드로부터 안전합니다.
     */
    fun asMediaSource(dataSourceFactory: DataSource.Factory): ConcatenatingMediaSource {
        val concatenatingMediaSource = ConcatenatingMediaSource()
        songs.forEach { song ->
            // Uri에서 데이터를 로드하고 Extractor를 사용하여 추출하는 하나의 기간을 제공합니다.
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(
                    MediaItem.fromUri(
                        song.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUri()
                    )
                )
            concatenatingMediaSource.addMediaSource(mediaSource)
        }

        return concatenatingMediaSource
    }

    /**
     * 미디어 검색에 사용할 새 MediaItem 을 만듭니다.
     * description 에는 mediaId 가 포함되어야 한다
     */
    fun asMediaItems() = songs.map { song ->

        /**
         * MediaDescriptionCompat
         * 표시에 적합한 미디어 항목에 대한 간단한 메타데이터 집합입니다.
         * 빌더를 사용하여 생성하거나 다음을 사용하여 기존 메타데이터에서 검색할 수 있습니다.
         */
        val desc = MediaDescriptionCompat.Builder()
            .setMediaUri(song.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUri())
            .setTitle(song.description.title)
            .setSubtitle(song.description.subtitle)
            .setMediaId(song.description.mediaId)
            .setIconUri(song.description.iconUri)
            .build()

        MediaBrowserCompat.MediaItem(desc, FLAG_PLAYABLE)
    }

    /**
     * boolean 으로 나중에 소스가 초기화 되었는지 확인
     * 음악 소스를 호출 할 준비가 되면 코드 블록을 실행
     */
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    private var state: State = State.STATE_CREATED
        set(value) {
            if (value == State.STATE_INITIALIZED || value == State.STATE_ERROR) {
                // 동일한 쓰레드에서만 접근 가능, onReadyListeners 를 동시에 접근 불가능
                synchronized(onReadyListeners) {
                    field = value

                    onReadyListeners.forEach { listener ->
                        listener(value == State.STATE_INITIALIZED)
                    }
                }
            } else {
                field = value
            }
        }

    fun whenReady(action: (Boolean) -> Unit): Boolean {
        return if (state == State.STATE_CREATED || state == State.STATE_INITIALIZING) {
            onReadyListeners += action
            false
        } else {
            action(state == State.STATE_INITIALIZED)
            true
        }
    }

}

enum class State {
    STATE_CREATED,
    STATE_INITIALIZING,
    STATE_INITIALIZED,
    STATE_ERROR
}