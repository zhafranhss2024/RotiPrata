import { Badge } from "@/components/ui/badge";

interface CompactVideoTileProps {
  title: string;
  snippet?: string | null;
  thumbnailUrl?: string | null;
  mediaUrl?: string | null;
  badgeLabel?: string;
  onClick?: () => void;
}

export function CompactVideoTile({
  title,
  snippet,
  thumbnailUrl,
  mediaUrl,
  badgeLabel = "Video",
  onClick,
}: CompactVideoTileProps) {
  const previewUrl = thumbnailUrl ?? mediaUrl ?? null;

  return (
    <button type="button" className="group w-full max-w-none text-left sm:max-w-[9.5rem]" onClick={onClick}>
      <div className="relative aspect-[9/16] overflow-hidden rounded-2xl bg-mainDark border border-mainAlt/70">
        {previewUrl ? (
          <img
            src={previewUrl}
            alt={title}
            className="absolute inset-0 h-full w-full object-cover"
          />
        ) : (
          <div className="absolute inset-0 bg-mainDark" />
        )}
        <div className="absolute top-2 left-2">
          <Badge variant="secondary" className="bg-black/45 text-white border-0">
            {badgeLabel}
          </Badge>
        </div>
        <div className="absolute bottom-0 left-0 right-0 p-2.5">
          <h3 className="text-xs font-semibold text-white line-clamp-2 sm:text-sm">{title}</h3>
          {snippet ? (
            <p className="mt-1 text-[11px] text-white/75 line-clamp-2 sm:text-xs">{snippet}</p>
          ) : null}
        </div>
      </div>
    </button>
  );
}
