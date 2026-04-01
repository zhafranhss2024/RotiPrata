import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { MainLayout } from "@/components/layout/MainLayout";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";
import { fetchFlagFromDb, FlagByDate } from "@/lib/api";

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
  const maxMonth = today.getMonth(); // 0-based

  const [selectedMonth, setSelectedMonth] = useState(() => {
    const today = new Date();
    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
  });

  // Helper to get all days in month
  const getDaysInMonth = (year: number, month: number) => {
    return new Array(new Date(year, month, 0).getDate()).fill(0).map((_, i) => i + 1);
  };

  // Transform backend data into full month array with 0s
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

  // Fetch flags whenever selected month changes
  useEffect(() => {
    const loadFlags = async () => {
      const [yearStr, monthStr] = selectedMonth.split("-");
      const year = Number(yearStr);
      const month = Number(monthStr);

      try {
        const data = await fetchFlagFromDb(monthStr, yearStr);
        const formattedData = formatFlagDataForMonth(data, year, month);
        setFlagTrend(formattedData);

        const monthYearStr = new Date(year, month - 1, 1).toLocaleDateString("en-GB", {
          month: "long",
          year: "numeric",
        });
        setMonthYear(monthYearStr);
      } catch (err) {
        console.error("Failed to fetch flags:", err);
      }
    };

    loadFlags();
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
              value={Number(selectedMonth.split("-")[1]) - 1}
              onChange={(e) => {
                const year = selectedMonth.split("-")[0];
                const month = String(Number(e.target.value) + 1).padStart(2, "0");
                setSelectedMonth(`${year}-${month}`);
              }}
              className="border rounded-lg px-4 py-2 pr-8 bg-white text-gray-700 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-300"
            >
              {["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"].map((m, idx) => {
                const isDisabled = Number(selectedMonth.split("-")[0]) === maxYear && idx > maxMonth;
                return (
                  <option
                    key={idx}
                    value={idx}
                    disabled={isDisabled}
                    className={`${isDisabled ? "text-gray-400 cursor-not-allowed" : "text-gray-700"}`}
                  >
                    {m}
                  </option>
                );
              })}
            </select>

            {/* Year dropdown */}
            <select
              value={selectedMonth.split("-")[0]}
              onChange={(e) => setSelectedMonth(`${e.target.value}-${selectedMonth.split("-")[1]}`)}
              className="border rounded-lg px-4 py-2 pr-8 bg-white text-gray-700 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-300"
            >
              {Array.from({ length: new Date().getFullYear() - 2020 + 1 }, (_, i) => 2020 + i).map(
                (y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                )
              )}
            </select>

            {/* This Month button */}
            <button
              onClick={() => {
                const today = new Date();
                setSelectedMonth(
                  `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
                );
              }}
              className="bg-indigo-500 text-white px-4 py-2 rounded-lg hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-indigo-300"
            >
              This Month
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Flag Trend - {monthYear}</h2>
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={flagTrend}>
                    <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="count" fill="#4F46E5" />
                  </BarChart>
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