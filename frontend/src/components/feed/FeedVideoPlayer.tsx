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

  useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }

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
      onBufferingChange?.(false);
      lastPlaySucceededRef.current = true;
      onPlaybackStarted?.();
    };

    const onLoadedData = () => {
      if (loadedDataAtRef.current == null) {
        loadedDataAtRef.current = nowMs();
      }
    };

    const onCanPlay = () => {
      if (canPlayAtRef.current == null) {
        canPlayAtRef.current = nowMs();
      }
    };

    const onWaiting = () => {
      finalizeWatch();
      if (stallStartedAtRef.current == null) {
        stallStartedAtRef.current = nowMs();
        stallCountRef.current += 1;
      }
      onBufferingChange?.(true);
    };

    const onPause = () => {
      finalizeWatch();
      onBufferingChange?.(false);
    };

    video.addEventListener('loadeddata', onLoadedData);
    video.addEventListener('canplay', onCanPlay);
    video.addEventListener('playing', onPlaying);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('stalled', onWaiting);
    video.addEventListener('pause', onPause);
    video.addEventListener('ended', onPause);

    return () => {
      video.removeEventListener('loadeddata', onLoadedData);
      video.removeEventListener('canplay', onCanPlay);
      video.removeEventListener('playing', onPlaying);
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('stalled', onWaiting);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('ended', onPause);
    };
  }, [onBufferingChange, onPlaybackStarted]);

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
      loop
      playsInline
      controls={false}
    />
  );
}
