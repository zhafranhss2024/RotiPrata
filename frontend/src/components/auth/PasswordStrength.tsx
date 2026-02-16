import React from "react";
import { Check, X } from "lucide-react";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { getPasswordChecklist, getPasswordStrength } from "@/lib/passwordPolicy";

type Props = {
  password: string;
};

const strengthColor = (score: number) => {
  if (score <= 1) return "text-destructive";
  if (score === 2) return "text-muted-foreground";
  return "text-success";
};

export const PasswordStrength = ({ password }: Props) => {
  const checklist = getPasswordChecklist(password);
  const strength = getPasswordStrength(password);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>Password strength</span>
        <span className={cn("font-medium", strengthColor(strength.score))}>{strength.label}</span>
      </div>
      <Progress value={strength.percent} className="h-2" />
      <div className="grid gap-1 text-xs">
        {checklist.map(item => (
          <div
            key={item.key}
            className={cn(
              "flex items-center gap-2",
              item.met ? "text-success" : "text-muted-foreground"
            )}
          >
            {item.met ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
            <span>{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
