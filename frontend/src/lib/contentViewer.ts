import type { Content } from "@/types";

export interface ContentViewerLocationState {
  queueContents?: Content[];
  initialIndex?: number;
  returnTo?: string;
  backLabel?: string;
}
