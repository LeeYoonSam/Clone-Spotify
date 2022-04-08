package com.ys.spotify.exoplayer

class FirebaseMusicSource {

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