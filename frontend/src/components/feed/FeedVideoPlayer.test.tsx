import { fireEvent, render, waitFor } from '@testing-library/react';
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

const BUFFERING_SHOW_DELAY_MS = 180;
const RECENT_PLAYBACK_PROGRESS_MS = 400;

describe('FeedVideoPlayer', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const attachMediaState = (
    video: HTMLVideoElement,
    state: { paused: boolean; ended: boolean; readyState: number }
  ) => {
    Object.defineProperty(video, 'paused', {
      configurable: true,
      get: () => state.paused,
    });
    Object.defineProperty(video, 'ended', {
      configurable: true,
      get: () => state.ended,
    });
    Object.defineProperty(video, 'readyState', {
      configurable: true,
      get: () => state.readyState,
    });
  };

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

  it('does not mark buffering when waiting fires but the video is already playable', () => {
    const onBufferingChange = vi.fn();
    const mediaState = { paused: false, ended: false, readyState: HTMLMediaElement.HAVE_ENOUGH_DATA };
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);

    const { container, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    const video = container.querySelector('video') as HTMLVideoElement;
    attachMediaState(video, mediaState);
    onBufferingChange.mockClear();

    fireEvent(video, new Event('waiting'));

    expect(onBufferingChange).not.toHaveBeenCalledWith(true);
    expect(onBufferingChange).not.toHaveBeenCalledWith(false);
    unmount();
  });

  it('keeps buffering off when playback keeps advancing after waiting at low readyState', () => {
    vi.useFakeTimers();
    const onBufferingChange = vi.fn();
    const mediaState = { paused: false, ended: false, readyState: HTMLMediaElement.HAVE_CURRENT_DATA };
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);

    const { container, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    const video = container.querySelector('video') as HTMLVideoElement;
    attachMediaState(video, mediaState);
    onBufferingChange.mockClear();

    fireEvent(video, new Event('playing'));
    fireEvent(video, new Event('waiting'));

    mediaState.readyState = HTMLMediaElement.HAVE_CURRENT_DATA;
    video.currentTime = 3.5;
    fireEvent(video, new Event('timeupdate'));
    vi.advanceTimersByTime(250);
    video.currentTime = 3.85;
    fireEvent(video, new Event('timeupdate'));
    vi.advanceTimersByTime(250);
    video.currentTime = 4.2;
    fireEvent(video, new Event('timeupdate'));
    vi.advanceTimersByTime(150);

    expect(onBufferingChange).not.toHaveBeenCalledWith(true);
    expect(onBufferingChange).not.toHaveBeenCalledWith(false);
    unmount();
  });

  it('shows buffering only after playback stops advancing and clears once progress resumes', () => {
    vi.useFakeTimers();
    const onBufferingChange = vi.fn();
    const mediaState = { paused: false, ended: false, readyState: HTMLMediaElement.HAVE_CURRENT_DATA };
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);

    const { container, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    const video = container.querySelector('video') as HTMLVideoElement;
    attachMediaState(video, mediaState);
    onBufferingChange.mockClear();

    fireEvent(video, new Event('playing'));
    fireEvent(video, new Event('waiting'));
    vi.advanceTimersByTime(RECENT_PLAYBACK_PROGRESS_MS + BUFFERING_SHOW_DELAY_MS + 50);
    expect(onBufferingChange).toHaveBeenLastCalledWith(true);

    video.currentTime = 4.25;
    fireEvent(video, new Event('timeupdate'));

    expect(onBufferingChange).toHaveBeenLastCalledWith(false);
    unmount();
  });

  it('clears buffering when the player becomes paused or inactive', () => {
    vi.useFakeTimers();
    const onBufferingChange = vi.fn();
    const mediaState = { paused: false, ended: false, readyState: HTMLMediaElement.HAVE_CURRENT_DATA };
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);

    const { container, rerender, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    const video = container.querySelector('video') as HTMLVideoElement;
    attachMediaState(video, mediaState);
    onBufferingChange.mockClear();

    fireEvent(video, new Event('waiting'));
    vi.advanceTimersByTime(BUFFERING_SHOW_DELAY_MS + 50);
    expect(onBufferingChange).toHaveBeenLastCalledWith(true);

    mediaState.paused = true;
    rerender(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    expect(onBufferingChange).toHaveBeenLastCalledWith(false);
    unmount();
  });

  it('clears buffering when the source changes after a stall', () => {
    vi.useFakeTimers();
    const onBufferingChange = vi.fn();
    const mediaState = { paused: false, ended: false, readyState: HTMLMediaElement.HAVE_CURRENT_DATA };
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);

    const { container, rerender, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    const video = container.querySelector('video') as HTMLVideoElement;
    attachMediaState(video, mediaState);
    onBufferingChange.mockClear();

    fireEvent(video, new Event('waiting'));
    vi.advanceTimersByTime(BUFFERING_SHOW_DELAY_MS + 50);
    expect(onBufferingChange).toHaveBeenLastCalledWith(true);

    rerender(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/next-file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    expect(onBufferingChange).toHaveBeenLastCalledWith(false);
    unmount();
  });

  it('clears buffering when the player unmounts during a stall', () => {
    vi.useFakeTimers();
    const onBufferingChange = vi.fn();
    const mediaState = { paused: false, ended: false, readyState: HTMLMediaElement.HAVE_CURRENT_DATA };
    vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(window.HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);

    const { container, unmount } = render(
      <FeedVideoPlayer
        sourceUrl="https://cdn.example.com/video/file.mp4"
        isActive
        isPaused={false}
        shouldAutoplay={false}
        onBufferingChange={onBufferingChange}
      />
    );

    const video = container.querySelector('video') as HTMLVideoElement;
    attachMediaState(video, mediaState);
    onBufferingChange.mockClear();

    fireEvent(video, new Event('waiting'));
    vi.advanceTimersByTime(BUFFERING_SHOW_DELAY_MS + 50);
    expect(onBufferingChange).toHaveBeenLastCalledWith(true);

    unmount();

    expect(onBufferingChange).toHaveBeenLastCalledWith(false);
  });
});
