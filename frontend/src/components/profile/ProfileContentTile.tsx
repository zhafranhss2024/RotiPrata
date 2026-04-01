import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { Content } from "@/types";
import { FileText, ImageIcon, Play } from "lucide-react";

const typeLabelByContentType: Record<Content["content_type"], string> = {
  video: "Video",
  image: "Image",
  text: "Text",
};

const statusLabelByContentStatus: Record<Content["status"], string> = {
  approved: "Approved",
  pending: "Pending",
  rejected: "Rejected",
};

const statusClassByContentStatus: Record<Content["status"], string> = {
  approved: "bg-emerald-500/85 text-white border-0",
  pending: "bg-amber-500/85 text-black border-0",
  rejected: "bg-rose-500/85 text-white border-0",
};

const iconByContentType: Record<Content["content_type"], typeof Play> = {
  video: Play,
  image: ImageIcon,
  text: FileText,
};

type Props = {
  content: Content;
  showStatus?: boolean;
  onClick?: () => void;
};

export function ProfileContentTile({ content, showStatus = false, onClick }: Props) {
  const previewUrl = content.thumbnail_url ?? content.media_url ?? null;
  const TypeIcon = iconByContentType[content.content_type];

  return (
    <button
      type="button"
      onClick={onClick}
      className="group text-left rounded-[1.75rem] overflow-hidden border border-mainAlt/80 bg-main shadow-sm transition-transform hover:-translate-y-0.5"
    >
      <div className="relative aspect-[3/4] bg-mainDark">
        {previewUrl ? (
          <img src={previewUrl} alt={content.title} className="absolute inset-0 h-full w-full object-cover" />
        ) : (
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,#ff6b9833,transparent_55%),linear-gradient(180deg,#12121a,#09090f)]" />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/10 to-black/10" />
        <div className="absolute left-3 right-3 top-3 flex flex-wrap gap-2">
          <Badge variant="secondary" className="border-0 bg-black/50 text-white">
            <TypeIcon className="mr-1 h-3 w-3" />
            {typeLabelByContentType[content.content_type]}
          </Badge>
          {showStatus ? (
            <Badge className={cn("border-0", statusClassByContentStatus[content.status])}>
              {statusLabelByContentStatus[content.status]}
            </Badge>
          ) : null}
        </div>
        <div className="absolute inset-x-0 bottom-0 p-4">
          <h3 className="line-clamp-2 text-sm font-semibold text-white">{content.title}</h3>
          {content.description ? (
            <p className="mt-1 line-clamp-2 text-xs text-white/80">{content.description}</p>
          ) : null}
        </div>
      </div>
    </button>
  );
}
