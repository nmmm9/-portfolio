import React, { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Activity,
  Newspaper,
  ArrowLeft,
  Search,
  Filter,
  TrendingUp,
  Database,
  Loader2,
  Calendar,
  CheckCircle2,
  BarChart3,
  ExternalLink,
  X,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
  PieChart,
  Pie,
  Cell,
} from "recharts";

const fmt = new Intl.NumberFormat("ko-KR");

// 전문적인 색상 팔레트
const COLORS = {
  primary: "#0F172A",
  secondary: "#64748B",
  accent: "#0EA5E9",
  success: "#10B981",
  warning: "#F59E0B",
  background: "#F8FAFC",
  cardBg: "#FFFFFF",
  border: "#E2E8F0",
};

// 카테고리 색상
const CATEGORY_COLORS: Record<string, string> = {
  환경: "#10B981",
  기부: "#8B5CF6",
  교육: "#F59E0B",
  일자리: "#3B82F6",
  지역사회: "#EC4899",
  윤리경영: "#6366F1",
  혁신: "#14B8A6",
  전체: "#6B7280",
};

interface Organization {
  id: number;
  name: string;
  type?: string;
}

interface NewsItem {
  id: number;
  organizationId: number;
  organizationName?: string;
  title: string;
  description: string;
  url: string;
  category: string;
  matchedKeywords: string;
  publishedDate: string;
}

interface PageResponse {
  content: NewsItem[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export default function PositiveNewsDetail() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingNews, setLoadingNews] = useState(false);

  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [newsData, setNewsData] = useState<PageResponse | null>(null);
  const [yearStats, setYearStats] = useState<any[]>([]);
  const [categoryStats, setCategoryStats] = useState<any[]>([]);

  const [selectedOrg, setSelectedOrg] = useState("");
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [currentPage, setCurrentPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);
  const [orgPage, setOrgPage] = useState(1);
  const itemsPerPage = 5;
  const pageSize = 20;

  useEffect(() => {
    loadOrganizations();
  }, []);

  useEffect(() => {
    if (selectedOrg) {
      loadNewsData();
      loadStats();
    }
  }, [selectedOrg, selectedYear, selectedCategory, currentPage]);

  async function loadOrganizations() {
    try {
      const response = await fetch(`${API_BASE}/api/organizations`);
      if (!response.ok) throw new Error("Failed to fetch organizations");

      const data = await response.json();
      setOrganizations(data);
      setIsApiConnected(true);
      setLoading(false);

      // 첫 번째 조직 자동 선택
      if (data.length > 0) {
        setSelectedOrg(String(data[0].id));
      }
    } catch (error) {
      console.error("조직 로드 실패:", error);
      setIsApiConnected(false);
      setLoading(false);
    }
  }

  async function loadNewsData() {
    if (!selectedOrg) return;

    setLoadingNews(true);
    try {
      let url = `${API_BASE}/api/positive-news/organization/${selectedOrg}?page=${currentPage}&size=${pageSize}`;

      if (selectedYear) {
        url += `&year=${selectedYear}`;
      }
      if (selectedCategory && selectedCategory !== "all") {
        url += `&category=${selectedCategory}`;
      }

      const response = await fetch(url);
      if (!response.ok) throw new Error("Failed to fetch news");

      const data: PageResponse = await response.json();
      setNewsData(data);
    } catch (error) {
      console.error("뉴스 로드 실패:", error);
      setNewsData(null);
    } finally {
      setLoadingNews(false);
    }
  }

  async function loadStats() {
    if (!selectedOrg) return;

    try {
      const [yearResponse, categoryResponse] = await Promise.all([
        fetch(
          `${API_BASE}/api/positive-news/organization/${selectedOrg}/stats/by-year`
        ),
        fetch(
          `${API_BASE}/api/positive-news/organization/${selectedOrg}/stats/by-category`
        ),
      ]);

      if (yearResponse.ok) {
        const yearData = await yearResponse.json();
        setYearStats(yearData);
      }

      if (categoryResponse.ok) {
        const catData = await categoryResponse.json();
        setCategoryStats(catData);
      }
    } catch (error) {
      console.error("통계 로드 실패:", error);
    }
  }

