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
  Heart,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Download
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
import './DetailPage.css';

const fmt = new Intl.NumberFormat("ko-KR");

// 네이비 & 블랙 모던 컨셉 팔레트
const COLORS = {
  primary: '#1a1a2e',      // 네이비
  secondary: '#666666',    // 그레이
  accent: '#121212',       // 모던 블랙
  background: '#ffffff',
  border: '#eeeeee',
  success: '#22c55e',
  warning: '#f59e0b',
};

export default function DonationsDetail() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [donations, setDonations] = useState([]);
  const [allOrganizations, setAllOrganizations] = useState([]);

  const [selectedYear, setSelectedYear] = useState("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedOrg, setSelectedOrg] = useState(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const [dropdownPage, setDropdownPage] = useState(1);

  const ORGS_PER_PAGE = 8;

  useEffect(() => {
    initData();
  }, []);

  const initData = async () => {
  setLoading(true);
  try {
    // 병렬 API 호출로 성능 최적화 (2000개 로드 → 첫 페이지만)
    const [orgsWithDonationsRes, donationRes] = await Promise.all([
      fetch(`${API_BASE}/api/organizations/with-data/donations`),
      fetch(`${API_BASE}/api/donations?size=50`)  // 첫 페이지만 로드
    ]);

    // 기부금 데이터가 있는 조직만 로드 (경량화)
    if (orgsWithDonationsRes.ok) {
      const orgsWithDonations = await orgsWithDonationsRes.json();
      setAllOrganizations(orgsWithDonations);
    }

    // 첫 페이지 기부금 데이터
    if (donationRes.ok) {
      const data = await donationRes.json();
      const normalized = data.map(item => ({
        ...item,
        donationAmount: Number(item.donationAmount || 0),
        organizationId: item.organizationId || item.organization?.id,
        organizationName: item.organizationName || item.organization?.name,
        verificationStatus: item.verificationStatus === "DART_AUTO" ? "자동 수집" : (item.verificationStatus || "검증 완료")
      }));
      setDonations(normalized);
      setIsApiConnected(true);
    }
  } catch (error) {
    console.error('❌ Donation initialization failed:', error);
  } finally {
    setLoading(false);
  }
};

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (!e.target.closest(".org-dropdown-container")) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("click", handleClickOutside)
    return () => document.removeEventListener("click", handleClickOutside);
  }, []);

  // 데이터가 있는 조직만 필터링
  const organizationsWithData = useMemo(() => {
    const orgIdsWithData = new Set(donations.map(d => d.organizationId));
    return allOrganizations.filter(org => orgIdsWithData.has(org.id));
  }, [allOrganizations, donations]);

  const filteredDropdownOrgs = useMemo(() => {
    const list = searchTerm 
      ? organizationsWithData.filter(org => org.name?.toLowerCase().includes(searchTerm.toLowerCase()))
      : organizationsWithData;
    return list;
  }, [organizationsWithData, searchTerm]);

  const paginatedOrgs = useMemo(() => {
    const start = (dropdownPage - 1) * ORGS_PER_PAGE;
    return filteredDropdownOrgs.slice(start, start + ORGS_PER_PAGE);
  }, [filteredDropdownOrgs, dropdownPage]);

  const totalPages = Math.ceil(filteredDropdownOrgs.length / ORGS_PER_PAGE);

  const filteredDonations = useMemo(() => {
    return donations.filter(d => {
      const matchYear = selectedYear === "all" || d.year === parseInt(selectedYear);
      const matchOrg = !selectedOrg || d.organizationId === selectedOrg.id;
      return matchYear && matchOrg;
    });
  }, [donations, selectedYear, selectedOrg]);

  const statistics = useMemo(() => {
    const total = filteredDonations.reduce((sum, d) => sum + d.donationAmount, 0);
    const uniqueCos = new Set(filteredDonations.map(d => d.organizationId)).size;
    const avg = uniqueCos > 0 ? total / uniqueCos : 0;
    
    // 연도별 증감 (선택된 조직이 있을 때)
    let trend = 0;
    if (selectedOrg) {
      const years = [...new Set(filteredDonations.map(d => d.year))].sort((a,b) => b-a);
      const latest = filteredDonations.filter(d => d.year === years[0]).reduce((s,d) => s+d.donationAmount, 0);
      const prev = filteredDonations.filter(d => d.year === years[1]).reduce((s,d) => s+d.donationAmount, 0);
      trend = prev > 0 ? Math.round(((latest - prev) / prev) * 100) : 0;
    }

    return { total, uniqueCos, avg, trend };
  }, [filteredDonations, selectedOrg]);

  const trendData = useMemo(() => {
    const map = new Map();
    filteredDonations.forEach(d => {
      const val = map.get(d.year) || 0;
      map.set(d.year, val + d.donationAmount);
    });
    return Array.from(map.entries())
      .map(([year, total]) => ({ 
        year: `${year}년`, 
        total: Math.round(total / 100000000 * 10) / 10 // 억원 단위
      }))
      .sort((a,b) => a.year.localeCompare(b.year));
  }, [filteredDonations]);

  const handleReset = () => {
    setSelectedOrg(null);
    setSelectedYear("all");
    setSearchTerm("");
    setDropdownPage(1);
    setShowDropdown(false);
  };

  const formatAmount = (amount) => {
    if (amount >= 100000000) return `${(amount / 100000000).toFixed(1)}억원`;
    if (amount >= 10000) return `${(amount / 10000).toFixed(1)}만원`;
    return `${fmt.format(amount)}원`;
  };

  const availableYears = useMemo(() => {
    return [...new Set(donations.map(d => d.year))].sort((a,b) => b-a);
  }, [donations]);

  if (loading) {
    return (
      <div className="loading-container">
        <div className="spinner"></div>
        <p style={{ color: '#64748b', fontWeight: 600 }}>기부 실적 조회 중...</p>
      </div>
    );
  }

  return (
    <div className="detail-page">
      {/* 프리미엄 헤더 */}
      <header className="detail-header">
        <div className="header-inner">
          <button className="back-btn" onClick={() => navigate('/')} title="뒤로가기">
            <ArrowLeft size={22} color="#ffffff" />
          </button>
          <div className="header-icon-container">
            <Heart />
          </div>
          <div className="header-content">
            <h1 className="header-title">기부금 현황 분석</h1>
            <p className="header-subtitle">기업별 사회공헌 기부금 지출 트래킹 및 실적 분석</p>
          </div>
          <div className="header-actions">
            <button className="export-btn glass" onClick={() => {}}>
              <Download size={15} />
              엑셀 다운로드
            </button>
          </div>
        </div>
      </header>

      <main className="detail-main">
        {/* 필터 섹션 */}
        <section className="filter-card">
          <div className="filter-title">데이터 필터링</div>
          <div className="filter-grid">
            {/* 기업 검색 (데이터 보유 기업만) */}
            <div className="filter-group org-dropdown-container" style={{ position: 'relative' }}>
              <label className="filter-label">기부 실적 기업</label>
              <div style={{ position: 'relative' }}>
                <Search size={16} style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
                <input
                  className="filter-input"
                  placeholder="기업명 검색..."
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value);
                    setShowDropdown(true);
                  }}
                  onFocus={() => setShowDropdown(true)}
                  style={{ paddingLeft: '44px', width: '100%' }}
                />
              </div>
              
              {showDropdown && (
                <div className="org-dropdown">
                  {paginatedOrgs.length > 0 ? (
                    <>
                      {paginatedOrgs.map(org => (
                        <button 
                          key={org.id} 
                          className="dropdown-item"
                          onClick={() => {
                            setSelectedOrg(org);
                            setSearchTerm(org.name);
                            setShowDropdown(false);
                            setSelectedYear("all"); // 기업 선택 시 연도 초기화하여 해당 기업의 전체 데이터를 우선 보여줌
                            setDropdownPage(1);
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <span>{org.name}</span>
                          </div>
                        </button>
                      ))}
                      {totalPages > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', padding: '10px', borderTop: '1px solid #f1f5f9' }}>
                          <Button variant="ghost" size="sm" onClick={() => setDropdownPage(p => Math.max(1, p - 1))} disabled={dropdownPage === 1}>이전</Button>
                          <span style={{ fontSize: '12px', alignSelf: 'center' }}>{dropdownPage} / {totalPages}</span>
                          <Button variant="ghost" size="sm" onClick={() => setDropdownPage(p => Math.min(totalPages, p + 1))} disabled={dropdownPage === totalPages}>다음</Button>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="empty-state" style={{ padding: '20px' }}>검색 결과가 없습니다.</div>
                  )}
                </div>
              )}
            </div>

            {/* 연도 필터 */}
            <div className="filter-group">
              <label className="filter-label">기준 연도</label>
              <select 
                className="filter-select"
                value={selectedYear}
                onChange={(e) => setSelectedYear(e.target.value)}
              >
                <option value="all">전체 연도</option>
                {availableYears.map(y => <option key={y} value={y}>{y}년</option>)}
              </select>
            </div>

            <button className="reset-btn" onClick={handleReset}>
              <Activity size={16} />
              필터 초기화
            </button>
          </div>
        </section>

        {/* 주요 지표 */}
        <section className="stats-grid">
          <div className="stat-card">
            <div className="stat-label">
              <DollarSign size={16} color="#3b82f6" />
              총 기부 금액
            </div>
            <div className="stat-value">
              {formatAmount(statistics.total)}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">
              <Building2 size={16} color="#10b981" />
              공시 참여 기업
            </div>
            <div className="stat-value">
              {statistics.uniqueCos}
              <span className="stat-unit">개사</span>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">
              <TrendingUp size={16} color="#6366f1" />
              기업별 평균
            </div>
            <div className="stat-value">
              {formatAmount(statistics.avg)}
            </div>
          </div>
        </section>

        {/* 차트 영역 */}
        <section className="content-card">
          <div className="card-header">
            <h3 className="card-title">연도별 기부 실적 추이</h3>
          </div>
          <div className="card-content">
            <div style={{ height: '350px', width: '100%' }}>
              <ResponsiveContainer>
                <BarChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                  <XAxis dataKey="year" axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dy={10} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dx={-10} />
                  <Tooltip 
                    contentStyle={{ background: '#fff', borderRadius: '16px', border: '1px solid #e2e8f0', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }}
                    formatter={(val) => [`${val} 억원`, '기부금']}
                  />
                  <Bar dataKey="total" fill="#1e293b" radius={[8, 8, 0, 0]} barSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </section>

        {/* 상세 내역 테이블 */}
        <section className="content-card">
          <div className="card-header">
            <h3 className="card-title">상세 기부 내역</h3>
          </div>
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>기업명</th>
                  <th>연도</th>
                  <th>분기</th>
                  <th style={{ textAlign: 'right' }}>기부금액</th>
                  <th style={{ textAlign: 'center' }}>검증 상태</th>
                </tr>
              </thead>
              <tbody>
                {filteredDonations.length > 0 ? (
                  filteredDonations.map((d, idx) => (
                    <tr key={idx}>
                      <td style={{ fontWeight: 700, color: '#1e293b' }}>{d.organizationName}</td>
                      <td>{d.year}년</td>
                      <td>{d.quarter}Q</td>
                      <td style={{ textAlign: 'right', fontWeight: 600 }}>{fmt.format(d.donationAmount)}원</td>
                      <td style={{ textAlign: 'center' }}>
                        <span className={`status-badge ${d.verificationStatus?.includes('완료') || d.verificationStatus?.includes('DART') ? 'connected' : 'disconnected'}`}>
                          {d.verificationStatus}
                        </span>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="5" className="empty-state">조회된 데이터가 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
}
