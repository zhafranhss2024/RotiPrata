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
  onFatalError?: (error: unknown) => void;
};

export const useHlsVideo = ({
  videoRef,
  src,
  enabled = true,
  onFatalError,
}: UseHlsVideoParams) => {
  const hlsRef = useRef<HlsInstance | null>(null);
  const [readyKey, setReadyKey] = useState(0);

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
          const hls = new Hls();
          hlsRef.current = hls;
          hls.on(Hls.Events.MEDIA_ATTACHED, () => {
            if (!cancelled) {
              hls.loadSource(src as string);
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
        const existingSrc = video.getAttribute('src');
        if (existingSrc !== src) {
          video.src = src as string;
          setReadyKey((prev) => prev + 1);
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
  }, [enabled, onFatalError, src, videoRef]);

  return { readyKey };
};
