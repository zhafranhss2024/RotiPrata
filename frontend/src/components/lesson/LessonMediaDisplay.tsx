import { FeedVideoPlayer } from "@/components/feed/FeedVideoPlayer";
import { cn } from "@/lib/utils";

export type LessonMediaKind = "image" | "gif" | "video";

type Props = {
  mediaUrl: string | null | undefined;
  mediaKind?: LessonMediaKind | null;
  thumbnailUrl?: string | null;
  alt?: string | null;
  caption?: string | null;
  className?: string;
  mediaClassName?: string;
};

const VIDEO_EXTENSIONS = [".m3u8", ".mp4", ".webm", ".ogg", ".mov", ".m4v"];

export const inferLessonMediaKind = (
  mediaUrl: string | null | undefined,
  explicitKind?: LessonMediaKind | null
): LessonMediaKind | null => {
  if (explicitKind) {
    return explicitKind;
  }
  if (!mediaUrl) {
    return null;
  }
  const normalized = mediaUrl.toLowerCase().split("?")[0];
  if (VIDEO_EXTENSIONS.some((extension) => normalized.endsWith(extension))) {
    return "video";
  }
  if (normalized.endsWith(".gif")) {
    return "gif";
  }
  return "image";
};

export function LessonMediaDisplay({
  mediaUrl,
  mediaKind,
  thumbnailUrl,
  alt,
  caption,
  className,
  mediaClassName,
}: Props) {
  if (!mediaUrl) {
    return null;
  }

  const resolvedKind = inferLessonMediaKind(mediaUrl, mediaKind);
  if (!resolvedKind) {
    return null;
  }

  if (resolvedKind === "video") {
    return (
      <div className={cn("space-y-3", className)}>
        <FeedVideoPlayer
          sourceUrl={mediaUrl}
          poster={thumbnailUrl ?? null}
          showPoster
          className={cn(
            "mx-auto block h-auto max-h-[min(32rem,70vh)] w-full max-w-full rounded-2xl border bg-black object-contain",
            mediaClassName
          )}
          controls
          loop={false}
          isActive
          isPaused={false}
          shouldAutoplay={false}
        />
        {caption?.trim() ? <p className="text-sm leading-6 text-muted-foreground">{caption.trim()}</p> : null}
      </div>
    );
  }

  return (
    <figure className={cn("space-y-3", className)}>
      <img
        src={mediaUrl}
        alt={alt?.trim() || caption?.trim() || "Lesson media"}
        className={cn(
          "mx-auto block h-auto max-h-[min(32rem,70vh)] w-full max-w-full rounded-2xl border bg-black/5 object-contain object-center",
          mediaClassName
        )}
      />
      {caption?.trim() ? <figcaption className="text-sm leading-6 text-muted-foreground">{caption.trim()}</figcaption> : null}
    </figure>
  );
}
