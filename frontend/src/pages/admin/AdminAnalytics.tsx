import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { MainLayout } from "@/components/layout/MainLayout";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";
import { getFlaggedContentStats, getAvgReviewTimeStats, FlagByDate } from "@/lib/api";

// Types
type TopItem = { name: string; count: number };
type AuditLog = { admin: string; action: string; targetId: number; time: string };

const AdminAnalytics = () => {
  const [flagTrend, setFlagTrend] = useState<{ day: string; count: number }[]>([]);
  const [topUsers, setTopUsers] = useState<TopItem[]>([]);
  const [topContent, setTopContent] = useState<TopItem[]>([]);
  const [avgReviewTime, setAvgReviewTime] = useState<number>(0);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [monthYear, setMonthYear] = useState<string>("");

  const today = new Date();
  const maxYear = today.getFullYear();
  const maxMonth = today.getMonth();

  const [selectedMonth, setSelectedMonth] = useState(() => {
    const today = new Date();
    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
  });

  const getDaysInMonth = (year: number, month: number) => {
    return new Array(new Date(year, month, 0).getDate()).fill(0).map((_, i) => i + 1);
  };

  const formatFlagDataForMonth = (data: FlagByDate[], year: number, month: number) => {
    const days = getDaysInMonth(year, month);
    const dataMap: Record<number, number> = {};

    data.forEach((item) => {
      const day = new Date(item.date).getDate();
      dataMap[day] = item.count;
    });

    return days.map((day) => ({
      day: day.toString(),
      count: dataMap[day] || 0,
    }));
  };

  useEffect(() => {
    const loadAnalytics = async () => {
      const [yearStr, monthStr] = selectedMonth.split("-");
      const year = Number(yearStr);
      const month = Number(monthStr);

      try {
        // Fetch flagged content for the chart
        const flagData = await getFlaggedContentStats(monthStr, yearStr);
        const formattedFlagData = formatFlagDataForMonth(flagData, year, month);
        setFlagTrend(formattedFlagData);

        // Fetch average review time
        const avgReview = await getAvgReviewTimeStats(monthStr, yearStr);
        setAvgReviewTime(avgReview.avgReviewTime);

        // Set formatted month/year for display
        const monthYearStr = new Date(year, month - 1, 1).toLocaleDateString("en-GB", {
          month: "long",
          year: "numeric",
        });
        setMonthYear(monthYearStr);

      } catch (err) {
        console.error("Failed to fetch analytics:", err);
      }
    };

    loadAnalytics();
  }, [selectedMonth]);

  useEffect(() => {
    setTopUsers([
      { name: "user1", count: 12 },
      { name: "user2", count: 9 },
    ]);

    setTopContent([
      { name: "Post #123", count: 15 },
      { name: "Post #456", count: 11 },
    ]);

    setAuditLogs([
      { admin: "Admin1", action: "DELETE_CONTENT", targetId: 123, time: "10:00" },
      { admin: "Admin2", action: "BAN_USER", targetId: 45, time: "11:30" },
    ]);
  }, []);

  return (
    <MainLayout>
      <div className="p-6 space-y-8 min-h-screen bg-gray-50 text-gray-900 dark:bg-gray-900 dark:text-gray-100">
        
        {/* Overview */}
        <div>
          <h1 className="text-2xl font-bold mb-4">Analytics Overview</h1>

          {/* Controls */}
          <div className="flex gap-4 items-center mb-6">
            <label className="font-semibold text-gray-700 dark:text-gray-300">
              Period:
            </label>

            {/* Month */}
            <select
              value={Number(selectedMonth.split("-")[1]) - 1}
              onChange={(e) => {
                const year = selectedMonth.split("-")[0];
                const month = String(Number(e.target.value) + 1).padStart(2, "0");
                setSelectedMonth(`${year}-${month}`);
              }}
              className="border rounded-lg px-4 py-2 bg-white text-gray-700 
              dark:bg-gray-800 dark:text-gray-200 dark:border-gray-600"
            >
              {["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"].map((m, idx) => {
                const isDisabled =
                  Number(selectedMonth.split("-")[0]) === maxYear && idx > maxMonth;

                return (
                  <option key={idx} value={idx} disabled={isDisabled}>
                    {m}
                  </option>
                );
              })}
            </select>

            {/* Year */}
            <select
              value={selectedMonth.split("-")[0]}
              onChange={(e) =>
                setSelectedMonth(`${e.target.value}-${selectedMonth.split("-")[1]}`)
              }
              className="border rounded-lg px-4 py-2 bg-white text-gray-700 
              dark:bg-gray-800 dark:text-gray-200 dark:border-gray-600"
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
                const today = new Date();
                setSelectedMonth(
                  `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
                );
              }}
              className="bg-indigo-500 text-white px-4 py-2 rounded-lg hover:bg-indigo-600"
            >
              This Month
            </button>
          </div>

          {/* Charts */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="bg-white dark:bg-gray-800 shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">
                  Flag Trend - {monthYear}
                </h2>

                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={flagTrend} margin={{ top: 10, right: 20, left: 10, bottom: 40 }}>
                    <XAxis dataKey="day" tick={{ fill: "#9CA3AF", fontSize: 12 }} 
                    label={{ 
                      value: "Day", 
                      position: "insideBottom", 
                      offset: -1,
                      dy: 10,
                      fill: "#9CA3AF"
                    }}/>
                    <YAxis tick={{ fill: "#9CA3AF" }} allowDecimals={false}   
                    label={{ 
                      value: "Number of Flags",
                      angle: -90,
                      position: "left",
                      dx: 10,
                      style: { fill: "#9CA3AF", textAnchor: "middle" }
                    }}/>
                    <Tooltip
                      contentStyle={{ backgroundColor: "#1F2937", border: "none" }}
                      labelStyle={{ color: "#E5E7EB" }}
                    />
                    <Bar dataKey="count" fill="#6366F1" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card className="bg-white dark:bg-gray-800 shadow-sm rounded-2xl flex items-center justify-center">
              <CardContent className="flex flex-col items-center justify-center">
                <h2 className="text-lg font-semibold mb-2">Average Review Time</h2>
                <p
                  className={`text-4xl font-bold ${
                    avgReviewTime > 20
                      ? "text-red-500"
                      : avgReviewTime > 10
                      ? "text-amber-500"
                      : "text-indigo-600"
                  }`}
                >
                  {avgReviewTime > 0 ? `${avgReviewTime} mins` : "N/A"}
                </p>
              </CardContent>
            </Card>
          </div>
        </div>

        {/* Risk */}
        <div>
          <h1 className="text-2xl font-bold mb-4">Risk Analysis</h1>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="bg-white dark:bg-gray-800 shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Top Flagged Users</h2>
                <ul>
                  {topUsers.map((user, i) => (
                    <li key={i} className="flex justify-between py-2 border-b dark:border-gray-700">
                      <span>{user.name}</span>
                      <span className="font-semibold">{user.count}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>

            <Card className="bg-white dark:bg-gray-800 shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Top Flagged Content</h2>
                <ul>
                  {topContent.map((c, i) => (
                    <li key={i} className="flex justify-between py-2 border-b dark:border-gray-700">
                      <span>{c.name}</span>
                      <span className="font-semibold">{c.count}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </div>
        </div>

        {/* Audit */}
        <div>
          <h1 className="text-2xl font-bold mb-4">Audit Logs</h1>

          <Card className="bg-white dark:bg-gray-800 shadow-sm rounded-2xl">
            <CardContent>
              <table className="w-full text-left">
                <thead>
                  <tr className="text-gray-500 dark:text-gray-400 text-sm">
                    <th className="pb-2">Admin</th>
                    <th className="pb-2">Action</th>
                    <th className="pb-2">Target</th>
                    <th className="pb-2">Time</th>
                  </tr>
                </thead>

                <tbody>
                  {auditLogs.map((log, i) => (
                    <tr key={i} className="border-t dark:border-gray-700">
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