import { useEffect, useRef } from 'react';
import Hls from 'hls.js';

export type FeedVideoPreload = 'none' | 'metadata' | 'auto';

export interface FeedVideoPlaybackMetrics {
  startupMs: number | null;
  stallCount: number;
  stalledMs: number;
  watchMs: number;
  playSuccess: boolean;
  autoplayBlockedCount: number;
  debugTimingsMs?: {
    sourceToLoadedDataMs: number | null;
    sourceToCanPlayMs: number | null;
    sourceToPlayingMs: number | null;
  };
}

interface FeedVideoPlayerProps {
  sourceUrl: string;
  poster?: string | null;
  showPoster?: boolean;
  className?: string;
  preload?: FeedVideoPreload;
  controls?: boolean;
  loop?: boolean;
  isActive: boolean;
  isPaused: boolean;
  shouldAutoplay: boolean;
  onPlaybackBlocked?: () => void;
  onPlaybackStarted?: () => void;
  onBufferingChange?: (isBuffering: boolean) => void;
  onPlaybackMetrics?: (metrics: FeedVideoPlaybackMetrics) => void;
}

const MAX_NETWORK_RECOVERY_RETRIES = 2;
const NETWORK_RECOVERY_DELAY_MS = 400;
const BUFFERING_SHOW_DELAY_MS = 180;
const RECENT_PLAYBACK_PROGRESS_MS = 400;
const PLAYBACK_PROGRESS_EPSILON_SECONDS = 0.05;

export const isHlsStreamUrl = (url: string | null | undefined) => {
  if (!url) {
    return false;
  }
  return url.toLowerCase().includes('.m3u8');
};

const isAutoplayBlockedError = (error: unknown) => {
  if (!error) {
    return false;
  }
  if (error instanceof DOMException) {
    return error.name === 'NotAllowedError';
  }
  if (error instanceof Error) {
    return error.name === 'NotAllowedError' || error.message.toLowerCase().includes('notallowederror');
  }
  return false;
};

const nowMs = () => performance.now();

