# Spotify Clone

## DaggerHilt

### SongAdapter, SwipeSongAdapter 주입방식

**SongAdapter**
```kotlin
class SongAdapter @Inject constructor(
    private val glide: RequestManager
) : BaseSongAdapter(R.layout.list_item)
```

**SwipeSongAdapter**
```kotlin
class SwipeSongAdapter : BaseSongAdapter(R.layout.list_item)
```

- `SongAdapter` 는 module 에서 provide 로 제공하지 않아도 `@Inject constructor` 를 통해서 DaggerHilt 가 자동으로 필요한 것을 주입하고 생성을 해준다.
- `SwipeSongAdapter` 는 생성자가 없고 생성하기 매우 쉽더라도 실제로 이를 알지 못하므로 명시적으로 삽입을 해야한다.
- `SwipeSongAdapter` 를 제공하는 방법은 2가지가 있다.
    1. `@Inject constructor()` 빈생성자를 추가하고 @Inject 를 명시해서 생성
    2. `@Module`에서 @Provides 로 직접 생성

---

## 발생한 문제 리포트

### MusicService sendResult 에러

**에러**
```
java.lang.IllegalStateException: sendResult() called when either sendResult() or sendError() had already been called for: root_id
```

- 이미 호출되어서 오류가 발생

```
MediaBrowserServiceCompat 오버로드 함수

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
                            result.sendResult(null)
                        }
                    }
                }

                if (!resultsSent) {
                    result.detach()
                }
            }
        }
    }
```

**해결**
- 정확한 해결 방법은 나중에 찾아보고 result.sendResult(null) 를 주석처리해서 일단 패스

### FireStore 권한 문제

**에러**
```
W/Firestore: (24.1.0) [Firestore]: Listen for Query(target=Query(songs order by __name__);limitType=LIMIT_TO_FIRST) failed: Status{code=PERMISSION_DENIED, description=Missing or insufficient permissions., cause=null}
```

**해결**
```
service cloud.firestore {
  match /databases/{database}/documents {

    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

- if true 를 사용해서 모두 허용으로 변경
- 개발 테스트를 위한 용도라서 다른 보안은 신경쓰지 않고 접근이 되도록 변경

**참고**
- [Cloud Firestore 보안 규칙 시작하기](https://firebase.google.com/docs/firestore/security/get-started?hl=ko#allow-all)


###
**에러**
```
java.lang.IllegalStateException: Player is accessed on the wrong thread.
```

- ExoPlayer 는 main 쓰레드에서 접근을 해야하는데 다른 쓰레드에서 접근할때 발생하는 에러
- exoplayer.prepare() 에서 발생하는 에러

**해결**
```
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
```
- exoplayer 관련 코드를 serviceScope 안으로 이동해서 main 쓰레드에서 작업하도록 수정