import React, { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Activity,
  TrendingUp,
  Building2,
  ArrowLeft,
  Calendar,
  Database,
  Loader2,
  AlertCircle,
  DollarSign,
  Search,
  X,
  ChevronDown,
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
  LineChart,
  Line,
} from "recharts";

const fmt = new Intl.NumberFormat("ko-KR");

// 프로페셔널한 색상 팔레트
const COLORS = {
  primary: "#0F172A",
  secondary: "#64748B",
  accent: "#6366F1",
  success: "#10B981",
  warning: "#F59E0B",
  background: "#F8FAFC",
  cardBg: "#FFFFFF",
  border: "#E2E8F0",
  hover: "#F1F5F9",
};

interface Organization {
  id: number;
  name: string;
  donationCount?: number;
  totalDonations?: number;
}

interface Donation {
  id: number;
  organizationId: number;
  organizationName: string;
  year: number;
  quarter: number;
  donationAmount: number;
  reportType: string;
  verificationStatus: string;
  dataSource: string;
}

export default function DonationsDetail() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [donations, setDonations] = useState<Donation[]>([]);
  const [organizations, setOrganizations] = useState<Organization[]>([]);

  const [selectedYear, setSelectedYear] = useState("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedOrg, setSelectedOrg] = useState<Organization | null>(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const [dropdownPage, setDropdownPage] = useState(1);

  const ORGS_PER_PAGE = 10;

  useEffect(() => {
    checkApiConnection();
    loadDonations();
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: any) => {
      if (!e.target.closest(".dropdown-container")) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("click", handleClickOutside);
    return () => document.removeEventListener("click", handleClickOutside);
  }, []);

  async function checkApiConnection() {
    try {
      const response = await fetch(`${API_BASE}/api/organizations`);
      setIsApiConnected(response.ok);
    } catch (error) {
      setIsApiConnected(false);
    }
  }

  async function loadDonations() {
    try {
      const response = await fetch(`${API_BASE}/api/donations`);
      if (!response.ok) throw new Error("Failed to fetch donations");

      const data = await response.json();

      // 데이터 정규화
      const normalizedData = data.map((item: any) => ({
        id: item.id,
        organizationId: item.organizationId || item.organization?.id,
        organizationName: item.organizationName || item.organization?.name,
        year: item.year,
        quarter: item.quarter,
        donationAmount:
          typeof item.donationAmount === "number"
            ? item.donationAmount
            : Number(item.donationAmount || 0),
        reportType: item.reportType || "N/A",
        verificationStatus:
          item.verificationStatus === "DART_AUTO" ? "자동수집" : "검증완료",
        dataSource: "DART_AUTO",
      }));

      setDonations(normalizedData);

      // 조직별 기부금 정보 집계
      const orgMap = new Map<number, Organization>();
      normalizedData.forEach((d: Donation) => {
        if (!orgMap.has(d.organizationId)) {
          orgMap.set(d.organizationId, {
            id: d.organizationId,
            name: d.organizationName,
            donationCount: 0,
            totalDonations: 0,
          });
        }
        const org = orgMap.get(d.organizationId)!;
        org.donationCount! += 1;
        org.totalDonations! += d.donationAmount;
      });

      const orgsArray = Array.from(orgMap.values()).sort(
        (a, b) => b.totalDonations! - a.totalDonations!
      );
      setOrganizations(orgsArray);
      setLoading(false);
    } catch (error) {
      console.error("기부금 데이터 로드 실패:", error);
      setIsApiConnected(false);
      setLoading(false);
    }
  }

  // 필터링된 조직 목록
  const filteredOrganizations = useMemo(() => {
    if (!searchTerm.trim()) return organizations;
    const searchLower = searchTerm.toLowerCase();
    return organizations.filter((org) =>
      org.name.toLowerCase().includes(searchLower)
    );
  }, [organizations, searchTerm]);

  // 페이지네이션된 조직 목록
  const paginatedOrgs = useMemo(() => {
    const start = (dropdownPage - 1) * ORGS_PER_PAGE;
    const end = start + ORGS_PER_PAGE;
    return filteredOrganizations.slice(start, end);
  }, [filteredOrganizations, dropdownPage]);

  const totalPages = Math.ceil(filteredOrganizations.length / ORGS_PER_PAGE);

  // 검색어와 연도, 조직으로 필터링
  const filteredDonations = useMemo(() => {
    let result = donations;

    // 연도 필터
    if (selectedYear !== "all") {
      result = result.filter((d) => d.year === parseInt(selectedYear));
    }

    // 조직 필터
    if (selectedOrg) {
      result = result.filter((d) => d.organizationId === selectedOrg.id);
    }

    return result;
  }, [donations, selectedYear, selectedOrg]);

  // 통계 계산
  const statistics = useMemo(() => {
    const total = filteredDonations.reduce(
      (sum, d) => sum + d.donationAmount,
      0
    );
    const uniqueCompanies = new Set(
      filteredDonations.map((d) => d.organizationName)
    ).size;
    const avgPerCompany = uniqueCompanies > 0 ? total / uniqueCompanies : 0;
    const topDonor = [...filteredDonations].sort(
      (a, b) => b.donationAmount - a.donationAmount
    )[0];

    return {
      total,
      count: filteredDonations.length,
      uniqueCompanies,
      avgPerCompany,
      topDonor: topDonor ? topDonor.organizationName : "-",
      topAmount: topDonor ? topDonor.donationAmount : 0,
    };
  }, [filteredDonations]);

  // 연도별 트렌드 데이터
  const trendData = useMemo(() => {
    const yearlyData: Record<number, number> = {};
    const dataToUse = selectedOrg ? filteredDonations : donations;

    dataToUse.forEach((d) => {
      if (!yearlyData[d.year]) {
        yearlyData[d.year] = 0;
      }
      yearlyData[d.year] += d.donationAmount;
    });

    return Object.keys(yearlyData)
      .sort()
      .map((year) => ({
        year: year,
        total: Math.round(yearlyData[Number(year)] / 100000000) / 10,
      }));
  }, [donations, filteredDonations, selectedOrg]);

  // 상위 10개 기업 데이터
  const topCompanies = useMemo(() => {
    const companyTotals: Record<string, number> = {};
    const dataToUse = selectedYear === "all" ? donations : filteredDonations;

    dataToUse.forEach((d) => {
      if (!companyTotals[d.organizationName]) {
        companyTotals[d.organizationName] = 0;
      }
      companyTotals[d.organizationName] += d.donationAmount;
    });

    return Object.entries(companyTotals)
      .map(([name, amount]) => ({
        name,
        amount: Math.round(amount / 100000000) / 10,
      }))
      .sort((a, b) => b.amount - a.amount)
      .slice(0, 10);
  }, [donations, filteredDonations, selectedYear]);

  const formatAmount = (amount: number) => {
    if (amount >= 1000000000) {
      return `${(amount / 1000000000).toFixed(1)}십억원`;
    } else if (amount >= 100000000) {
      return `${(amount / 100000000).toFixed(1)}억원`;
    } else if (amount >= 10000000) {
      return `${(amount / 10000000).toFixed(1)}천만원`;
    } else {
      return `${fmt.format(amount)}원`;
    }
  };

  const availableYears = useMemo(() => {
    const years = Array.from(new Set(donations.map((d) => d.year))).sort(
      (a, b) => b - a
    );
    return years;
  }, [donations]);

  if (loading) {
    return (
      <div
        style={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: COLORS.background,
        }}
      >
        <Loader2
          style={{ width: "48px", height: "48px", color: COLORS.accent }}
          className="animate-spin"
        />
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
            maxWidth: "1400px",
            margin: "0 auto",
            padding: "16px 32px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
            <Button
              variant="ghost"
              onClick={() => navigate("/")}
              style={{
                padding: "8px",
                borderRadius: "8px",
              }}
            >
              <ArrowLeft
                style={{ width: "20px", height: "20px", color: COLORS.primary }}
              />
            </Button>
            <div
              style={{
                padding: "10px",
                background: COLORS.accent,
                borderRadius: "12px",
              }}
            >
              <DollarSign
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
                }}
              >
                기부금 분석
              </h1>
              <p
                style={{
                  fontSize: "14px",
                  color: COLORS.secondary,
                  fontWeight: 500,
                }}
              >
                기업별 사회공헌 기부금 상세 내역
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
      </header>

      {/* 메인 컨텐츠 */}
      <main
        style={{
          maxWidth: "1400px",
          margin: "0 auto",
          padding: "32px",
        }}
      >
        {/* 필터 섹션 */}
        <div
          style={{
            marginBottom: "24px",
            display: "flex",
            gap: "16px",
            flexWrap: "wrap",
            alignItems: "flex-end",
          }}
        >
          {/* 회사 검색 드롭다운 */}
          <div
            style={{ flex: "1 1 400px", position: "relative" }}
            className="dropdown-container"
          >
            <label
              style={{
                display: "block",
                marginBottom: "8px",
                fontSize: "14px",
                fontWeight: 600,
                color: COLORS.primary,
              }}
            >
              <Building2
                style={{
                  width: "14px",
                  height: "14px",
                  display: "inline",
                  marginRight: "6px",
                  verticalAlign: "middle",
                }}
              />
              기업 선택
            </label>
            <div style={{ position: "relative" }}>
              <div
                onClick={() => setShowDropdown(!showDropdown)}
                style={{
                  padding: "10px 40px 10px 16px",
                  borderRadius: "8px",
                  border: `1px solid ${COLORS.border}`,
                  background: COLORS.cardBg,
                  cursor: "pointer",
                  fontSize: "14px",
                  fontWeight: 500,
                  color: selectedOrg ? COLORS.primary : COLORS.secondary,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  transition: "all 0.2s",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = COLORS.accent;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = COLORS.border;
                }}
              >
                <span
                  style={{
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {selectedOrg ? selectedOrg.name : "전체 기업"}
                </span>
                <ChevronDown
                  style={{
                    width: "18px",
                    height: "18px",
                    color: COLORS.secondary,
                    transition: "transform 0.2s",
                    transform: showDropdown ? "rotate(180deg)" : "rotate(0deg)",
                  }}
                />
              </div>

              {/* 드롭다운 메뉴 */}
              {showDropdown && (
                <div
                  style={{
                    position: "absolute",
                    top: "calc(100% + 4px)",
                    left: 0,
                    right: 0,
                    background: COLORS.cardBg,
                    border: `1px solid ${COLORS.border}`,
                    borderRadius: "8px",
                    boxShadow: "0 4px 20px rgba(0,0,0,0.1)",
                    zIndex: 100,
                    maxHeight: "400px",
                    overflow: "hidden",
                    display: "flex",
                    flexDirection: "column",
                  }}
                >
                  {/* 검색 입력 */}
                  <div
                    style={{
                      padding: "12px",
                      borderBottom: `1px solid ${COLORS.border}`,
                    }}
                  >
                    <div style={{ position: "relative" }}>
                      <Search
                        style={{
                          position: "absolute",
                          left: "12px",
                          top: "50%",
                          transform: "translateY(-50%)",
                          width: "16px",
                          height: "16px",
                          color: COLORS.secondary,
                        }}
                      />
                      <input
                        type="text"
                        placeholder="기업명 검색..."
                        value={searchTerm}
                        onChange={(e) => {
                          setSearchTerm(e.target.value);
                          setDropdownPage(1);
                        }}
                        onClick={(e) => e.stopPropagation()}
                        style={{
                          width: "100%",
                          padding: "8px 32px 8px 36px",
                          border: `1px solid ${COLORS.border}`,
                          borderRadius: "6px",
                          fontSize: "14px",
                          outline: "none",
                        }}
                      />
                      {searchTerm && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setSearchTerm("");
                            setDropdownPage(1);
                          }}
                          style={{
                            position: "absolute",
                            right: "8px",
                            top: "50%",
                            transform: "translateY(-50%)",
                            background: "transparent",
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
                  </div>

                  {/* 전체 보기 옵션 */}
                  <div
                    onClick={() => {
                      setSelectedOrg(null);
                      setShowDropdown(false);
                    }}
                    style={{
                      padding: "12px 16px",
                      cursor: "pointer",
                      fontSize: "14px",
                      fontWeight: 500,
                      color: !selectedOrg ? COLORS.accent : COLORS.primary,
                      background: !selectedOrg
                        ? `${COLORS.accent}10`
                        : "transparent",
                      borderBottom: `1px solid ${COLORS.border}`,
                      transition: "background 0.2s",
                    }}
                    onMouseEnter={(e) => {
                      if (selectedOrg)
                        e.currentTarget.style.background = COLORS.hover;
                    }}
                    onMouseLeave={(e) => {
                      if (selectedOrg)
                        e.currentTarget.style.background = "transparent";
                    }}
                  >
                    📊 전체 기업 ({organizations.length}개)
                  </div>

                  {/* 조직 리스트 */}
                  <div
                    style={{ flex: 1, overflow: "auto", maxHeight: "240px" }}
                  >
                    {paginatedOrgs.length > 0 ? (
                      paginatedOrgs.map((org) => (
                        <div
                          key={org.id}
                          onClick={() => {
                            setSelectedOrg(org);
                            setShowDropdown(false);
                            setSearchTerm("");
                          }}
                          style={{
                            padding: "12px 16px",
                            cursor: "pointer",
                            borderBottom: `1px solid ${COLORS.border}`,
                            background:
                              selectedOrg?.id === org.id
                                ? `${COLORS.accent}10`
                                : "transparent",
                            transition: "background 0.2s",
                          }}
                          onMouseEnter={(e) => {
                            if (selectedOrg?.id !== org.id) {
                              e.currentTarget.style.background = COLORS.hover;
                            }
                          }}
                          onMouseLeave={(e) => {
                            if (selectedOrg?.id !== org.id) {
                              e.currentTarget.style.background = "transparent";
                            }
                          }}
                        >
                          <div
                            style={{
                              display: "flex",
                              justifyContent: "space-between",
                              alignItems: "center",
                            }}
                          >
                            <div style={{ flex: 1, overflow: "hidden" }}>
                              <div
                                style={{
                                  fontSize: "14px",
                                  fontWeight: 600,
                                  color:
                                    selectedOrg?.id === org.id
                                      ? COLORS.accent
                                      : COLORS.primary,
                                  overflow: "hidden",
                                  textOverflow: "ellipsis",
                                  whiteSpace: "nowrap",
                                }}
                              >
                                {org.name}
                              </div>
                              <div
                                style={{
                                  fontSize: "12px",
                                  color: COLORS.secondary,
                                  marginTop: "2px",
                                }}
                              >
                                {org.donationCount}건 ·{" "}
                                {formatAmount(org.totalDonations!)}
                              </div>
                            </div>
                            {selectedOrg?.id === org.id && (
                              <div
                                style={{
                                  width: "20px",
                                  height: "20px",
                                  borderRadius: "50%",
                                  background: COLORS.accent,
                                  display: "flex",
                                  alignItems: "center",
                                  justifyContent: "center",
                                  marginLeft: "12px",
                                }}
                              >
                                <svg
                                  width="12"
                                  height="12"
                                  viewBox="0 0 12 12"
                                  fill="none"
                                >
                                  <path
                                    d="M10 3L4.5 8.5L2 6"
                                    stroke="white"
                                    strokeWidth="2"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  />
                                </svg>
                              </div>
                            )}
                          </div>
                        </div>
                      ))
                    ) : (
                      <div
                        style={{
                          padding: "32px 16px",
                          textAlign: "center",
                          color: COLORS.secondary,
                        }}
                      >
                        <AlertCircle
                          style={{
                            width: "32px",
                            height: "32px",
                            margin: "0 auto 8px",
                            color: COLORS.secondary,
                          }}
                        />
                        <div style={{ fontSize: "14px" }}>
                          검색 결과가 없습니다
                        </div>
                      </div>
                    )}
                  </div>

                  {/* 페이지네이션 */}
                  {totalPages > 1 && (
                    <div
                      style={{
                        padding: "12px 16px",
                        borderTop: `1px solid ${COLORS.border}`,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        background: "#F8FAFC",
                      }}
                    >
                      <Button
                        onClick={(e) => {
                          e.stopPropagation();
                          setDropdownPage(Math.max(1, dropdownPage - 1));
                        }}
                        disabled={dropdownPage === 1}
                        variant="outline"
                        size="sm"
                        style={{
                          padding: "6px 12px",
                          fontSize: "12px",
                          border: `1px solid ${COLORS.border}`,
                          background: "white",
                          color: COLORS.primary,
                          cursor:
                            dropdownPage === 1 ? "not-allowed" : "pointer",
                          opacity: dropdownPage === 1 ? 0.5 : 1,
                        }}
                      >
                        <ChevronLeft
                          style={{ width: "14px", height: "14px" }}
                        />
                      </Button>
                      <span
                        style={{ fontSize: "13px", color: COLORS.secondary }}
                      >
                        {dropdownPage} / {totalPages}
                      </span>
                      <Button
                        onClick={(e) => {
                          e.stopPropagation();
                          setDropdownPage(
                            Math.min(totalPages, dropdownPage + 1)
                          );
                        }}
                        disabled={dropdownPage === totalPages}
                        variant="outline"
                        size="sm"
                        style={{
                          padding: "6px 12px",
                          fontSize: "12px",
                          border: `1px solid ${COLORS.border}`,
                          background: "white",
                          color: COLORS.primary,
                          cursor:
                            dropdownPage === totalPages
                              ? "not-allowed"
                              : "pointer",
                          opacity: dropdownPage === totalPages ? 0.5 : 1,
                        }}
                      >
                        <ChevronRight
                          style={{ width: "14px", height: "14px" }}
                        />
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* 연도 필터 */}
          <div style={{ flex: "0 1 200px" }}>
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
                  verticalAlign: "middle",
                }}
              />
              연도 선택
            </label>
            <select
              value={selectedYear}
              onChange={(e) => setSelectedYear(e.target.value)}
              style={{
                width: "100%",
                padding: "10px 12px",
                borderRadius: "8px",
                border: `1px solid ${COLORS.border}`,
                fontSize: "14px",
                fontWeight: 500,
                color: COLORS.primary,
                background: COLORS.cardBg,
                cursor: "pointer",
                outline: "none",
              }}
            >
              <option value="all">전체 연도</option>
              {availableYears.map((year) => (
                <option key={year} value={year}>
                  {year}년
                </option>
              ))}
            </select>
          </div>

          {/* 필터 초기화 버튼 */}
          {(selectedOrg || selectedYear !== "all") && (
            <Button
              onClick={() => {
                setSelectedOrg(null);
                setSelectedYear("all");
                setSearchTerm("");
              }}
              variant="outline"
              style={{
                padding: "10px 16px",
                fontSize: "14px",
                fontWeight: 600,
                border: `1px solid ${COLORS.border}`,
                background: "white",
                color: COLORS.primary,
                cursor: "pointer",
                borderRadius: "8px",
                display: "flex",
                alignItems: "center",
                gap: "6px",
              }}
            >
              <X style={{ width: "16px", height: "16px" }} />
              필터 초기화
            </Button>
          )}
        </div>

        {/* 선택된 필터 표시 */}
        {(selectedOrg || selectedYear !== "all") && (
          <div
            style={{
              marginBottom: "20px",
              padding: "14px 18px",
              background: "#EEF2FF",
              borderRadius: "10px",
              border: "1px solid #C7D2FE",
              display: "flex",
              alignItems: "center",
              gap: "12px",
            }}
          >
            <Activity
              style={{ width: "18px", height: "18px", color: COLORS.accent }}
            />
            <div style={{ flex: 1 }}>
              <span
                style={{
                  fontSize: "14px",
                  fontWeight: 600,
                  color: COLORS.primary,
                }}
              >
                적용된 필터:{" "}
              </span>
              <span style={{ fontSize: "14px", color: COLORS.secondary }}>
                {selectedOrg && `${selectedOrg.name}`}
                {selectedOrg && selectedYear !== "all" && " · "}
                {selectedYear !== "all" && `${selectedYear}년`}
              </span>
            </div>
            <Badge
              style={{
                background: COLORS.accent,
                color: "white",
                padding: "4px 10px",
                fontSize: "13px",
                fontWeight: 600,
              }}
            >
              {filteredDonations.length}건
            </Badge>
          </div>
        )}

        {/* 통계 카드 */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
            gap: "20px",
            marginBottom: "32px",
          }}
        >
          {/* 총 기부금 */}
          <Card
            style={{
              borderRadius: "12px",
              boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
              border: `1px solid ${COLORS.border}`,
              background: COLORS.cardBg,
            }}
          >
            <CardContent style={{ padding: "20px" }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  marginBottom: "12px",
                }}
              >
                <div
                  style={{
                    padding: "10px",
                    background: `${COLORS.accent}15`,
                    borderRadius: "10px",
                  }}
                >
                  <TrendingUp
                    style={{
                      width: "20px",
                      height: "20px",
                      color: COLORS.accent,
                    }}
                  />
                </div>
                <span
                  style={{
                    fontSize: "13px",
                    fontWeight: 600,
                    color: COLORS.secondary,
                  }}
                >
                  총 기부금
                </span>
              </div>
              <div
                style={{
                  fontSize: "28px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "4px",
                }}
              >
                {formatAmount(statistics.total)}
              </div>
              <p style={{ fontSize: "12px", color: COLORS.secondary }}>
                {statistics.count}건의 기부 기록
              </p>
            </CardContent>
          </Card>

          {/* 참여 기업 수 */}
          <Card
            style={{
              borderRadius: "12px",
              boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
              border: `1px solid ${COLORS.border}`,
              background: COLORS.cardBg,
            }}
          >
            <CardContent style={{ padding: "20px" }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  marginBottom: "12px",
                }}
              >
                <div
                  style={{
                    padding: "10px",
                    background: `${COLORS.success}15`,
                    borderRadius: "10px",
                  }}
                >
                  <Building2
                    style={{
                      width: "20px",
                      height: "20px",
                      color: COLORS.success,
                    }}
                  />
                </div>
                <span
                  style={{
                    fontSize: "13px",
                    fontWeight: 600,
                    color: COLORS.secondary,
                  }}
                >
                  참여 기업
                </span>
              </div>
              <div
                style={{
                  fontSize: "28px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "4px",
                }}
              >
                {statistics.uniqueCompanies}
              </div>
              <p style={{ fontSize: "12px", color: COLORS.secondary }}>
                개 기업 참여
              </p>
            </CardContent>
          </Card>

          {/* 평균 기부금 */}
          <Card
            style={{
              borderRadius: "12px",
              boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
              border: `1px solid ${COLORS.border}`,
              background: COLORS.cardBg,
            }}
          >
            <CardContent style={{ padding: "20px" }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  marginBottom: "12px",
                }}
              >
                <div
                  style={{
                    padding: "10px",
                    background: `${COLORS.accent}15`,
                    borderRadius: "10px",
                  }}
                >
                  <Activity
                    style={{
                      width: "20px",
                      height: "20px",
                      color: COLORS.accent,
                    }}
                  />
                </div>
                <span
                  style={{
                    fontSize: "13px",
                    fontWeight: 600,
                    color: COLORS.secondary,
                  }}
                >
                  기업당 평균
                </span>
              </div>
              <div
                style={{
                  fontSize: "28px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "4px",
                }}
              >
                {formatAmount(statistics.avgPerCompany)}
              </div>
              <p style={{ fontSize: "12px", color: COLORS.secondary }}>
                평균 기부금액
              </p>
            </CardContent>
          </Card>

          {/* 최다 기부 기업 */}
          <Card
            style={{
              borderRadius: "12px",
              boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
              border: `1px solid ${COLORS.border}`,
              background: COLORS.cardBg,
            }}
          >
            <CardContent style={{ padding: "20px" }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  marginBottom: "12px",
                }}
              >
                <div
                  style={{
                    padding: "10px",
                    background: `${COLORS.warning}15`,
                    borderRadius: "10px",
                  }}
                >
                  <DollarSign
                    style={{
                      width: "20px",
                      height: "20px",
                      color: COLORS.warning,
                    }}
                  />
                </div>
                <span
                  style={{
                    fontSize: "13px",
                    fontWeight: 600,
                    color: COLORS.secondary,
                  }}
                >
                  최다 기부 기업
                </span>
              </div>
              <div
                style={{
                  fontSize: "16px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "4px",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {statistics.topDonor}
              </div>
              <p style={{ fontSize: "12px", color: COLORS.secondary }}>
                {formatAmount(statistics.topAmount)}
              </p>
            </CardContent>
          </Card>
        </div>

        {/* 차트 섹션 */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns:
              trendData.length > 0 && topCompanies.length > 0
                ? "1fr 1fr"
                : "1fr",
            gap: "24px",
            marginBottom: "24px",
          }}
        >
          {/* 연도별 트렌드 차트 */}
          {trendData.length > 0 && (
            <Card
              style={{
                borderRadius: "12px",
                boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
                border: `1px solid ${COLORS.border}`,
                background: COLORS.cardBg,
              }}
            >
              <CardHeader style={{ padding: "20px", paddingBottom: "12px" }}>
                <CardTitle
                  style={{
                    fontSize: "16px",
                    fontWeight: 700,
                    color: COLORS.primary,
                  }}
                >
                  {selectedOrg
                    ? `${selectedOrg.name} 연도별 추이`
                    : "연도별 기부금 추이"}
                </CardTitle>
              </CardHeader>
              <CardContent style={{ padding: "20px", paddingTop: "0" }}>
                <ResponsiveContainer width="100%" height={280}>
                  <LineChart data={trendData}>
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke={COLORS.border}
                    />
                    <XAxis
                      dataKey="year"
                      style={{ fontSize: "12px", fill: COLORS.secondary }}
                    />
                    <YAxis
                      style={{ fontSize: "12px", fill: COLORS.secondary }}
                      label={{
                        value: "억원",
                        angle: -90,
                        position: "insideLeft",
                        style: { fill: COLORS.secondary, fontSize: "12px" },
                      }}
                    />
                    <Tooltip
                      contentStyle={{
                        background: COLORS.cardBg,
                        border: `1px solid ${COLORS.border}`,
                        borderRadius: "8px",
                        fontSize: "13px",
                      }}
                    />
                    <Line
                      type="monotone"
                      dataKey="total"
                      stroke={COLORS.accent}
                      strokeWidth={3}
                      dot={{ fill: COLORS.accent, r: 5 }}
                      activeDot={{ r: 7 }}
                      name="기부금 (억원)"
                    />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          )}

          {/* 상위 기업 차트 */}
          {topCompanies.length > 0 && (
            <Card
              style={{
                borderRadius: "12px",
                boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
                border: `1px solid ${COLORS.border}`,
                background: COLORS.cardBg,
              }}
            >
              <CardHeader style={{ padding: "20px", paddingBottom: "12px" }}>
                <CardTitle
                  style={{
                    fontSize: "16px",
                    fontWeight: 700,
                    color: COLORS.primary,
                  }}
                >
                  상위 10개 기부 기업
                </CardTitle>
              </CardHeader>
              <CardContent style={{ padding: "20px", paddingTop: "0" }}>
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart
                    data={topCompanies}
                    layout="vertical"
                    margin={{ left: 20, right: 20 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke={COLORS.border}
                    />
                    <XAxis
                      type="number"
                      style={{ fontSize: "11px", fill: COLORS.secondary }}
                    />
                    <YAxis
                      type="category"
                      dataKey="name"
                      width={80}
                      style={{
                        fontSize: "10px",
                        fill: COLORS.secondary,
                      }}
                      tick={(props) => {
                        const { x, y, payload } = props;
                        const maxLength = 10;
                        const text = payload.value;
                        const truncated =
                          text.length > maxLength
                            ? text.substring(0, maxLength) + "..."
                            : text;
                        return (
                          <text
                            x={x}
                            y={y}
                            textAnchor="end"
                            fill={COLORS.secondary}
                            fontSize="10px"
                            dy={4}
                          >
                            {truncated}
                          </text>
                        );
                      }}
                    />
                    <Tooltip
                      contentStyle={{
                        background: COLORS.cardBg,
                        border: `1px solid ${COLORS.border}`,
                        borderRadius: "8px",
                        fontSize: "12px",
                      }}
                    />
                    <Bar
                      dataKey="amount"
                      fill={COLORS.accent}
                      name="기부금 (억원)"
                      radius={[0, 6, 6, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          )}
        </div>

        {/* 상세 데이터 테이블 */}
        <Card
          style={{
            borderRadius: "12px",
            boxShadow: "0 1px 3px rgba(15, 23, 42, 0.08)",
            border: `1px solid ${COLORS.border}`,
            background: COLORS.cardBg,
          }}
        >
          <CardHeader style={{ padding: "20px" }}>
            <CardTitle
              style={{
                fontSize: "16px",
                fontWeight: 700,
                color: COLORS.primary,
              }}
            >
              상세 기부금 내역
            </CardTitle>
          </CardHeader>
          <CardContent style={{ padding: "20px", paddingTop: "0" }}>
            {filteredDonations.length === 0 ? (
              <div
                style={{
                  textAlign: "center",
                  padding: "50px 0",
                  color: COLORS.secondary,
                }}
              >
                <AlertCircle
                  style={{
                    width: "48px",
                    height: "48px",
                    margin: "0 auto 16px auto",
                    color: COLORS.secondary,
                  }}
                />
                <p style={{ fontSize: "14px", fontWeight: 600 }}>
                  선택한 조건에 해당하는 기부금 데이터가 없습니다
                </p>
              </div>
            ) : (
              <div
                style={{
                  overflowX: "auto",
                  borderRadius: "8px",
                  border: `1px solid ${COLORS.border}`,
                }}
              >
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr
                      style={{
                        borderBottom: `2px solid ${COLORS.border}`,
                        background: "#F8FAFC",
                      }}
                    >
                      <th
                        style={{
                          padding: "14px 18px",
                          textAlign: "left",
                          fontSize: "13px",
                          fontWeight: 700,
                          color: COLORS.primary,
                        }}
                      >
                        조직명
                      </th>
                      <th
                        style={{
                          padding: "14px 18px",
                          textAlign: "center",
                          fontSize: "13px",
                          fontWeight: 700,
                          color: COLORS.primary,
                        }}
                      >
                        연도
                      </th>
                      <th
                        style={{
                          padding: "14px 18px",
                          textAlign: "center",
                          fontSize: "13px",
                          fontWeight: 700,
                          color: COLORS.primary,
                        }}
                      >
                        분기
                      </th>
                      <th
                        style={{
                          padding: "14px 18px",
                          textAlign: "right",
                          fontSize: "13px",
                          fontWeight: 700,
                          color: COLORS.primary,
                        }}
                      >
                        기부금액
                      </th>
                      <th
                        style={{
                          padding: "14px 18px",
                          textAlign: "center",
                          fontSize: "13px",
                          fontWeight: 700,
                          color: COLORS.primary,
                        }}
                      >
                        보고서
                      </th>
                      <th
                        style={{
                          padding: "14px 18px",
                          textAlign: "center",
                          fontSize: "13px",
                          fontWeight: 700,
                          color: COLORS.primary,
                        }}
                      >
                        검증상태
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredDonations.map((donation, idx) => (
                      <tr
                        key={donation.id}
                        style={{
                          borderBottom: `1px solid ${COLORS.border}`,
                          transition: "background 0.2s",
                        }}
                        onMouseEnter={(e) =>
                          (e.currentTarget.style.background = "#F8FAFC")
                        }
                        onMouseLeave={(e) =>
                          (e.currentTarget.style.background = "transparent")
                        }
                      >
                        <td
                          style={{
                            padding: "14px 18px",
                            fontSize: "13px",
                            fontWeight: 600,
                            color: COLORS.primary,
                          }}
                        >
                          {donation.organizationName}
                        </td>
                        <td
                          style={{
                            padding: "14px 18px",
                            textAlign: "center",
                            fontSize: "13px",
                            color: COLORS.secondary,
                          }}
                        >
                          {donation.year}
                        </td>
                        <td
                          style={{
                            padding: "14px 18px",
                            textAlign: "center",
                            fontSize: "13px",
                            color: COLORS.secondary,
                          }}
                        >
                          {donation.quarter}Q
                        </td>
                        <td
                          style={{
                            padding: "14px 18px",
                            textAlign: "right",
                            fontSize: "14px",
                            fontWeight: 600,
                            color: COLORS.accent,
                          }}
                        >
                          {formatAmount(donation.donationAmount)}
                        </td>
                        <td
                          style={{
                            padding: "14px 18px",
                            textAlign: "center",
                            fontSize: "12px",
                            color: COLORS.secondary,
                          }}
                        >
                          {donation.reportType}
                        </td>
                        <td
                          style={{
                            padding: "14px 18px",
                            textAlign: "center",
                          }}
                        >
                          <Badge
                            variant="outline"
                            style={{
                              background:
                                donation.verificationStatus === "자동수집"
                                  ? "#EEF2FF"
                                  : "#DCFCE7",
                              color:
                                donation.verificationStatus === "자동수집"
                                  ? COLORS.accent
                                  : COLORS.success,
                              border: "none",
                              fontSize: "11px",
                              fontWeight: 600,
                              padding: "4px 10px",
                            }}
                          >
                            {donation.verificationStatus}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
