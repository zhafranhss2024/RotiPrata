import React, { useEffect, useMemo, useRef, useState } from "react";
import { MainLayout } from "@/components/layout/MainLayout";
import { AdminUserManagementDialog } from "@/components/admin/AdminUserManagementDialog";
import { useAuthContext } from "@/contexts/AuthContext";
import { useAdminUserManagement } from "@/hooks/useAdminUserManagement";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from "recharts";
import {
  getFlaggedContentStats,
  getAvgReviewTimeStats,
  getTopFlagUsers,
  getTopFlagContent,
  getAuditLogs,
  fetchAdminUsers,
  FlagByDate,
} from "@/lib/api";
import type { AuditLogItem, TopFlagUser } from "@/lib/api";
import type { AdminUserSummary } from "@/types";

// ── Types ─────────────────────────────────────────────────────────────────────
type TopItem = { name: string; count: number; id: string };
type AuditLog = { admin: string; action: string; targetId: string | number; time: string };
type ExportSection = { id: string; label: string; description: string };
type CSVRow = Record<string, string | number | boolean | null | undefined>;
type TooltipPayloadItem = { value: number };
type TooltipProps = { active?: boolean; payload?: TooltipPayloadItem[]; label?: string };
type AuditLogApiItem = AuditLogItem & {
  action_type?: string | null;
  profiles?: { display_name?: string | null } | null;
};

// ── Constants ─────────────────────────────────────────────────────────────────
const actionMeta: Record<string, { light: string; dark: string }> = {
  DELETE_CONTENT:  {
    light: "text-red-500 bg-red-50 border-red-200",
    dark:  "dark:text-red-400 dark:bg-red-400/10 dark:border-red-400/25",
  },
  BAN_USER: {
    light: "text-orange-500 bg-orange-50 border-orange-200",
    dark:  "dark:text-orange-400 dark:bg-orange-400/10 dark:border-orange-400/25",
  },
  WARN_USER: {
    light: "text-amber-600 bg-amber-50 border-amber-200",
    dark:  "dark:text-amber-400 dark:bg-amber-400/10 dark:border-amber-400/25",
  },
  APPROVE_CONTENT: {
    light: "text-emerald-600 bg-emerald-50 border-emerald-200",
    dark:  "dark:text-emerald-400 dark:bg-emerald-400/10 dark:border-emerald-400/25",
  },
};

const EXPORT_SECTIONS: ExportSection[] = [
  { id: "flagTrend",  label: "Flag trend",          description: "Daily flag counts for selected month"   },
  { id: "topUsers",   label: "Top flagged users",    description: "Users with most flags this period"      },
  { id: "topContent", label: "Top flagged content",  description: "Content items with most flags"          },
  { id: "auditLogs",  label: "Audit logs",           description: "Admin actions and timestamps"           },
  { id: "kpi",        label: "KPI summary",          description: "Total flags, peak day, avg review time" },
];

