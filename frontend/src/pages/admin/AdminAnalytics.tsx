import React, { useEffect, useRef, useState } from "react";
import { MainLayout } from "@/components/layout/MainLayout";
import { UserDetailModal, UserSummary } from "@/components/ui/UserDetailModel";
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
  fetchAdminUserDetail,
  FlagByDate,
} from "@/lib/api";

// ── Types ─────────────────────────────────────────────────────────────────────
type TopItem = { name: string; count: number; id: string };
type AuditLog = { admin: string; action: string; targetId: number; time: string };

type ExportSection = {
  id: string;
  label: string;
  description: string;
};

// ── Constants ─────────────────────────────────────────────────────────────────
const actionMeta: Record<string, { color: string; bg: string; border: string }> = {
  DELETE_CONTENT:  { color: "text-red-400",    bg: "bg-red-400/10",     border: "border-red-400/30"     },
  BAN_USER:        { color: "text-orange-400", bg: "bg-orange-400/10",  border: "border-orange-400/30"  },
  WARN_USER:       { color: "text-yellow-400", bg: "bg-yellow-400/10",  border: "border-yellow-400/30"  },
  APPROVE_CONTENT: { color: "text-emerald-400",bg: "bg-emerald-400/10", border: "border-emerald-400/30" },
};

const EXPORT_SECTIONS: ExportSection[] = [
  { id: "flagTrend",  label: "Flag trend",          description: "Daily flag counts for selected month"   },
  { id: "topUsers",   label: "Top flagged users",    description: "Users with most flags this period"      },
  { id: "topContent", label: "Top flagged content",  description: "Content items with most flags"          },
  { id: "auditLogs",  label: "Audit logs",           description: "Admin actions and timestamps"           },
  { id: "kpi",        label: "KPI summary",          description: "Total flags, peak day, avg review time" },
];

// ── Utilities ─────────────────────────────────────────────────────────────────
function exportToCSV(filename: string, rows: Record<string, any>[]) {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const csv = [
    headers.join(","),
    ...rows.map((row) =>
      headers.map((h) => JSON.stringify(row[h] ?? "")).join(",")
    ),
  ].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

// ── Sub-components ────────────────────────────────────────────────────────────
const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-3 shadow-xl">
      <p className="text-xs text-slate-400 mb-1">Day {label}</p>
      <p className="text-lg font-bold text-indigo-500">{payload[0].value} flags</p>
    </div>
  );
};

const StatCard = ({
  label,
  value,
  accent,
}: {
  label: string;
  value: string | number;
  accent: string;
}) => (
  <div className="flex-1 min-w-[130px] rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800/60 px-6 py-5 shadow-sm">
    <p className="text-xs font-semibold uppercase tracking-widest text-slate-400 mb-2">{label}</p>
    <p className={`text-3xl font-extrabold ${accent}`}>{value}</p>
  </div>
);

const SectionHeading = ({ children }: { children: React.ReactNode }) => (
  <div className="flex items-center gap-3 mb-5">
    <span className="w-1 h-5 rounded-full bg-gradient-to-b from-indigo-400 to-indigo-600 shrink-0" />
    <h2 className="text-base font-bold tracking-tight text-slate-800 dark:text-slate-100">{children}</h2>
  </div>
);

