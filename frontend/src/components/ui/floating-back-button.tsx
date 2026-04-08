import { ArrowLeft } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface FloatingBackButtonProps {
  onClick: () => void;
  label?: string;
  className?: string;
}

export function FloatingBackButton({
  onClick,
  label = "Back",
  className,
}: FloatingBackButtonProps) {
  return (
    <div className={cn("pointer-events-none absolute left-3 top-3 z-30", className)}>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={label}
        onClick={onClick}
        className="pointer-events-auto h-11 w-11 rounded-full border border-white/15 bg-black/35 text-white shadow-lg backdrop-blur-md hover:bg-white/10 hover:text-white"
      >
        <ArrowLeft className="h-5 w-5" />
      </Button>
    </div>
  );
}
