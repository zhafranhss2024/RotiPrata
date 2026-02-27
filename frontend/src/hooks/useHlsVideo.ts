import { useEffect, useRef, useState } from 'react';

type HlsInstance = import('hls.js').default;

let hlsImportPromise: Promise<typeof import('hls.js')> | null = null;

const loadHls = () => {
  if (!hlsImportPromise) {
    hlsImportPromise = import('hls.js');
  }
  return hlsImportPromise;
};

export const isHlsUrl = (url?: string | null) => {
  if (!url) {
    return false;
  }
  return /\.m3u8($|[?#])/i.test(url);
};

type UseHlsVideoParams = {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  src?: string | null;
  enabled?: boolean;
  shouldLoad?: boolean;
  loadMode?: 'active' | 'prefetch' | 'none';
  onFatalError?: (error: unknown) => void;
};

export const useHlsVideo = ({
  videoRef,
  src,
  enabled = true,
  shouldLoad = true,
  loadMode,
  onFatalError,
}: UseHlsVideoParams) => {
  const hlsRef = useRef<HlsInstance | null>(null);
  const [readyKey, setReadyKey] = useState(0);
  const loadModeRef = useRef<'active' | 'prefetch' | 'none'>('none');
  const prefetchDoneRef = useRef(false);

  const resolvedLoadMode: 'active' | 'prefetch' | 'none' =
    loadMode ?? (shouldLoad ? 'active' : 'none');

  useEffect(() => {
    const video = videoRef.current;
    const shouldAttach = enabled && Boolean(src) && isHlsUrl(src);

    if (!video) {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      return;
    }

    if (!shouldAttach) {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      const existingSrc = video.getAttribute('src');
      if (existingSrc && isHlsUrl(existingSrc)) {
        video.removeAttribute('src');
        video.load();
      }
      return;
    }

    let cancelled = false;
    prefetchDoneRef.current = false;
    loadModeRef.current = resolvedLoadMode;

    const attach = async () => {
      try {
        const module = await loadHls();
        if (cancelled) {
          return;
        }
        const Hls = module.default;
        if (Hls.isSupported()) {
          if (hlsRef.current) {
            hlsRef.current.destroy();
            hlsRef.current = null;
          }
          const hls = new Hls({
            autoStartLoad: false,
            capLevelToPlayerSize: true,
            startLevel: -1,
            maxBufferLength: 10,
            maxMaxBufferLength: 20,
            backBufferLength: 30,
          });
          hlsRef.current = hls;
          hls.on(Hls.Events.FRAG_BUFFERED, () => {
            if (loadModeRef.current !== 'prefetch' || prefetchDoneRef.current) {
              return;
            }
            prefetchDoneRef.current = true;
            hls.stopLoad();
          });
          hls.on(Hls.Events.MEDIA_ATTACHED, () => {
            if (!cancelled) {
              hls.loadSource(src as string);
              setReadyKey((prev) => prev + 1);
              if (loadModeRef.current === 'active') {
                hls.startLoad();
              } else if (loadModeRef.current === 'prefetch') {
                hls.startLoad();
              } else {
                hls.stopLoad();
              }
            }
          });
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            if (!cancelled) {
              setReadyKey((prev) => prev + 1);
            }
          });
          hls.on(Hls.Events.ERROR, (_event, data) => {
            if (!data?.fatal) {
              return;
            }
            onFatalError?.(data);
            hls.destroy();
            hlsRef.current = null;
          });
          hls.attachMedia(video);
          return;
        }
      } catch (error) {
        onFatalError?.(error);
      }

      if (!cancelled && video.canPlayType('application/vnd.apple.mpegurl')) {
        if (loadModeRef.current === 'none') {
          const existingSrc = video.getAttribute('src');
          if (existingSrc && isHlsUrl(existingSrc)) {
            video.removeAttribute('src');
            video.load();
          }
          return;
        }
        const existingSrc = video.getAttribute('src');
        if (existingSrc !== src) {
          video.src = src as string;
          setReadyKey((prev) => prev + 1);
        }
        if (loadModeRef.current === 'prefetch') {
          video.load();
        }
      }
    };

    void attach();

    return () => {
      cancelled = true;
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [enabled, onFatalError, resolvedLoadMode, src, videoRef]);

  useEffect(() => {
    const hls = hlsRef.current;
    if (!hls) {
      return;
    }
    loadModeRef.current = resolvedLoadMode;
    if (resolvedLoadMode === 'active') {
      prefetchDoneRef.current = false;
      hls.startLoad();
      return;
    }
    if (resolvedLoadMode === 'prefetch') {
      if (prefetchDoneRef.current) {
        hls.stopLoad();
        return;
      }
      hls.startLoad();
    } else {
      hls.stopLoad();
    }
  }, [resolvedLoadMode]);

  return { readyKey };
};
