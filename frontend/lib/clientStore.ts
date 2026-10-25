'use client'
import { useCallback, useMemo, useSyncExternalStore } from 'react'

// 브라우저에만 있는 값(localStorage·미디어쿼리·수화 여부)을 리액트가 읽는 방법.
//
// 왜 이펙트가 아닌가 — 이펙트로 읽어 setState 하면 첫 렌더 뒤에 한 번 더 그리고,
// React 19 의 react-hooks/set-state-in-effect 가 그 자리를 막는다.
// useSyncExternalStore 는 서버 렌더에는 기본값을, 브라우저에는 실제 값을 주고
// 값이 바뀌면 구독으로 따라간다. 다른 탭에서 바뀐 것도 storage 이벤트로 들어온다.

// 같은 탭 안에서는 storage 이벤트가 안 오므로 쓰는 쪽이 직접 알린다
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

const subscribeStorage = (cb: () => void) => {
  listeners.add(cb)
  window.addEventListener('storage', cb)
  return () => {
    listeners.delete(cb)
    window.removeEventListener('storage', cb)
  }
}

/**
 * localStorage 에 둔 켬·끔 값을 상태처럼 쓴다.
 * 저장이 막힌 브라우저(사생활 보호 모드 등)에서는 끔으로 읽고 쓰기는 조용히 넘어간다.
 */
export function useStoredFlag(key: string, onValue: string, offValue: string) {
  const getSnapshot = useCallback(() => {
    try {
      return localStorage.getItem(key) === onValue
    } catch {
      return false
    }
  }, [key, onValue])

  const value = useSyncExternalStore(subscribeStorage, getSnapshot, () => false)

  const set = useCallback(
    (next: boolean) => {
      try {
        localStorage.setItem(key, next ? onValue : offValue)
      } catch {
        // 저장이 막혀도 이번 세션 동작에는 지장이 없다
      }
      notify()
    },
    [key, onValue, offValue],
  )

  return [value, set] as const
}

// 저장이 막힌 브라우저에서도 이번 세션 동안은 값이 바뀌어야 입력이 먹는다. 그때 쓰는 자리
const memory = new Map<string, string>()

/**
 * localStorage 에 둔 JSON 값을 상태처럼 쓴다. 스냅샷은 저장된 문자열이라 값이 같으면 같은 참조를 돌려준다
 * (객체를 스냅샷으로 쓰면 매번 새 객체라 무한히 다시 그린다). fallback 은 모듈 상수로 넘긴다.
 * 저장이 막히면 메모리에 두고, 읽기 실패·JSON 깨짐은 fallback 으로 읽는다.
 */
export function useStoredJson<T>(key: string, fallback: T) {
  const getSnapshot = useCallback(() => {
    try {
      return localStorage.getItem(key) ?? memory.get(key) ?? null
    } catch {
      return memory.get(key) ?? null
    }
  }, [key])

  const raw = useSyncExternalStore(subscribeStorage, getSnapshot, () => null)

  const value = useMemo<T>(() => {
    if (raw == null) return fallback
    try {
      return JSON.parse(raw) as T
    } catch {
      return fallback
    }
  }, [raw, fallback])

  const set = useCallback(
    (next: T) => {
      const json = JSON.stringify(next)
      memory.set(key, json)
      try {
        localStorage.setItem(key, json)
      } catch {
        // 메모리에 둔 값으로 이번 세션은 돈다
      }
      notify()
    },
    [key],
  )

  return [value, set] as const
}

/** 미디어쿼리가 지금 맞는지. 창 크기가 바뀌면 따라간다. 서버 렌더에서는 안 맞는 것으로 본다 */
export function useMediaQuery(query: string) {
  const subscribe = useCallback(
    (cb: () => void) => {
      const mq = window.matchMedia(query)
      mq.addEventListener('change', cb)
      return () => mq.removeEventListener('change', cb)
    },
    [query],
  )
  const getSnapshot = useCallback(() => window.matchMedia(query).matches, [query])
  return useSyncExternalStore(subscribe, getSnapshot, () => false)
}

/**
 * 브라우저에서 한 번이라도 그려졌는지. 서버가 그린 첫 화면에는 false 다.
 * 전환 효과를 수화 전까지 꺼 두는 데 쓴다 — 접힌 채로 들어왔는데 펼친 폭에서 밀려오면 눈에 띈다.
 */
export function useHydrated() {
  return useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  )
}