// ── Utilities ─────────────────────────────────────────────────────────────────
function exportToCSV(filename: string, rows: CSVRow[]) {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const csv = [
    headers.join(","),
    ...rows.map((row) => headers.map((h) => JSON.stringify(row[h] ?? "")).join(",")),
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function useDarkMode(): boolean {
  const [isDark, setIsDark] = useState(
    () => typeof document !== "undefined" && document.documentElement.classList.contains("dark")
  );
  useEffect(() => {
    const obs = new MutationObserver(() =>
      setIsDark(document.documentElement.classList.contains("dark"))
    );
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => obs.disconnect();
  }, []);
  return isDark;
}

// ── Sub-components ────────────────────────────────────────────────────────────

const CustomTooltip = ({ active, payload, label }: TooltipProps) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white dark:bg-[#1a1a1a] border border-gray-100 dark:border-white/10 rounded-2xl px-4 py-3 shadow-lg">
      <p className="text-xs text-gray-400 dark:text-neutral-500 mb-1">Day {label}</p>
      <p className="text-base font-bold text-gray-900 dark:text-white">{payload[0].value} flags</p>
    </div>
  );
};

const Pill = ({
  children,
  variant = "default",
}: {
  children: React.ReactNode;
  variant?: "default" | "active" | "warn";
}) => {
  const cls = {
    default: "bg-white dark:bg-white/5 border border-gray-200 dark:border-white/10 text-gray-600 dark:text-neutral-400",
    active:  "bg-[#ff385c] text-white border-transparent",
    warn:    "bg-amber-50 dark:bg-amber-400/10 border border-amber-200 dark:border-amber-400/25 text-amber-700 dark:text-amber-400",
  }[variant];
  return (
    <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-medium ${cls}`}>
      {children}
    </span>
  );
};

const StatCard = ({ label, value, sub }: { label: string; value: string | number; sub?: string }) => (
  <div className="flex-1 min-w-[140px] rounded-3xl border border-gray-100 dark:border-white/[0.06] bg-white dark:bg-[#1a1a1a] px-6 py-5 shadow-sm transition-colors duration-200">
    <p className="text-[11px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-3">{label}</p>
    <p className="text-3xl font-bold text-gray-900 dark:text-white tracking-tight">{value}</p>
    {sub && <p className="text-xs text-gray-400 dark:text-neutral-500 mt-1">{sub}</p>}
  </div>
);

const Card = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
  <div className={`rounded-3xl border border-gray-100 dark:border-white/[0.06] bg-white dark:bg-[#1a1a1a] shadow-sm transition-colors duration-200 ${className}`}>
    {children}
  </div>
);

const Divider = () => <div className="border-b border-gray-50 dark:border-white/[0.05]" />;

// ── Export Modal ──────────────────────────────────────────────────────────────
const ExportModal = ({
  flagTrend, topUsers, topContent, auditLogs,
  totalFlags, maxFlags, avgReviewTime, monthYear,
}: {
  flagTrend: { day: string; count: number }[];
  topUsers: TopItem[];
  topContent: TopItem[];
  auditLogs: AuditLog[];
  totalFlags: number;
  maxFlags: number;
  avgReviewTime: number;
  monthYear: string;
}) => {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set(EXPORT_SECTIONS.map((s) => s.id)));
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const toggle = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  const allSelected = selected.size === EXPORT_SECTIONS.length;

  const handleExport = () => {
    const prefix = monthYear.replace(/\s/g, "_");
    if (selected.has("flagTrend"))   exportToCSV(`${prefix}_flag_trend.csv`,         flagTrend.map((d) => ({ day: d.day, flags: d.count })));
    if (selected.has("topUsers"))    exportToCSV(`${prefix}_top_flagged_users.csv`,   topUsers.map((u) => ({ name: u.name, flag_count: u.count, user_id: u.id })));
    if (selected.has("topContent"))  exportToCSV(`${prefix}_top_flagged_content.csv`, topContent.map((c) => ({ title: c.name, flag_count: c.count, content_id: c.id })));
    if (selected.has("auditLogs"))   exportToCSV(`${prefix}_audit_logs.csv`,          auditLogs.map((l) => ({ admin: l.admin, action: l.action, target_id: l.targetId, time: l.time })));
    if (selected.has("kpi"))         exportToCSV(`${prefix}_kpi_summary.csv`,         [{ period: monthYear, total_flags: totalFlags, peak_day_flags: maxFlags, avg_review_time_min: avgReviewTime }]);
    setOpen(false);
  };

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-full border border-gray-200 dark:border-white/10 bg-white dark:bg-[#1a1a1a] text-gray-700 dark:text-neutral-300 text-sm font-medium px-5 py-2.5 hover:bg-gray-50 dark:hover:bg-white/[0.04] transition-colors shadow-sm"
      >
        <svg className="w-4 h-4" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
          <path d="M2 11v2a1 1 0 001 1h10a1 1 0 001-1v-2M8 2v8M5 7l3 3 3-3" />
        </svg>
        Export CSV
        <svg className={`w-3 h-3 transition-transform ${open ? "rotate-180" : ""}`} viewBox="0 0 10 6" fill="currentColor">
          <path d="M0 0l5 6 5-6z" />
        </svg>
      </button>

      {open && (
        <div className="absolute right-0 top-[calc(100%+8px)] z-50 w-72 rounded-3xl border border-gray-100 dark:border-white/10 bg-white dark:bg-[#1a1a1a] shadow-2xl dark:shadow-black/50 p-5">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-3">
            Choose sections to export
          </p>
          <button
            onClick={() => setSelected(allSelected ? new Set() : new Set(EXPORT_SECTIONS.map((s) => s.id)))}
            className="text-xs text-[#ff385c] hover:underline mb-3 block font-medium"
          >
            {allSelected ? "Deselect all" : "Select all"}
          </button>

          <div className="space-y-2 mb-4">
            {EXPORT_SECTIONS.map((section) => {
              const checked = selected.has(section.id);
              return (
                <div
                  key={section.id}
                  onClick={() => toggle(section.id)}
                  className={`flex items-start gap-3 p-3 rounded-2xl border cursor-pointer transition-colors ${
                    checked
                      ? "border-[#ff385c]/30 bg-[#ff385c]/5 dark:bg-[#ff385c]/10"
                      : "border-gray-100 dark:border-white/[0.06] hover:bg-gray-50 dark:hover:bg-white/[0.03]"
                  }`}
                >
                  <div
                    className={`mt-0.5 w-4 h-4 rounded flex-shrink-0 flex items-center justify-center border transition-colors ${
                      checked ? "bg-[#ff385c] border-[#ff385c]" : "border-gray-300 dark:border-white/20"
                    }`}
                  >
                    {checked && (
                      <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 9 9" fill="none" stroke="currentColor" strokeWidth="2">
                        <polyline points="1.5,4.5 3.5,7 7.5,2" />
                      </svg>
                    )}
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-800 dark:text-neutral-100">{section.label}</p>
                    <p className="text-xs text-gray-400 dark:text-neutral-500 mt-0.5">{section.description}</p>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="flex gap-2">
            <button
              onClick={() => setOpen(false)}
              className="flex-1 text-sm text-gray-500 dark:text-neutral-400 border border-gray-200 dark:border-white/10 rounded-full py-2.5 hover:bg-gray-50 dark:hover:bg-white/[0.04] transition-colors font-medium"
            >
              Cancel
            </button>
            <button
              onClick={handleExport}
              disabled={selected.size === 0}
              className="flex-[2] flex items-center justify-center gap-1.5 text-sm font-semibold text-white bg-[#ff385c] hover:bg-[#e0304f] disabled:opacity-40 disabled:cursor-not-allowed rounded-full py-2.5 transition-colors"
            >
              Export ({selected.size})
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

// ── Main Component ────────────────────────────────────────────────────────────
const AdminAnalytics = () => {
  const isDark = useDarkMode();

  const [flagTrend, setFlagTrend]           = useState<{ day: string; count: number }[]>([]);
  const [topUsers, setTopUsers]             = useState<TopItem[]>([]);
  const [topContent, setTopContent]         = useState<TopItem[]>([]);
  const [avgReviewTime, setAvgReviewTime]   = useState<number>(0);
  const [auditLogs, setAuditLogs]           = useState<AuditLog[]>([]);
  const [monthYear, setMonthYear]           = useState<string>("");

  const { user: currentUser } = useAuthContext();
  const currentAdminUserId = currentUser?.user_id ?? null;
  const [adminUsers, setAdminUsers]         = useState<AdminUserSummary[]>([]);
  const adminCount = useMemo(
    () => adminUsers.filter((user) => user.roles.includes("admin")).length,
    [adminUsers]
  );

  const upsertUserSummary = (summary: AdminUserSummary) => {
    setAdminUsers((users) => {
      const existingIndex = users.findIndex((user) => user.userId === summary.userId);
      if (existingIndex === -1) {
        return [...users, summary];
      }
      return users.map((user) => (user.userId === summary.userId ? summary : user));
    });
  };

  const {
    selectedUser,
    isOpen,
    isLoading,
    userActionKey,
    userContentRejectTarget,
    userContentRejectReason,
    userContentRejectAttempted,
    openUser,
    closeUser,
    updateRole,
    toggleStatus,
    resetLessonProgress,
    deleteComment,
    startTakeDownContent,
    cancelTakeDownContent,
    setTakeDownReason,
    confirmTakeDownContent,
  } = useAdminUserManagement({
    currentAdminUserId,
    adminCount,
    findUserSummary: (userId) => adminUsers.find((user) => user.userId === userId) ?? null,
    onUserSummaryUpdated: upsertUserSummary,
  });

  const today    = new Date();
  const maxYear  = today.getFullYear();
  const maxMonth = today.getMonth();

  const [selectedMonth, setSelectedMonth] = useState(() => {
    const t = new Date();
    return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}`;
  });

  const getDaysInMonth = (year: number, month: number) =>
    new Array(new Date(year, month, 0).getDate()).fill(0).map((_, i) => i + 1);

  const formatFlagDataForMonth = (data: FlagByDate[], year: number, month: number) => {
    const days = getDaysInMonth(year, month);
    const map: Record<number, number> = {};
    data.forEach((item) => { map[new Date(item.date).getDate()] = item.count; });
    return days.map((day) => ({ day: day.toString(), count: map[day] || 0 }));
  };

  useEffect(() => {
    fetchAdminUsers()
      .then(setAdminUsers)
      .catch((error) => console.warn("Failed to load admin users", error));
  }, []);

  useEffect(() => {
    const load = async () => {
      const [yearStr, monthStr] = selectedMonth.split("-");
      const year = Number(yearStr), month = Number(monthStr);
      try {
        const flagData = await getFlaggedContentStats(monthStr, yearStr);
        setFlagTrend(formatFlagDataForMonth(flagData, year, month));

        const avgReview = await getAvgReviewTimeStats(monthStr, yearStr);
        setAvgReviewTime(avgReview.avgReviewTime);

        const topUsersData = await getTopFlagUsers(monthStr, yearStr);
        setTopUsers(
          topUsersData.map((user: TopFlagUser) => ({
            name: user.display_name || "Unknown",
            count: user.flag_count,
            id: user.user_id,
          }))
        );

        const topContentData = await getTopFlagContent(monthStr, yearStr);
        setTopContent(topContentData.map((c) => ({ name: c.content_title || "Untitled", count: c.flag_count, id: c.content_id })));

        const logs = await getAuditLogs(monthStr, yearStr);
        setAuditLogs(logs.map((log: AuditLogApiItem) => ({
          admin: log.admin_name ?? log.profiles?.display_name ?? "Unknown",
          action: log.action_type || log.action || "UNKNOWN",
          targetId: log.target_id ?? 0,
          time: log.created_at
            ? new Date(log.created_at).toLocaleString("en-GB", {
                day: "2-digit", month: "2-digit", year: "numeric",
                hour: "2-digit", minute: "2-digit", second: "2-digit",
              })
            : "",
        })));

        setMonthYear(
          new Date(year, month - 1, 1).toLocaleDateString("en-GB", { month: "long", year: "numeric" })
        );
      } catch (err) {
        console.error("Failed to fetch analytics:", err);
      }
    };
    load();
  }, [selectedMonth]);

  const totalFlags = flagTrend.reduce((a, d) => a + d.count, 0);
  const maxFlags   = Math.max(...flagTrend.map((d) => d.count), 0);

  const reviewAccent =
    avgReviewTime > 20
      ? "text-red-500 dark:text-red-400"
      : avgReviewTime > 10
      ? "text-amber-500 dark:text-amber-400"
      : "text-emerald-500 dark:text-emerald-400";

  const reviewStroke = avgReviewTime > 20 ? "#ef4444" : avgReviewTime > 10 ? "#f59e0b" : "#10b981";

  const ringTrack = isDark ? "#2a2a2a" : "#f3f4f6";

  const reviewStatus =
    avgReviewTime > 20
      ? "⚠ High — needs attention"
      : avgReviewTime > 10
      ? "△ Moderate"
      : avgReviewTime > 0
      ? "✓ Within target"
      : "";

  const gridColor   = isDark ? "rgba(255,255,255,0.05)" : "#f3f4f6";
  const tickColor   = isDark ? "#555555" : "#9ca3af";
  const cursorColor = isDark ? "rgba(255,255,255,0.04)" : "rgba(0,0,0,0.04)";

  const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

  const selectCls =
    "rounded-full border border-gray-200 dark:border-white/10 bg-white dark:bg-[#111] " +
    "text-gray-700 dark:text-neutral-300 text-sm px-4 py-2 outline-none cursor-pointer " +
    "focus:ring-2 focus:ring-[#ff385c]/30 transition-colors duration-200";

  return (
    <MainLayout>
      <div
        className="min-h-screen bg-gray-50 dark:bg-[#111111] text-gray-900 dark:text-white p-6 md:p-10 space-y-5 transition-colors duration-200"
        style={{ fontFamily: "'Geist', 'DM Sans', system-ui, sans-serif" }}
      >

        {/* ── Header ─────────────────────────────────────── */}
        <Card className="p-6">
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-1">
                ANALYTICS
              </p>
              <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
                Admin Analytics
              </h1>
              <p className="text-sm text-gray-400 dark:text-neutral-500 mt-1">
                Review flagged content, risk signals, and audit trails.
              </p>
            </div>

            <div className="flex items-center gap-3 flex-wrap">
              {/* Period selector */}
              <div className="flex items-center gap-2 border border-gray-200 dark:border-white/10 rounded-full px-4 py-2 bg-white dark:bg-[#1a1a1a] shadow-sm transition-colors duration-200">
                <span className="text-xs text-gray-400 dark:text-neutral-500 font-medium">Period:</span>

                <select
                  className={selectCls}
                  value={Number(selectedMonth.split("-")[1]) - 1}
                  onChange={(e) => {
                    const year  = selectedMonth.split("-")[0];
                    const month = String(Number(e.target.value) + 1).padStart(2, "0");
                    setSelectedMonth(`${year}-${month}`);
                  }}
                >
                  {MONTHS.map((m, idx) => (
                    <option
                      key={idx}
                      value={idx}
                      disabled={Number(selectedMonth.split("-")[0]) === maxYear && idx > maxMonth}
                    >
                      {m}
                    </option>
                  ))}
                </select>

                <select
                  className={selectCls}
                  value={selectedMonth.split("-")[0]}
                  onChange={(e) => setSelectedMonth(`${e.target.value}-${selectedMonth.split("-")[1]}`)}
                >
                  {Array.from({ length: new Date().getFullYear() - 2020 + 1 }, (_, i) => 2020 + i).map((y) => (
                    <option key={y} value={y}>{y}</option>
                  ))}
                </select>

                <button
                  onClick={() => {
                    const t = new Date();
                    setSelectedMonth(`${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}`);
                  }}
                  className="bg-[#ff385c] hover:bg-[#e0304f] text-white text-xs font-semibold px-4 py-2 rounded-full transition-colors"
                >
                  This Month
                </button>
              </div>

              <ExportModal
                flagTrend={flagTrend}
                topUsers={topUsers}
                topContent={topContent}
                auditLogs={auditLogs}
                totalFlags={totalFlags}
                maxFlags={maxFlags}
                avgReviewTime={avgReviewTime}
                monthYear={monthYear}
              />
            </div>
          </div>
        </Card>

        {/* ── KPI Stat Cards ───────────────────────────────── */}
        <div className="flex gap-4 flex-wrap">
          <StatCard label="Total Flags"     value={totalFlags}                                       sub={monthYear} />
          <StatCard label="Peak Day"        value={maxFlags}                                         sub="flags in a single day" />
          <StatCard label="Avg Review Time" value={avgReviewTime > 0 ? `${avgReviewTime}m` : "N/A"} sub={reviewStatus || undefined} />
          <StatCard label="Audit Events"    value={auditLogs.length}                                 sub="logged this period" />
        </div>

        {/* ── Charts ──────────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">

          {/* Flag Trend */}
          <Card className="p-6">
            <div className="mb-5">
              <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-1">FLAG TREND</p>
              <h2 className="text-lg font-bold text-gray-900 dark:text-white">{monthYear}</h2>
            </div>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={flagTrend} margin={{ top: 4, right: 4, left: -18, bottom: 28 }}>
                <CartesianGrid vertical={false} stroke={gridColor} />
                <XAxis
                  dataKey="day"
                  tick={{ fill: tickColor, fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  label={{ value: "Day", position: "insideBottom", offset: -16, fill: tickColor, fontSize: 12 }}
                />
                <YAxis
                  tick={{ fill: tickColor, fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  allowDecimals={false}
                />
                <Tooltip content={<CustomTooltip />} cursor={{ fill: cursorColor, radius: 4 }} />
                <Bar dataKey="count" fill="#ff385c" radius={[4, 4, 0, 0]} maxBarSize={16} />
              </BarChart>
            </ResponsiveContainer>
          </Card>

          {/* Avg Review Time Ring */}
          <Card className="p-6 flex flex-col items-center justify-center">
            <div className="w-full mb-4">
              <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-1">AVERAGE REVIEW TIME</p>
              <h2 className="text-lg font-bold text-gray-900 dark:text-white">Response Speed</h2>
            </div>
            <div className="relative flex items-center justify-center">
              <svg width="148" height="148">
                <circle cx="74" cy="74" r="58" fill="none" stroke={ringTrack} strokeWidth="10" />
                <circle
                  cx="74" cy="74" r="58"
                  fill="none"
                  stroke={reviewStroke}
                  strokeWidth="10"
                  strokeLinecap="round"
                  strokeDasharray={`${Math.min((avgReviewTime / 30) * 365, 365)} 365`}
                  transform="rotate(-90 74 74)"
                  style={{ transition: "stroke-dasharray 0.8s ease" }}
                />
              </svg>
              <div className="absolute text-center">
                <p className={`text-3xl font-bold ${reviewAccent}`}>
                  {avgReviewTime > 0 ? avgReviewTime : "—"}
                </p>
                <p className="text-xs text-gray-400 dark:text-neutral-500 mt-1">
                  {avgReviewTime > 0 ? "minutes" : "No data"}
                </p>
              </div>
            </div>
            {reviewStatus && (
              <div className="mt-4">
                <Pill variant={avgReviewTime > 20 ? "warn" : "default"}>{reviewStatus}</Pill>
              </div>
            )}
          </Card>
        </div>

        {/* ── Risk Analysis ────────────────────────────────── */}
        <Card className="p-6">
          <div className="mb-5">
            <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-1">RISK ANALYSIS</p>
            <h2 className="text-lg font-bold text-gray-900 dark:text-white">Flagged Entities</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

            {/* Top Flagged Users */}
            <div>
              <p className="text-xs font-semibold text-gray-400 dark:text-neutral-500 mb-4 uppercase tracking-widest">
                Top Flagged Users
              </p>
              <div className="space-y-1">
                {topUsers.map((user, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between py-3 border-b border-gray-50 dark:border-white/[0.04] last:border-0"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-[#ff385c]/10 dark:bg-[#ff385c]/15 flex items-center justify-center text-[#ff385c] text-xs font-bold">
                        {user.name[0].toUpperCase()}
                      </div>
                      <button
                        className="text-sm font-medium text-gray-800 dark:text-neutral-200 hover:text-[#ff385c] dark:hover:text-[#ff385c] transition-colors"
                        onClick={() => {
                          void openUser(user.id);
                        }}
                      >
                        {user.name}
                      </button>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="h-1.5 rounded-full bg-[#ff385c]/15 dark:bg-[#ff385c]/20 overflow-hidden" style={{ width: 80 }}>
                        <div
                          className="h-full rounded-full bg-[#ff385c]"
                          style={{ width: `${(user.count / (topUsers[0]?.count || 1)) * 100}%`, transition: "width 0.6s ease" }}
                        />
                      </div>
                      <span className="text-sm font-bold text-gray-700 dark:text-neutral-300 w-5 text-right">
                        {user.count}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Top Flagged Content */}
            <div>
              <p className="text-xs font-semibold text-gray-400 dark:text-neutral-500 mb-4 uppercase tracking-widest">
                Top Flagged Content
              </p>
              <div className="space-y-1">
                {topContent.map((c, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between py-3 border-b border-gray-50 dark:border-white/[0.04] last:border-0"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-xl bg-gray-100 dark:bg-white/[0.06] flex items-center justify-center text-sm">
                        📄
                      </div>
                      <span className="text-sm font-medium text-gray-800 dark:text-neutral-200 max-w-[160px] truncate">
                        {c.name}
                      </span>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="h-1.5 rounded-full bg-gray-100 dark:bg-white/[0.08] overflow-hidden" style={{ width: 80 }}>
                        <div
                          className="h-full rounded-full bg-gray-400 dark:bg-neutral-500"
                          style={{ width: `${(c.count / (topContent[0]?.count || 1)) * 100}%`, transition: "width 0.6s ease" }}
                        />
                      </div>
                      <span className="text-sm font-bold text-gray-700 dark:text-neutral-300 w-5 text-right">
                        {c.count}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </Card>

        {/* ── Audit Logs ───────────────────────────────────── */}
        <Card className="overflow-hidden">
          <div className="px-6 py-5">
            <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500 mb-1">AUDIT LOGS</p>
            <h2 className="text-lg font-bold text-gray-900 dark:text-white">Admin Actions</h2>
          </div>
          <Divider />
          <table className="w-full text-left">
            <thead>
              <tr>
                {["Admin", "Action", "Target ID", "Time"].map((h) => (
                  <th key={h} className="px-6 py-3 text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-neutral-500">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {auditLogs.map((log, i) => {
                const meta = actionMeta[log.action] ?? {
                  light: "text-gray-500 bg-gray-50 border-gray-200",
                  dark:  "dark:text-neutral-400 dark:bg-white/[0.05] dark:border-white/10",
                };
                return (
                  <tr
                    key={i}
                    className="border-t border-gray-50 dark:border-white/[0.04] hover:bg-gray-50/80 dark:hover:bg-white/[0.02] transition-colors"
                  >
                    <td className="px-6 py-3.5">
                      <div className="flex items-center gap-3">
                        <div className="w-7 h-7 rounded-full bg-gray-100 dark:bg-white/[0.07] flex items-center justify-center text-gray-600 dark:text-neutral-400 text-xs font-bold">
                          {log.admin[0]}
                        </div>
                        <span className="text-sm font-medium text-gray-700 dark:text-neutral-300">{log.admin}</span>
                      </div>
                    </td>
                    <td className="px-6 py-3.5">
                      <span className={`inline-block text-[11px] font-semibold tracking-wide border rounded-full px-3 py-1 ${meta.light} ${meta.dark}`}>
                        {log.action}
                      </span>
                    </td>
                    <td className="px-6 py-3.5 text-sm text-gray-400 dark:text-neutral-500">#{log.targetId}</td>
                    <td className="px-6 py-3.5 text-sm text-gray-400 dark:text-neutral-500">{log.time}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>

      </div>

      <AdminUserManagementDialog
        isOpen={isOpen}
        user={selectedUser}
        isLoading={isLoading}
        userActionKey={userActionKey}
        currentAdminUserId={currentAdminUserId}
        adminCount={adminCount}
        userContentRejectTarget={userContentRejectTarget}
        userContentRejectReason={userContentRejectReason}
        userContentRejectAttempted={userContentRejectAttempted}
        onClose={closeUser}
        onToggleRole={updateRole}
        onToggleStatus={toggleStatus}
        onResetLessonProgress={resetLessonProgress}
        onDeleteComment={deleteComment}
        onStartTakeDownContent={startTakeDownContent}
        onTakeDownReasonChange={setTakeDownReason}
        onCancelTakeDownContent={cancelTakeDownContent}
        onConfirmTakeDownContent={confirmTakeDownContent}
      />
    </MainLayout>
  );
};

export default AdminAnalytics;
