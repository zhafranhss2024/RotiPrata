import { render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FeedVideoPlayer, isHlsStreamUrl } from './FeedVideoPlayer';

vi.mock('hls.js', () => {
  class MockHls {
    static isSupported() {
      return false;
    }
  }
  return { default: MockHls };
});

describe('FeedVideoPlayer', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('detects HLS URLs', () => {
    expect(isHlsStreamUrl('https://cdn.example.com/video/master.m3u8')).toBe(true);
    expect(isHlsStreamUrl('https://cdn.example.com/video/file.mp4')).toBe(false);
    expect(isHlsStreamUrl(null)).toBe(false);
  });

  it('invokes blocked callback when unmuted autoplay is blocked', async () => {
    const onPlaybackBlocked = vi.fn();
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'play').mockRejectedValue(
      new DOMException('Autoplay blocked', 'NotAllowedError')
    );

    const { unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay
        onPlaybackBlocked={onPlaybackBlocked}
      />
    );

    await waitFor(() => {
      expect(onPlaybackBlocked).toHaveBeenCalledTimes(1);
    });
    unmount();
  });

  it('does not apply poster by default', () => {
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
    const { container, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        poster="https://cdn.example.com/poster.jpg"
        isActive={false}
        isPaused={false}
        shouldAutoplay={false}
      />
    );

    const video = container.querySelector('video');
    expect(video).not.toBeNull();
    expect(video?.getAttribute('poster')).toBeNull();
    unmount();
  });

  it('prewarms inactive videos by calling load', () => {
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    const loadSpy = vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
    const { unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive={false}
        isPaused={false}
        shouldAutoplay={false}
      />
    );

    expect(loadSpy).toHaveBeenCalled();
    unmount();
  });
});