export function FeedVideoPlayer({
  sourceUrl,
  poster,
  showPoster = false,
  className,
  preload = 'metadata',
  controls = false,
  loop = true,
  isActive,
  isPaused,
  shouldAutoplay,
  onPlaybackBlocked,
  onPlaybackStarted,
  onBufferingChange,
  onPlaybackMetrics,
}: FeedVideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  const bufferingStateRef = useRef(false);
  const bufferingTimeoutRef = useRef<number | null>(null);
  const usingHlsJsRef = useRef(false);
  const networkRecoveriesRef = useRef(0);
  const startupStartedAtRef = useRef<number | null>(null);
  const startupMsRef = useRef<number | null>(null);
  const stallCountRef = useRef(0);
  const stalledMsRef = useRef(0);
  const stallStartedAtRef = useRef<number | null>(null);
  const watchMsRef = useRef(0);
  const watchStartedAtRef = useRef<number | null>(null);
  const autoplayBlockedCountRef = useRef(0);
  const lastPlaySucceededRef = useRef(false);
  const sourceAttachedAtRef = useRef<number | null>(null);
  const loadedDataAtRef = useRef<number | null>(null);
  const canPlayAtRef = useRef<number | null>(null);
  const firstPlayingAtRef = useRef<number | null>(null);
  const lastPlaybackProgressAtRef = useRef<number | null>(null);
  const lastObservedCurrentTimeRef = useRef<number | null>(null);
  const lowReadinessStartedAtRef = useRef<number | null>(null);

  const finalizeWatch = () => {
    if (watchStartedAtRef.current == null) {
      return;
    }
    watchMsRef.current += Math.max(0, nowMs() - watchStartedAtRef.current);
    watchStartedAtRef.current = null;
  };

  const finalizeStall = () => {
    if (stallStartedAtRef.current == null) {
      return;
    }
    stalledMsRef.current += Math.max(0, nowMs() - stallStartedAtRef.current);
    stallStartedAtRef.current = null;
  };

  const emitMetrics = () => {
    finalizeWatch();
    finalizeStall();
    const metrics: FeedVideoPlaybackMetrics = {
      startupMs: startupMsRef.current,
      stallCount: stallCountRef.current,
      stalledMs: Math.round(stalledMsRef.current),
      watchMs: Math.round(watchMsRef.current),
      playSuccess: lastPlaySucceededRef.current,
      autoplayBlockedCount: autoplayBlockedCountRef.current,
      debugTimingsMs: {
        sourceToLoadedDataMs:
          sourceAttachedAtRef.current != null && loadedDataAtRef.current != null
            ? Math.round(Math.max(0, loadedDataAtRef.current - sourceAttachedAtRef.current))
            : null,
        sourceToCanPlayMs:
          sourceAttachedAtRef.current != null && canPlayAtRef.current != null
            ? Math.round(Math.max(0, canPlayAtRef.current - sourceAttachedAtRef.current))
            : null,
        sourceToPlayingMs:
          sourceAttachedAtRef.current != null && firstPlayingAtRef.current != null
            ? Math.round(Math.max(0, firstPlayingAtRef.current - sourceAttachedAtRef.current))
            : null,
      },
    };
    const hasSignal =
      metrics.startupMs !== null ||
      metrics.watchMs > 0 ||
      metrics.stalledMs > 0 ||
      metrics.autoplayBlockedCount > 0;
    if (!hasSignal) {
      return;
    }
    onPlaybackMetrics?.(metrics);
    startupStartedAtRef.current = null;
    startupMsRef.current = null;
    stallCountRef.current = 0;
    stalledMsRef.current = 0;
    stallStartedAtRef.current = null;
    watchMsRef.current = 0;
    watchStartedAtRef.current = null;
    autoplayBlockedCountRef.current = 0;
    lastPlaySucceededRef.current = false;
    sourceAttachedAtRef.current = null;
    loadedDataAtRef.current = null;
    canPlayAtRef.current = null;
    firstPlayingAtRef.current = null;
  };

  const emitBufferingChange = (nextIsBuffering: boolean) => {
    if (bufferingStateRef.current === nextIsBuffering) {
      return;
    }
    bufferingStateRef.current = nextIsBuffering;
    onBufferingChange?.(nextIsBuffering);
  };

  const clearPendingBufferingChange = () => {
    if (bufferingTimeoutRef.current == null) {
      return;
    }
    window.clearTimeout(bufferingTimeoutRef.current);
    bufferingTimeoutRef.current = null;
  };

  const scheduleBufferingResync = (delayMs: number) => {
    if (bufferingTimeoutRef.current != null) {
      return;
    }
    bufferingTimeoutRef.current = window.setTimeout(() => {
      bufferingTimeoutRef.current = null;
      const video = videoRef.current;
      if (!video) {
        return;
      }
      video.dispatchEvent(new Event('progress'));
    }, delayMs);
  };

  useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }

    const markPlaybackProgress = (force = false) => {
      const currentTime = Number.isFinite(video.currentTime) ? video.currentTime : 0;
      const previousTime = lastObservedCurrentTimeRef.current;
      lastObservedCurrentTimeRef.current = currentTime;
      if (force || previousTime == null || currentTime > previousTime + PLAYBACK_PROGRESS_EPSILON_SECONDS) {
        lastPlaybackProgressAtRef.current = nowMs();
      }
    };

    const syncBufferingState = () => {
      const lowReadiness =
        isActive &&
        !isPaused &&
        !video.paused &&
        !video.ended &&
        video.readyState < HTMLMediaElement.HAVE_FUTURE_DATA;
      if (!lowReadiness) {
        lowReadinessStartedAtRef.current = null;
      } else if (lowReadinessStartedAtRef.current == null) {
        lowReadinessStartedAtRef.current = nowMs();
      }

      const hasRecentPlaybackProgress =
        lastPlaybackProgressAtRef.current != null &&
        nowMs() - lastPlaybackProgressAtRef.current < RECENT_PLAYBACK_PROGRESS_MS;
      const lowReadinessDurationMs =
        lowReadinessStartedAtRef.current != null ? nowMs() - lowReadinessStartedAtRef.current : 0;
      const nextIsBuffering =
        lowReadiness &&
        !hasRecentPlaybackProgress &&
        lowReadinessDurationMs >= BUFFERING_SHOW_DELAY_MS;

      if (!nextIsBuffering) {
        if (!lowReadiness) {
          finalizeStall();
        }
        clearPendingBufferingChange();
        emitBufferingChange(false);
        if (lowReadiness) {
          if (hasRecentPlaybackProgress && lastPlaybackProgressAtRef.current != null) {
            const remainingGraceMs = Math.max(
              RECENT_PLAYBACK_PROGRESS_MS - (nowMs() - lastPlaybackProgressAtRef.current),
              0
            );
            scheduleBufferingResync(remainingGraceMs);
          } else {
            const remainingDelayMs = Math.max(BUFFERING_SHOW_DELAY_MS - lowReadinessDurationMs, 0);
            scheduleBufferingResync(remainingDelayMs);
          }
        }
        return;
      }

      clearPendingBufferingChange();
      emitBufferingChange(true);
    };

    const onPlaying = () => {
      if (startupStartedAtRef.current != null && startupMsRef.current == null) {
        startupMsRef.current = Math.round(Math.max(0, nowMs() - startupStartedAtRef.current));
      }
      finalizeStall();
      if (watchStartedAtRef.current == null) {
        watchStartedAtRef.current = nowMs();
      }
      if (firstPlayingAtRef.current == null) {
        firstPlayingAtRef.current = nowMs();
      }
      lastPlaySucceededRef.current = true;
      markPlaybackProgress(true);
      syncBufferingState();
      onPlaybackStarted?.();
    };

    const onLoadedData = () => {
      if (loadedDataAtRef.current == null) {
        loadedDataAtRef.current = nowMs();
      }
      syncBufferingState();
    };

    const onCanPlay = () => {
      if (canPlayAtRef.current == null) {
        canPlayAtRef.current = nowMs();
      }
      syncBufferingState();
    };

    const onWaiting = () => {
      finalizeWatch();
      if (stallStartedAtRef.current == null) {
        stallStartedAtRef.current = nowMs();
        stallCountRef.current += 1;
      }
      syncBufferingState();
    };

    const onPause = () => {
      finalizeWatch();
      syncBufferingState();
    };

    const onPlaybackProgress = () => {
      markPlaybackProgress();
      syncBufferingState();
    };

    const onBufferDataChange = () => {
      syncBufferingState();
    };

    video.addEventListener('loadeddata', onLoadedData);
    video.addEventListener('canplay', onCanPlay);
    video.addEventListener('canplaythrough', onBufferDataChange);
    video.addEventListener('playing', onPlaying);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('stalled', onWaiting);
    video.addEventListener('timeupdate', onPlaybackProgress);
    video.addEventListener('progress', onBufferDataChange);
    video.addEventListener('seeked', onPlaybackProgress);
    video.addEventListener('pause', onPause);
    video.addEventListener('ended', onPause);

    syncBufferingState();

    return () => {
      video.removeEventListener('loadeddata', onLoadedData);
      video.removeEventListener('canplay', onCanPlay);
      video.removeEventListener('canplaythrough', onBufferDataChange);
      video.removeEventListener('playing', onPlaying);
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('stalled', onWaiting);
      video.removeEventListener('timeupdate', onPlaybackProgress);
      video.removeEventListener('progress', onBufferDataChange);
      video.removeEventListener('seeked', onPlaybackProgress);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('ended', onPause);
      clearPendingBufferingChange();
      emitBufferingChange(false);
    };
  }, [isActive, isPaused, onBufferingChange, onPlaybackStarted]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }

    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }
    usingHlsJsRef.current = false;
    networkRecoveriesRef.current = 0;
    sourceAttachedAtRef.current = nowMs();
    loadedDataAtRef.current = null;
    canPlayAtRef.current = null;
    firstPlayingAtRef.current = null;
    lastPlaybackProgressAtRef.current = null;
    lastObservedCurrentTimeRef.current = null;

    const canPlayNativeHls = video.canPlayType('application/vnd.apple.mpegurl') !== '';
    if (isHlsStreamUrl(sourceUrl) && !canPlayNativeHls && Hls.isSupported()) {
      usingHlsJsRef.current = true;
      const hls = new Hls({
        maxBufferLength: 20,
        backBufferLength: 30,
      });
      hlsRef.current = hls;
      hls.attachMedia(video);
      hls.on(Hls.Events.MEDIA_ATTACHED, () => {
        hls.loadSource(sourceUrl);
      });
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) {
          return;
        }
        if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls.recoverMediaError();
          return;
        }
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR && networkRecoveriesRef.current < MAX_NETWORK_RECOVERY_RETRIES) {
          networkRecoveriesRef.current += 1;
          window.setTimeout(() => hls.startLoad(), NETWORK_RECOVERY_DELAY_MS);
          return;
        }
        hls.destroy();
        hlsRef.current = null;
      });
    } else {
      video.src = sourceUrl;
    }

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      usingHlsJsRef.current = false;
      video.pause();
      video.removeAttribute('src');
      video.load();
      clearPendingBufferingChange();
      emitBufferingChange(false);
    };
  }, [sourceUrl]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || isActive) {
      return;
    }
    if (usingHlsJsRef.current && hlsRef.current) {
      hlsRef.current.startLoad(-1);
      return;
    }
    video.load();
  }, [isActive, sourceUrl]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    if (!isActive || isPaused) {
      video.pause();
      clearPendingBufferingChange();
      emitBufferingChange(false);
      return;
    }
    if (!shouldAutoplay) {
      return;
    }

    if (startupStartedAtRef.current == null) {
      startupStartedAtRef.current = nowMs();
    }

    let cancelled = false;
    const play = async () => {
      try {
        video.muted = false;
        await video.play();
      } catch (error) {
        if (cancelled) {
          return;
        }
        if (isAutoplayBlockedError(error)) {
          autoplayBlockedCountRef.current += 1;
          onPlaybackBlocked?.();
        }
      }
    };

    void play();

    return () => {
      cancelled = true;
    };
  }, [isActive, isPaused, onPlaybackBlocked, shouldAutoplay]);

  useEffect(() => {
    if (isActive) {
      return;
    }
    emitMetrics();
  }, [isActive]);

  useEffect(() => {
    return () => {
      clearPendingBufferingChange();
      emitMetrics();
    };
    // Intentionally emit once when this player instance is removed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <video
      ref={videoRef}
      poster={showPoster ? poster ?? undefined : undefined}
      preload={preload}
      className={className}
      loop={loop}
      playsInline
      controls={controls}
    />
  );
}
