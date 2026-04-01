import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { MainLayout } from "@/components/layout/MainLayout";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";

// Types
type FlagTrend = { day: string; count: number };
type TopItem = { name: string; count: number };
type AuditLog = { admin: string; action: string; targetId: number; time: string };

const AdminAnalytics = () => {
  const [flagTrend, setFlagTrend] = useState<FlagTrend[]>([]);
  const [topUsers, setTopUsers] = useState<TopItem[]>([]);
  const [topContent, setTopContent] = useState<TopItem[]>([]);
  const [avgReviewTime, setAvgReviewTime] = useState<number>(0);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);

  useEffect(() => {
    setFlagTrend([
      { day: "Mon", count: 5 },
      { day: "Tue", count: 8 },
      { day: "Wed", count: 3 },
      { day: "Thu", count: 10 },
      { day: "Fri", count: 6 },
    ]);

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
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card className="shadow-sm rounded-2xl">
              <CardContent>
                <h2 className="text-lg font-semibold mb-2">Flag Trend (Last 7 Days)</h2>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={flagTrend}>
                    <XAxis dataKey="day" />
                    <YAxis />
                    <Tooltip />
                    <Line type="monotone" dataKey="count" />
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