const Panel = ({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) => (
  <div
    className={`rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800/60 shadow-sm ${className}`}
  >
    {children}
  </div>
);

// ── Export Modal ──────────────────────────────────────────────────────────────
const ExportModal = ({
  flagTrend,
  topUsers,
  topContent,
  auditLogs,
  totalFlags,
  maxFlags,
  avgReviewTime,
  monthYear,
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
  const [selected, setSelected] = useState<Set<string>>(
    new Set(EXPORT_SECTIONS.map((s) => s.id))
  );
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
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const allSelected = selected.size === EXPORT_SECTIONS.length;

  const handleExport = () => {
    const prefix = monthYear.replace(/\s/g, "_");

    if (selected.has("flagTrend"))
      exportToCSV(
        `${prefix}_flag_trend.csv`,
        flagTrend.map((d) => ({ day: d.day, flags: d.count }))
      );

    if (selected.has("topUsers"))
      exportToCSV(
        `${prefix}_top_flagged_users.csv`,
        topUsers.map((u) => ({ name: u.name, flag_count: u.count, user_id: u.id }))
      );

    if (selected.has("topContent"))
      exportToCSV(
        `${prefix}_top_flagged_content.csv`,
        topContent.map((c) => ({ title: c.name, flag_count: c.count, content_id: c.id }))
      );

    if (selected.has("auditLogs"))
      exportToCSV(
        `${prefix}_audit_logs.csv`,
        auditLogs.map((l) => ({
          admin: l.admin,
          action: l.action,
          target_id: l.targetId,
          time: l.time,
        }))
      );

    if (selected.has("kpi"))
      exportToCSV(`${prefix}_kpi_summary.csv`, [
        {
          period: monthYear,
          total_flags: totalFlags,
          peak_day_flags: maxFlags,
          avg_review_time_min: avgReviewTime,
        },
      ]);

    setOpen(false);
  };

  return (
    <div className="relative" ref={ref}>
      {/* Trigger button */}
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-200 text-sm font-medium px-4 py-2 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors shadow-sm"
      >
        <svg
          className="w-4 h-4"
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
        >
          <path d="M2 11v2a1 1 0 001 1h10a1 1 0 001-1v-2M8 2v8M5 7l3 3 3-3" />
        </svg>
        Export CSV
        <svg
          className={`w-3 h-3 transition-transform ${open ? "rotate-180" : ""}`}
          viewBox="0 0 10 6"
          fill="currentColor"
        >
          <path d="M0 0l5 6 5-6z" />
        </svg>
      </button>

      {/* Dropdown panel */}
      {open && (
        <div className="absolute right-0 top-[calc(100%+6px)] z-50 w-72 rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 shadow-xl p-4">
          <p className="text-xs font-semibold uppercase tracking-widest text-slate-400 mb-3">
            Choose sections to export
          </p>

          <button
            onClick={() =>
              setSelected(
                allSelected
                  ? new Set()
                  : new Set(EXPORT_SECTIONS.map((s) => s.id))
              )
            }
            className="text-xs text-indigo-500 hover:underline mb-3 block"
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
                  className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-colors ${
                    checked
                      ? "border-indigo-400/50 bg-indigo-50 dark:bg-indigo-900/20"
                      : "border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/40"
                  }`}
                >
                  {/* Checkbox */}
                  <div
                    className={`mt-0.5 w-4 h-4 rounded flex-shrink-0 flex items-center justify-center border transition-colors ${
                      checked
                        ? "bg-indigo-500 border-indigo-500"
                        : "border-slate-300 dark:border-slate-600"
                    }`}
                  >
                    {checked && (
                      <svg
                        className="w-2.5 h-2.5 text-white"
                        viewBox="0 0 9 9"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <polyline points="1.5,4.5 3.5,7 7.5,2" />
                      </svg>
                    )}
                  </div>

                  <div>
                    <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                      {section.label}
                    </p>
                    <p className="text-xs text-slate-400 mt-0.5">{section.description}</p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Footer */}
          <div className="flex gap-2">
            <button
              onClick={() => setOpen(false)}
              className="flex-1 text-sm text-slate-500 border border-slate-200 dark:border-slate-600 rounded-lg py-2 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleExport}
              disabled={selected.size === 0}
              className="flex-[2] flex items-center justify-center gap-1.5 text-sm font-semibold text-white bg-indigo-500 hover:bg-indigo-600 disabled:opacity-40 disabled:cursor-not-allowed rounded-lg py-2 transition-colors"
            >
              <svg
                className="w-3.5 h-3.5"
                viewBox="0 0 16 16"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
              >
                <path d="M2 11v2a1 1 0 001 1h10a1 1 0 001-1v-2M8 2v8M5 7l3 3 3-3" />
              </svg>
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
  const [flagTrend, setFlagTrend] = useState<{ day: string; count: number }[]>([]);
  const [topUsers, setTopUsers] = useState<TopItem[]>([]);
  const [topContent, setTopContent] = useState<TopItem[]>([]);
  const [avgReviewTime, setAvgReviewTime] = useState<number>(0);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [monthYear, setMonthYear] = useState<string>("");

  const [selectedUser, setSelectedUser] = useState<UserSummary | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isUserLoading, setIsUserLoading] = useState(false);

  const today = new Date();
  const maxYear = today.getFullYear();
  const maxMonth = today.getMonth();

  const [selectedMonth, setSelectedMonth] = useState(() => {
    const t = new Date();
    return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}`;
  });

  const getDaysInMonth = (year: number, month: number) =>
    new Array(new Date(year, month, 0).getDate()).fill(0).map((_, i) => i + 1);

  const formatFlagDataForMonth = (
    data: FlagByDate[],
    year: number,
    month: number
  ) => {
    const days = getDaysInMonth(year, month);
    const dataMap: Record<number, number> = {};
    data.forEach((item) => {
      dataMap[new Date(item.date).getDate()] = item.count;
    });
    return days.map((day) => ({ day: day.toString(), count: dataMap[day] || 0 }));
  };

  const handleOpenUserDetail = async (userId: string) => {
    setIsUserLoading(true);
    try {
      const profile = await fetchAdminUserDetail(userId);
      const summary = profile.summary;
      const mapped: UserSummary = {
        userId: summary.userId,
        displayName: summary.displayName ?? "Unknown",
        email: summary.email ?? "",
        status: summary.status ?? "active",
        roles: summary.roles ?? [],
        createdAt: summary.createdAt ?? "",
        lastSignInAt: summary.lastSignInAt ?? "",
        lastActivityDate: summary.lastActivityDate ?? "",
      };
      setSelectedUser(mapped);
      setIsModalOpen(true);
    } catch (err) {
      console.error("Failed to fetch user profile:", err);
    } finally {
      setIsUserLoading(false);
    }
  };

  useEffect(() => {
    const loadAnalytics = async () => {
      const [yearStr, monthStr] = selectedMonth.split("-");
      const year = Number(yearStr);
      const month = Number(monthStr);
      try {
        const flagData = await getFlaggedContentStats(monthStr, yearStr);
        setFlagTrend(formatFlagDataForMonth(flagData, year, month));

        const avgReview = await getAvgReviewTimeStats(monthStr, yearStr);
        setAvgReviewTime(avgReview.avgReviewTime);

        const topUsersData = await getTopFlagUsers(monthStr, yearStr);
        setTopUsers(
          topUsersData.map((u: any) => ({
            name: u.display_name || "Unknown",
            count: u.flag_count,
            id: u.user_id,
          }))
        );

        const topContentData = await getTopFlagContent(monthStr, yearStr);
        setTopContent(
          topContentData.map((c) => ({
            name: c.content_title || "Untitled",
            count: c.flag_count,
            id: c.content_id,
          }))
        );

        const logs = await getAuditLogs(monthStr, yearStr);
        setAuditLogs(
          logs.map((log: any) => ({
            admin: log.admin_name ?? log.profiles?.display_name ?? "Unknown",
            action: log.action_type || log.action || "UNKNOWN",
            targetId: log.target_id ?? 0,
            time: log.created_at
              ? new Date(log.created_at).toLocaleString("en-GB", {
                  day: "2-digit",
                  month: "2-digit",
                  year: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                  second: "2-digit",
                })
              : "",
          }))
        );

        setMonthYear(
          new Date(year, month - 1, 1).toLocaleDateString("en-GB", {
            month: "long",
            year: "numeric",
          })
        );
      } catch (err) {
        console.error("Failed to fetch analytics:", err);
      }
    };
    loadAnalytics();
  }, [selectedMonth]);

  const totalFlags = flagTrend.reduce((a, d) => a + d.count, 0);
  const maxFlags = Math.max(...flagTrend.map((d) => d.count), 0);

  const reviewAccent =
    avgReviewTime > 20
      ? "text-red-400"
      : avgReviewTime > 10
      ? "text-orange-400"
      : "text-emerald-400";
  const reviewStroke =
    avgReviewTime > 20 ? "#f87171" : avgReviewTime > 10 ? "#fb923c" : "#34d399";
  const reviewStatus =
    avgReviewTime > 20
      ? "⚠ High — needs attention"
      : avgReviewTime > 10
      ? "△ Moderate"
      : avgReviewTime > 0
      ? "✓ Within target"
      : "";

  const selectCls =
    "rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 " +
    "text-slate-700 dark:text-slate-200 text-sm px-3 py-2 outline-none cursor-pointer";

  return (
    <MainLayout>
      <div className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 p-8 space-y-8">

        {/* ── Header ─────────────────────────────────── */}
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_6px_#34d399]" />
              <span className="text-xs font-semibold uppercase tracking-widest text-emerald-400">
                Live Dashboard
              </span>
            </div>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 dark:text-white">
              Admin Analytics
            </h1>
          </div>

          {/* Right side: Period Selector + Export */}
          <div className="flex items-center gap-3 flex-wrap">
            {/* Period Selector */}
            <div className="flex items-center gap-2 flex-wrap bg-white dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 rounded-2xl px-4 py-3 shadow-sm">
              <span className="text-sm text-slate-400 mr-1">Period:</span>

              <select
                className={selectCls}
                value={Number(selectedMonth.split("-")[1]) - 1}
                onChange={(e) => {
                  const year = selectedMonth.split("-")[0];
                  const month = String(Number(e.target.value) + 1).padStart(2, "0");
                  setSelectedMonth(`${year}-${month}`);
                }}
              >
                {["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"].map(
                  (m, idx) => (
                    <option
                      key={idx}
                      value={idx}
                      disabled={
                        Number(selectedMonth.split("-")[0]) === maxYear && idx > maxMonth
                      }
                    >
                      {m}
                    </option>
                  )
                )}
              </select>

              <select
                className={selectCls}
                value={selectedMonth.split("-")[0]}
                onChange={(e) =>
                  setSelectedMonth(`${e.target.value}-${selectedMonth.split("-")[1]}`)
                }
              >
                {Array.from(
                  { length: new Date().getFullYear() - 2020 + 1 },
                  (_, i) => 2020 + i
                ).map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>

              <button
                onClick={() => {
                  const t = new Date();
                  setSelectedMonth(
                    `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}`
                  );
                }}
                className="bg-indigo-500 hover:bg-indigo-600 active:bg-indigo-700 text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
              >
                This Month
              </button>
            </div>

            {/* Export Button */}
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

        {/* ── KPI Stat Cards ──────────────────────────── */}
        <div className="flex gap-4 flex-wrap">
          <StatCard label="Total Flags"      value={totalFlags}                                        accent="text-indigo-500" />
          <StatCard label="Peak Day Flags"   value={maxFlags}                                          accent="text-pink-500"   />
          <StatCard label="Avg Review Time"  value={avgReviewTime > 0 ? `${avgReviewTime}m` : "N/A"}  accent={reviewAccent}    />
          <StatCard label="Audit Events"     value={auditLogs.length}                                  accent="text-sky-500"    />
        </div>

        {/* ── Charts ──────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

          {/* Flag Trend */}
          <Panel className="p-6">
            <SectionHeading>Flag Trend — {monthYear}</SectionHeading>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={flagTrend} margin={{ top: 4, right: 8, left: -10, bottom: 32 }}>
                <defs>
                  <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%"   stopColor="#818cf8" />
                    <stop offset="100%" stopColor="#4f46e5" />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} stroke="#e2e8f0" strokeDasharray="4 4" />
                <XAxis
                  dataKey="day"
                  tick={{ fill: "#94a3b8", fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  label={{
                    value: "Day",
                    position: "insideBottom",
                    offset: -18,
                    fill: "#94a3b8",
                    fontSize: 13,
                  }}
                />
                <YAxis
                  tick={{ fill: "#94a3b8", fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  allowDecimals={false}
                  label={{
                    value: "Flags",
                    angle: -90,
                    position: "insideLeft",
                    offset: 20,
                    style: { textAnchor: "middle", fill: "#9CA3AF", fontSize: 13 },
                  }}
                />
                <Tooltip
                  content={<CustomTooltip />}
                  cursor={{ fill: "rgba(99,102,241,0.07)" }}
                />
                <Bar dataKey="count" fill="url(#barGrad)" radius={[4, 4, 0, 0]} maxBarSize={18} />
              </BarChart>
            </ResponsiveContainer>
          </Panel>

          {/* Avg Review Time Ring */}
          <Panel className="p-6 flex flex-col items-center justify-center">
            <SectionHeading>Average Review Time</SectionHeading>
            <div className="relative flex items-center justify-center mt-2">
              <svg width="160" height="160">
                <circle
                  cx="80" cy="80" r="62"
                  fill="none" stroke="#e2e8f0" strokeWidth="10"
                  className="dark:[stroke:#334155]"
                />
                <circle
                  cx="80" cy="80" r="62"
                  fill="none"
                  stroke={reviewStroke} strokeWidth="10" strokeLinecap="round"
                  strokeDasharray={`${Math.min((avgReviewTime / 30) * 390, 390)} 390`}
                  transform="rotate(-90 80 80)"
                  style={{
                    filter: `drop-shadow(0 0 6px ${reviewStroke})`,
                    transition: "stroke-dasharray 0.8s ease",
                  }}
                />
              </svg>
              <div className="absolute text-center">
                <p className={`text-3xl font-extrabold ${reviewAccent}`}>
                  {avgReviewTime > 0 ? avgReviewTime : "—"}
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  {avgReviewTime > 0 ? "minutes" : "No data"}
                </p>
              </div>
            </div>
            {reviewStatus && (
              <p className={`mt-4 text-sm font-semibold ${reviewAccent}`}>{reviewStatus}</p>
            )}
          </Panel>
        </div>

        {/* ── Risk Analysis ───────────────────────────── */}
        <div>
          <SectionHeading>Risk Analysis</SectionHeading>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

            <Panel className="p-6">
              <p className="text-xs font-semibold uppercase tracking-widest text-slate-400 mb-4">
                Top Flagged Users
              </p>
              {topUsers.map((user, i) => (
                <div
                  key={i}
                  className={`flex items-center justify-between py-3 ${
                    i < topUsers.length - 1 ? "border-b border-slate-100 dark:border-slate-700" : ""
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-400 to-indigo-600 flex items-center justify-center text-white text-xs font-bold">
                      {user.name[0].toUpperCase()}
                    </div>
                    <button
                      className="text-sm text-slate-700 dark:text-slate-300 hover:underline"
                      onClick={() => handleOpenUserDetail(user.id!)}
                    >
                      {user.name}
                    </button>
                  </div>
                  <div className="flex items-center gap-3">
                    <div
                      className="h-1.5 rounded-full bg-gradient-to-r from-indigo-400 to-indigo-600"
                      style={{
                        width: `${(user.count / (topUsers[0]?.count || 1)) * 80}px`,
                      }}
                    />
                    <span className="text-sm font-bold text-indigo-500 w-6 text-right">
                      {user.count}
                    </span>
                  </div>
                </div>
              ))}
            </Panel>

            <Panel className="p-6">
              <p className="text-xs font-semibold uppercase tracking-widest text-slate-400 mb-4">
                Top Flagged Content
              </p>
              {topContent.map((c, i) => (
                <div
                  key={i}
                  className={`flex items-center justify-between py-3 ${
                    i < topContent.length - 1 ? "border-b border-slate-100 dark:border-slate-700" : ""
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-base">
                      📄
                    </div>
                    <span className="text-sm text-slate-700 dark:text-slate-300">{c.name}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <div
                      className="h-1.5 rounded-full bg-gradient-to-r from-pink-400 to-pink-600"
                      style={{
                        width: `${(c.count / (topContent[0]?.count || 1)) * 80}px`,
                      }}
                    />
                    <span className="text-sm font-bold text-pink-500 w-6 text-right">
                      {c.count}
                    </span>
                  </div>
                </div>
              ))}
            </Panel>
          </div>
        </div>

        {/* ── Audit Logs ──────────────────────────────── */}
        <div>
          <SectionHeading>Audit Logs</SectionHeading>
          <Panel className="overflow-hidden">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-slate-50 dark:bg-slate-800 border-b border-slate-100 dark:border-slate-700">
                  {["Admin", "Action", "Target ID", "Time"].map((h) => (
                    <th
                      key={h}
                      className="px-5 py-3 text-xs font-semibold uppercase tracking-widest text-slate-400"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {auditLogs.map((log, i) => {
                  const meta = actionMeta[log.action] ?? {
                    color: "text-slate-400",
                    bg: "bg-slate-100 dark:bg-slate-700",
                    border: "border-slate-300 dark:border-slate-600",
                  };
                  return (
                    <tr
                      key={i}
                      className="border-t border-slate-100 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800/80 transition-colors"
                    >
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-3">
                          <div className="w-7 h-7 rounded-full bg-gradient-to-br from-sky-400 to-sky-600 flex items-center justify-center text-white text-xs font-bold">
                            {log.admin[0]}
                          </div>
                          <span className="text-sm text-slate-700 dark:text-slate-300">
                            {log.admin}
                          </span>
                        </div>
                      </td>
                      <td className="px-5 py-3">
                        <span
                          className={`inline-block text-xs font-semibold tracking-wide border rounded-md px-2.5 py-1 ${meta.color} ${meta.bg} ${meta.border}`}
                        >
                          {log.action}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-sm text-slate-400">#{log.targetId}</td>
                      <td className="px-4 py-3 text-sm text-slate-400">{log.time}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Panel>
        </div>

      </div>

      <UserDetailModal
        isOpen={isModalOpen}
        user={selectedUser}
        isLoading={isUserLoading}
        onClose={() => setIsModalOpen(false)}
        onUpdateRole={(userId, role) => console.log("Change role", userId, role)}
        onToggleStatus={(user) => console.log("Toggle status", user)}
      />
    </MainLayout>
  );
};

export default AdminAnalytics;