  // 필터링된 조직 목록
  const filteredOrganizations = useMemo(() => {
    if (!searchTerm) return organizations;
    return organizations.filter((org) =>
      org.name.toLowerCase().includes(searchTerm.toLowerCase())
    );
  }, [organizations, searchTerm]);

  // 페이지네이션
  const totalOrgPages = Math.ceil(filteredOrganizations.length / itemsPerPage);
  const paginatedOrgs = filteredOrganizations.slice(
    (orgPage - 1) * itemsPerPage,
    orgPage * itemsPerPage
  );

  const selectedOrgData = organizations.find(
    (org) => org.id === Number(selectedOrg)
  );

  // 차트 데이터 변환
  const yearChartData = yearStats.map((stat) => ({
    year: stat.year,
    count: stat.count,
  }));

  const categoryChartData = categoryStats.map((stat) => ({
    name: stat.category,
    value: stat.count,
  }));

  if (loading) {
    return (
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
          background: COLORS.background,
        }}
      >
        <div style={{ textAlign: "center" }}>
          <Loader2
            style={{
              width: "48px",
              height: "48px",
              margin: "0 auto 16px auto",
              display: "block",
              animation: "spin 1s linear infinite",
              color: COLORS.accent,
            }}
          />
          <p
            style={{
              color: COLORS.secondary,
              fontSize: "16px",
              fontWeight: 500,
            }}
          >
            데이터를 불러오는 중...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        width: "100vw",
        background: COLORS.background,
        margin: 0,
        padding: 0,
      }}
    >
      {/* 헤더 */}
      <header
        style={{
          width: "100vw",
          backgroundColor: "rgba(255, 255, 255, 0.98)",
          backdropFilter: "blur(12px)",
          borderBottom: `1px solid ${COLORS.border}`,
          position: "sticky",
          top: 0,
          zIndex: 50,
          boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
        }}
      >
        <div
          style={{
            width: "100%",
            padding: "16px 32px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "20px" }}>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => navigate("/")}
              style={{ borderRadius: "10px", width: "40px", height: "40px" }}
            >
              <ArrowLeft style={{ width: "20px", height: "20px" }} />
            </Button>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "16px",
                flex: 1,
              }}
            >
              <div
                style={{
                  padding: "10px",
                  background: COLORS.accent,
                  borderRadius: "12px",
                }}
              >
                <Newspaper
                  style={{ width: "24px", height: "24px", color: "white" }}
                />
              </div>
              <div>
                <h1
                  style={{
                    fontSize: "24px",
                    fontWeight: 700,
                    color: COLORS.primary,
                    marginBottom: "2px",
                    letterSpacing: "-0.5px",
                  }}
                >
                  긍정 뉴스 분석
                </h1>
                <p
                  style={{
                    fontSize: "14px",
                    color: COLORS.secondary,
                    fontWeight: 500,
                  }}
                >
                  기업별 긍정 뉴스 추적 및 분석
                </p>
              </div>
            </div>
            <Badge
              variant={isApiConnected ? "default" : "destructive"}
              style={{
                background: isApiConnected ? COLORS.success : COLORS.warning,
                padding: "6px 14px",
                fontSize: "13px",
                fontWeight: 500,
              }}
            >
              <Database
                style={{ width: "14px", height: "14px", marginRight: "6px" }}
              />
              {isApiConnected ? "API 연결됨" : "API 연결 안 됨"}
            </Badge>
          </div>
        </div>
      </header>

      {/* 메인 컨텐츠 */}
      <main style={{ width: "100%", padding: "32px" }}>
        {/* 필터 섹션 */}
        <div
          style={{
            width: "100%",
            marginBottom: "24px",
            background: COLORS.cardBg,
            borderRadius: "16px",
            border: `1px solid ${COLORS.border}`,
            padding: "24px",
            boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
          }}
        >
          <div
            style={{
              display: "flex",
              gap: "16px",
              flexWrap: "wrap",
              alignItems: "flex-end",
            }}
          >
            {/* 회사 검색 */}
            <div style={{ flex: "1 1 300px", position: "relative" }}>
              <label
                style={{
                  display: "block",
                  marginBottom: "8px",
                  fontSize: "14px",
                  fontWeight: 600,
                  color: COLORS.primary,
                }}
              >
                <Search
                  style={{
                    width: "14px",
                    height: "14px",
                    display: "inline",
                    marginRight: "6px",
                  }}
                />
                회사 검색
              </label>
              <div style={{ position: "relative" }}>
                <Input
                  placeholder="회사명 검색..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value);
                    setShowDropdown(true);
                    setOrgPage(1);
                  }}
                  onFocus={() => setShowDropdown(true)}
                  style={{
                    paddingRight: searchTerm ? "36px" : "12px",
                    fontSize: "14px",
                    height: "42px",
                  }}
                />
                {searchTerm && (
                  <button
                    onClick={() => {
                      setSearchTerm("");
                      setShowDropdown(false);
                    }}
                    style={{
                      position: "absolute",
                      right: "10px",
                      top: "50%",
                      transform: "translateY(-50%)",
                      background: "none",
                      border: "none",
                      cursor: "pointer",
                      padding: "4px",
                      display: "flex",
                      alignItems: "center",
                    }}
                  >
                    <X
                      style={{
                        width: "16px",
                        height: "16px",
                        color: COLORS.secondary,
                      }}
                    />
                  </button>
                )}
              </div>

              {/* 드롭다운 */}
              {showDropdown && filteredOrganizations.length > 0 && (
                <div
                  style={{
                    position: "absolute",
                    top: "100%",
                    left: 0,
                    right: 0,
                    marginTop: "4px",
                    background: "white",
                    border: `1px solid ${COLORS.border}`,
                    borderRadius: "12px",
                    boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
                    maxHeight: "320px",
                    overflow: "hidden",
                    zIndex: 100,
                  }}
                >
                  <div style={{ maxHeight: "260px", overflowY: "auto" }}>
                    {paginatedOrgs.map((org) => (
                      <button
                        key={org.id}
                        onClick={() => {
                          setSelectedOrg(String(org.id));
                          setShowDropdown(false);
                          setSearchTerm("");
                          setSelectedYear(null);
                          setSelectedCategory("all");
                          setCurrentPage(0);
                        }}
                        style={{
                          width: "100%",
                          padding: "12px 16px",
                          textAlign: "left",
                          background:
                            selectedOrg === String(org.id)
                              ? "#F0FDF4"
                              : "white",
                          cursor: "pointer",
                          transition: "all 0.15s",
                          borderBottom: `1px solid ${COLORS.border}`,
                          border: "none",
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.background =
                            selectedOrg === String(org.id)
                              ? "#F0FDF4"
                              : "#F8FAFC";
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background =
                            selectedOrg === String(org.id)
                              ? "#F0FDF4"
                              : "white";
                        }}
                      >
                        <div
                          style={{
                            fontWeight: 600,
                            color: COLORS.primary,
                            fontSize: "13px",
                          }}
                        >
                          {org.name}
                        </div>
                        <div
                          style={{ fontSize: "11px", color: COLORS.secondary }}
                        >
                          {org.type || "조직"}
                        </div>
                      </button>
                    ))}
                  </div>

                  {totalOrgPages > 1 && (
                    <div
                      style={{
                        padding: "8px 14px",
                        background: "#F8FAFC",
                        borderTop: `1px solid ${COLORS.border}`,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                      }}
                    >
                      <div
                        style={{ fontSize: "11px", color: COLORS.secondary }}
                      >
                        {filteredOrganizations.length}개 중{" "}
                        {(orgPage - 1) * itemsPerPage + 1}-
                        {Math.min(
                          orgPage * itemsPerPage,
                          filteredOrganizations.length
                        )}
                        번째
                      </div>
                      <div style={{ display: "flex", gap: "4px" }}>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() =>
                            setOrgPage((prev) => Math.max(1, prev - 1))
                          }
                          disabled={orgPage === 1}
                          style={{ height: "28px", padding: "0 10px" }}
                        >
                          <ChevronLeft
                            style={{ width: "14px", height: "14px" }}
                          />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() =>
                            setOrgPage((prev) =>
                              Math.min(totalOrgPages, prev + 1)
                            )
                          }
                          disabled={orgPage === totalOrgPages}
                          style={{ height: "28px", padding: "0 10px" }}
                        >
                          <ChevronRight
                            style={{ width: "14px", height: "14px" }}
                          />
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 연도 필터 */}
            <div style={{ flex: "0 0 150px" }}>
              <label
                style={{
                  display: "block",
                  marginBottom: "8px",
                  fontSize: "14px",
                  fontWeight: 600,
                  color: COLORS.primary,
                }}
              >
                <Calendar
                  style={{
                    width: "14px",
                    height: "14px",
                    display: "inline",
                    marginRight: "6px",
                  }}
                />
                연도
              </label>
              <select
                value={selectedYear || ""}
                onChange={(e) => {
                  setSelectedYear(
                    e.target.value ? Number(e.target.value) : null
                  );
                  setCurrentPage(0);
                }}
                style={{
                  width: "100%",
                  height: "42px",
                  padding: "0 12px",
                  borderRadius: "8px",
                  border: `1px solid ${COLORS.border}`,
                  fontSize: "14px",
                  cursor: "pointer",
                }}
              >
                <option value="">전체</option>
                {yearStats.map((stat) => (
                  <option key={stat.year} value={stat.year}>
                    {stat.year}년 ({stat.count})
                  </option>
                ))}
              </select>
            </div>

            {/* 카테고리 필터 */}
            <div style={{ flex: "0 0 180px" }}>
              <label
                style={{
                  display: "block",
                  marginBottom: "8px",
                  fontSize: "14px",
                  fontWeight: 600,
                  color: COLORS.primary,
                }}
              >
                <Filter
                  style={{
                    width: "14px",
                    height: "14px",
                    display: "inline",
                    marginRight: "6px",
                  }}
                />
                카테고리
              </label>
              <select
                value={selectedCategory}
                onChange={(e) => {
                  setSelectedCategory(e.target.value);
                  setCurrentPage(0);
                }}
                style={{
                  width: "100%",
                  height: "42px",
                  padding: "0 12px",
                  borderRadius: "8px",
                  border: `1px solid ${COLORS.border}`,
                  fontSize: "14px",
                  cursor: "pointer",
                }}
              >
                <option value="all">전체</option>
                {categoryStats.map((stat) => (
                  <option key={stat.category} value={stat.category}>
                    {stat.category} ({stat.count})
                  </option>
                ))}
              </select>
            </div>

            {/* 필터 초기화 */}
            <Button
              variant="outline"
              onClick={() => {
                setSelectedYear(null);
                setSelectedCategory("all");
                setCurrentPage(0);
              }}
              style={{ height: "42px" }}
            >
              필터 초기화
            </Button>
          </div>

          {selectedOrgData && (
            <div
              style={{
                marginTop: "16px",
                padding: "12px",
                background: "#F0F9FF",
                borderRadius: "8px",
                border: "1px solid #BAE6FD",
              }}
            >
              <span
                style={{
                  fontSize: "13px",
                  fontWeight: 600,
                  color: COLORS.primary,
                }}
              >
                선택된 회사: {selectedOrgData.name}
              </span>
              {newsData && (
                <span
                  style={{
                    fontSize: "13px",
                    color: COLORS.secondary,
                    marginLeft: "12px",
                  }}
                >
                  • 총 {fmt.format(newsData.totalElements)}개의 뉴스
                </span>
              )}
            </div>
          )}
        </div>

        {/* 통계 섹션 */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(500px, 1fr))",
            gap: "24px",
            marginBottom: "24px",
          }}
        >
          {/* 연도별 통계 차트 */}
          <Card
            style={{
              borderRadius: "16px",
              border: `1px solid ${COLORS.border}`,
            }}
          >
            <CardHeader>
              <CardTitle
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  fontSize: "18px",
                }}
              >
                <BarChart3
                  style={{
                    width: "20px",
                    height: "20px",
                    color: COLORS.accent,
                  }}
                />
                연도별 뉴스 추이
              </CardTitle>
            </CardHeader>
            <CardContent>
              {yearChartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={yearChartData}>
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke={COLORS.border}
                    />
                    <XAxis
                      dataKey="year"
                      tick={{ fill: COLORS.secondary, fontSize: 12 }}
                    />
                    <YAxis tick={{ fill: COLORS.secondary, fontSize: 12 }} />
                    <Tooltip
                      contentStyle={{
                        background: "white",
                        border: `1px solid ${COLORS.border}`,
                        borderRadius: "8px",
                      }}
                    />
                    <Bar
                      dataKey="count"
                      fill={COLORS.accent}
                      radius={[8, 8, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div
                  style={{
                    textAlign: "center",
                    padding: "40px 0",
                    color: COLORS.secondary,
                  }}
                >
                  데이터가 없습니다
                </div>
              )}
            </CardContent>
          </Card>

          {/* 카테고리별 통계 차트 */}
          <Card
            style={{
              borderRadius: "16px",
              border: `1px solid ${COLORS.border}`,
            }}
          >
            <CardHeader>
              <CardTitle
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  fontSize: "18px",
                }}
              >
                <Activity
                  style={{
                    width: "20px",
                    height: "20px",
                    color: COLORS.success,
                  }}
                />
                카테고리별 분포
              </CardTitle>
            </CardHeader>
            <CardContent>
              {categoryChartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie
                      data={categoryChartData}
                      cx="50%"
                      cy="50%"
                      labelLine={false}
                      label={(entry) => `${entry.name} (${entry.value})`}
                      outerRadius={80}
                      fill="#8884d8"
                      dataKey="value"
                    >
                      {categoryChartData.map((entry, index) => (
                        <Cell
                          key={`cell-${index}`}
                          fill={CATEGORY_COLORS[entry.name] || COLORS.secondary}
                        />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div
                  style={{
                    textAlign: "center",
                    padding: "40px 0",
                    color: COLORS.secondary,
                  }}
                >
                  데이터가 없습니다
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* 뉴스 목록 */}
        <Card
          style={{ borderRadius: "16px", border: `1px solid ${COLORS.border}` }}
        >
          <CardHeader>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
              }}
            >
              <CardTitle
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  fontSize: "18px",
                }}
              >
                <Newspaper
                  style={{
                    width: "20px",
                    height: "20px",
                    color: COLORS.primary,
                  }}
                />
                뉴스 목록
                {newsData && (
                  <span
                    style={{
                      fontSize: "14px",
                      color: COLORS.secondary,
                      fontWeight: "normal",
                    }}
                  >
                    ({fmt.format(newsData.totalElements)}건)
                  </span>
                )}
              </CardTitle>
              {loadingNews && (
                <Loader2
                  style={{
                    width: "20px",
                    height: "20px",
                    animation: "spin 1s linear infinite",
                    color: COLORS.accent,
                  }}
                />
              )}
            </div>
          </CardHeader>
          <CardContent>
            {!selectedOrg ? (
              <div
                style={{
                  textAlign: "center",
                  padding: "40px 0",
                  color: COLORS.secondary,
                }}
              >
                회사를 선택해주세요
              </div>
            ) : loadingNews ? (
              <div
                style={{
                  textAlign: "center",
                  padding: "40px 0",
                  color: COLORS.secondary,
                }}
              >
                <Loader2
                  style={{
                    width: "32px",
                    height: "32px",
                    margin: "0 auto 12px",
                    animation: "spin 1s linear infinite",
                    color: COLORS.accent,
                  }}
                />
                <p>뉴스를 불러오는 중...</p>
              </div>
            ) : !newsData || newsData.content.length === 0 ? (
              <div style={{ textAlign: "center", padding: "40px 0" }}>
                <div
                  style={{
                    width: "64px",
                    height: "64px",
                    margin: "0 auto 16px",
                    background: COLORS.background,
                    borderRadius: "50%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Newspaper
                    style={{
                      width: "32px",
                      height: "32px",
                      color: COLORS.secondary,
                    }}
                  />
                </div>
                <p style={{ color: COLORS.secondary, fontSize: "15px" }}>
                  {selectedYear || selectedCategory !== "all"
                    ? "선택한 필터에 해당하는 뉴스가 없습니다"
                    : "뉴스가 수집 중입니다. 잠시 후 다시 확인해주세요."}
                </p>
              </div>
            ) : (
              <>
                {/* 뉴스 카드 */}
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    gap: "16px",
                  }}
                >
                  {newsData.content.map((item) => (
                    <a
                      key={item.id}
                      href={item.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        display: "block",
                        padding: "20px",
                        background: "white",
                        border: `1px solid ${COLORS.border}`,
                        borderRadius: "12px",
                        textDecoration: "none",
                        transition: "all 0.2s",
                        cursor: "pointer",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.boxShadow =
                          "0 4px 12px rgba(0,0,0,0.08)";
                        e.currentTarget.style.transform = "translateY(-2px)";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.boxShadow = "none";
                        e.currentTarget.style.transform = "translateY(0)";
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "flex-start",
                          marginBottom: "12px",
                        }}
                      >
                        <h3
                          style={{
                            fontSize: "16px",
                            fontWeight: 600,
                            color: COLORS.primary,
                            flex: 1,
                            marginRight: "16px",
                            lineHeight: "1.5",
                          }}
                        >
                          {item.title}
                        </h3>
                        <div
                          style={{
                            display: "flex",
                            gap: "8px",
                            alignItems: "center",
                          }}
                        >
                          <Badge
                            style={{
                              background:
                                CATEGORY_COLORS[item.category] ||
                                COLORS.secondary,
                              color: "white",
                              padding: "4px 12px",
                              borderRadius: "16px",
                              fontSize: "12px",
                              fontWeight: 500,
                              whiteSpace: "nowrap",
                            }}
                          >
                            {item.category}
                          </Badge>
                          <ExternalLink
                            style={{
                              width: "16px",
                              height: "16px",
                              color: COLORS.secondary,
                            }}
                          />
                        </div>
                      </div>

                      <p
                        style={{
                          color: COLORS.secondary,
                          fontSize: "14px",
                          lineHeight: "1.6",
                          marginBottom: "12px",
                        }}
                      >
                        {item.description}
                      </p>

                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                          fontSize: "13px",
                          color: "#9CA3AF",
                        }}
                      >
                        <span>{item.publishedDate}</span>
                        <span>키워드: {item.matchedKeywords}</span>
                      </div>
                    </a>
                  ))}
                </div>

                {/* 페이지네이션 */}
                {newsData.totalPages > 1 && (
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "center",
                      alignItems: "center",
                      gap: "8px",
                      marginTop: "32px",
                    }}
                  >
                    <Button
                      variant="outline"
                      onClick={() =>
                        setCurrentPage((prev) => Math.max(0, prev - 1))
                      }
                      disabled={currentPage === 0}
                      style={{ minWidth: "80px" }}
                    >
                      <ChevronLeft
                        style={{
                          width: "16px",
                          height: "16px",
                          marginRight: "4px",
                        }}
                      />
                      이전
                    </Button>

                    <span
                      style={{
                        padding: "0 16px",
                        color: COLORS.secondary,
                        fontSize: "14px",
                        fontWeight: 500,
                      }}
                    >
                      {currentPage + 1} / {newsData.totalPages}
                    </span>

                    <Button
                      variant="outline"
                      onClick={() =>
                        setCurrentPage((prev) =>
                          Math.min(newsData.totalPages - 1, prev + 1)
                        )
                      }
                      disabled={currentPage === newsData.totalPages - 1}
                      style={{ minWidth: "80px" }}
                    >
                      다음
                      <ChevronRight
                        style={{
                          width: "16px",
                          height: "16px",
                          marginLeft: "4px",
                        }}
                      />
                    </Button>
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </main>

      {/* 푸터 */}
      <footer
        style={{
          width: "100%",
          backgroundColor: "white",
          borderTop: `1px solid ${COLORS.border}`,
          padding: "32px 0",
          marginTop: "64px",
        }}
      >
        <div
          style={{
            width: "100%",
            maxWidth: "1600px",
            margin: "0 auto",
            padding: "0 32px",
            textAlign: "center",
          }}
        >
          <p style={{ fontSize: "14px", color: COLORS.secondary }}>
            © 2024 Social Impact Tracker. All rights reserved.
          </p>
        </div>
      </footer>
    </div>
  );
}
