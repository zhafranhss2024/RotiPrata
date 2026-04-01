import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { MainLayout } from "@/components/layout/MainLayout";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";

// Types
type FlagTrend = { day: string; count: number };
type TopItem = { name: string; count: number };
type AuditLog = { admin: string; action: string; targetId: number; time: string };
type FlagFromDB = { created_at: string };

const AdminAnalytics = () => {
  const [flagTrend, setFlagTrend] = useState<FlagTrend[]>([]);
  const [topUsers, setTopUsers] = useState<TopItem[]>([]);
  const [topContent, setTopContent] = useState<TopItem[]>([]);
  const [avgReviewTime, setAvgReviewTime] = useState<number>(0);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [monthYear, setMonthYear] = useState<string>("");

  const [selectedMonth, setSelectedMonth] = useState(() => {
    const today = new Date();
    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
  });

  // Mock flags from DB
  const fetchedFlags: FlagFromDB[] = [
    { created_at: "2026-03-01 10:00:00+00" },
    { created_at: "2026-03-01 12:30:00+00" },
    { created_at: "2026-03-03 14:00:00+00" },
    { created_at: "2026-03-05 09:00:00+00" },
    { created_at: "2026-03-05 18:00:00+00" },
    { created_at: "2026-03-25 12:23:04+00" },
  ];

  // Update flag trend when selected month changes
  useEffect(() => {
    const [yearStr, monthStr] = selectedMonth.split("-");
    const year = parseInt(yearStr, 10);
    const month = parseInt(monthStr, 10) - 1;

    const today = new Date();
    const lastDay =
      year === today.getFullYear() && month === today.getMonth()
        ? today.getDate()
        : new Date(year, month + 1, 0).getDate();

    const counts: Record<number, number> = {};
    fetchedFlags.forEach(f => {
      const d = new Date(f.created_at);
      if (d.getFullYear() === year && d.getMonth() === month && d.getDate() <= lastDay) {
        const day = d.getDate();
        counts[day] = (counts[day] || 0) + 1;
      }
    });

    const trend: FlagTrend[] = [];
    for (let d = 1; d <= lastDay; d++) {
      const dayLabel = new Date(year, month, d).toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
      });
      trend.push({ day: dayLabel, count: counts[d] || 0 });
    }
    setFlagTrend(trend);

    const monthYearStr = new Date(year, month, 1).toLocaleDateString("en-GB", {
      month: "long",
      year: "numeric",
    });
    setMonthYear(monthYearStr);
  }, [selectedMonth]);

  // Other mock data
  useEffect(() => {
    setTopUsers([
      { name: "user1", count: 12 },
      { name: "user2", count: 9 },
    ]);

    setTopContent([
      { name: "Post #123", count: 15 },
      { name: "Post #456", count: 11 },
    ]);

    setAvgReviewTime(4.5);

    setAuditLogs([
      { admin: "Admin1", action: "DELETE_CONTENT", targetId: 123, time: "10:00" },
      { admin: "Admin2", action: "BAN_USER", targetId: 45, time: "11:30" },
    ]);
  }, []);

  return (
    <MainLayout>
      <div className="p-6 space-y-8 bg-gray-50 min-h-screen">
        {/* SECTION: Overview */}
        <div>
          <h1 className="text-2xl font-bold mb-4">Analytics Overview</h1>

          {/* Month selector */}
            <div className="flex gap-4 items-center mb-6">
            <label className="font-semibold text-gray-700">Select Month:</label>

            {/* Month dropdown */}
            <select
                value={new Date(selectedMonth).getMonth()}
                onChange={e =>
                setSelectedMonth(
                    `${selectedMonth.split("-")[0]}-${String(Number(e.target.value) + 1).padStart(2, "0")}`
                )
                }
                className="border rounded-lg px-4 py-2 pr-8 bg-white text-gray-700 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-300"
            >
                {[
                "Jan","Feb","Mar","Apr","May","Jun",
                "Jul","Aug","Sep","Oct","Nov","Dec"
                ].map((m, idx) => (
                <option key={idx} value={idx}>{m}</option>
                ))}
            </select>

            {/* Year dropdown */}
            <select
                value={selectedMonth.split("-")[0]}
                onChange={e =>
                setSelectedMonth(`${e.target.value}-${selectedMonth.split("-")[1]}`)
                }
                className="border rounded-lg px-4 py-2 pr-8 bg-white text-gray-700 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-300"
            >
                {Array.from({ length: new Date().getFullYear() - 2020 + 1 }, (_, i) => 2020 + i)
                .map(y => <option key={y} value={y}>{y}</option>)
                }
            </select>

            {/* This Month button */}
            <button
                onClick={() => {
                const today = new Date();
                setSelectedMonth(`${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`);
                }}
                className="bg-indigo-500 text-white px-4 py-2 rounded-lg hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-indigo-300"
            >
                This Month
            </button>
            </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">
                  Flag Trend - {monthYear}
                </h2>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={flagTrend}>
                    <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                    <YAxis />
                    <Tooltip />
                    <Line type="monotone" dataKey="count" stroke="#4F46E5" />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card className="shadow-sm rounded-2xl flex items-center justify-center">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Average Review Time</h2>
                <p className="text-4xl font-bold">{avgReviewTime} mins</p>
              </CardContent>
            </Card>
          </div>
        </div>

        {/* SECTION: Risk Analysis */}
        <div>
          <h1 className="text-2xl font-bold mb-4">Risk Analysis</h1>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Top Flagged Users</h2>
                <ul>
                  {topUsers.map((user, index) => (
                    <li
                      key={index}
                      className="flex justify-between py-2 border-b last:border-none"
                    >
                      <span>{user.name}</span>
                      <span className="font-semibold">{user.count}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>

            <Card className="shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Top Flagged Content</h2>
                <ul>
                  {topContent.map((content, index) => (
                    <li
                      key={index}
                      className="flex justify-between py-2 border-b last:border-none"
                    >
                      <span>{content.name}</span>
                      <span className="font-semibold">{content.count}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </div>
        </div>

        {/* SECTION: Audit Logs */}
        <div>
          <h1 className="text-2xl font-bold mb-4">Audit Logs</h1>
          <Card className="shadow-sm rounded-2xl">
            <CardContent>
              <table className="w-full text-left">
                <thead>
                  <tr className="text-gray-500 text-sm">
                    <th className="pb-2">Admin</th>
                    <th className="pb-2">Action</th>
                    <th className="pb-2">Target</th>
                    <th className="pb-2">Time</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.map((log, index) => (
                    <tr key={index} className="border-t">
                      <td className="py-2">{log.admin}</td>
                      <td className="py-2">{log.action}</td>
                      <td className="py-2">{log.targetId}</td>
                      <td className="py-2">{log.time}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </div>
      </div>
    </MainLayout>
  );
};

export default AdminAnalytics;