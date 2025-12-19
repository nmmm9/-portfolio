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

// 카테고리 색상 (네이비 톤과 어울리게 조정)
const CATEGORY_COLORS = {
  환경: "#16a34a",
  기부: "#ca8a04",
  교육: "#9333ea",
  일자리: "#2563eb",
  지역사회: "#dc2626",
  윤리경영: "#4b5563",
  혁신: "#0891b2",
  전체: "#666666",
};

export default function PositiveNewsDetail() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
  const yearNow = new Date().getFullYear();

  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingNews, setLoadingNews] = useState(false);

  const [allOrganizations, setAllOrganizations] = useState([]);
  const [organizationsWithData, setOrganizationsWithData] = useState([]); // 뉴스 데이터 보유 기업
  const [newsData, setNewsData] = useState(null);
  const [yearStats, setYearStats] = useState([]);
  const [categoryStats, setCategoryStats] = useState([]);

  // ... (기존 변수들)
  const [selectedOrg, setSelectedOrg] = useState(null);
  const [selectedYear, setSelectedYear] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [currentPage, setCurrentPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);
  const [dropdownPage, setDropdownPage] = useState(1);
  const ORGS_PER_PAGE = 8;
  const pageSize = 10;

  useEffect(() => {
    initData();
  }, []);

  const initData = async () => {
    setLoading(true);
    try {
      // 병렬 API 호출로 성능 최적화 (2000개 로드 제거)
      const [orgsWithNewsRes, newsDataRes, yearStatsRes, catStatsRes] = await Promise.all([
        fetch(`${API_BASE}/api/organizations/with-data/news`),
        fetch(`${API_BASE}/api/positive-news?page=0&size=${pageSize}`),
        fetch(`${API_BASE}/api/positive-news/stats/by-year`),
        fetch(`${API_BASE}/api/positive-news/stats/by-category`)
      ]);

      // 뉴스 데이터가 있는 조직만 로드 (경량화)
      if (orgsWithNewsRes.ok) {
        const orgsWithNews = await orgsWithNewsRes.json();
        setOrganizationsWithData(orgsWithNews);
        setAllOrganizations(orgsWithNews);
      }

      // 첫 페이지 뉴스 데이터
      if (newsDataRes.ok) {
        const data = await newsDataRes.json();
        setNewsData(data);
      }

      // 통계 데이터
      if (yearStatsRes.ok) setYearStats(await yearStatsRes.json());
      if (catStatsRes.ok) setCategoryStats(await catStatsRes.json());
      
      setIsApiConnected(true);
    } catch (error) {
      console.error('❌ News initialization failed:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!loading) {
      loadNewsData(selectedOrg);
      loadStats(selectedOrg);
    }
  }, [selectedOrg, selectedYear, selectedCategory, currentPage]);

  const loadNewsData = async (org) => {
    setLoadingNews(true);
    try {
      let url = `${API_BASE}/api/positive-news${org ? `/organization/${org.id}` : ''}?page=${currentPage}&size=${pageSize}`;
      if (selectedYear) url += `&year=${selectedYear}`;
      if (selectedCategory && selectedCategory !== "all") url += `&category=${selectedCategory}`;

      const res = await fetch(url);
      if (res.ok) {
        const data = await res.json();
        setNewsData(data);
      }
    } catch (error) {
      console.error('❌ News load failed:', error);
    } finally {
      setLoadingNews(false);
    }
  };

  const loadStats = async (org) => {
    try {
      const baseUrl = `${API_BASE}/api/positive-news${org ? `/organization/${org.id}` : ''}/stats`;
      const [yearRes, catRes] = await Promise.all([
        fetch(`${baseUrl}/by-year`),
        fetch(`${baseUrl}/by-category`)
      ]);

      if (yearRes.ok) setYearStats(await yearRes.json());
      if (catRes.ok) setCategoryStats(await catRes.json());
    } catch (error) {
      console.error('❌ Stats load failed:', error);
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

  const filteredDropdownOrgs = useMemo(() => {
    // Fallback 제거: 데이터 있는 기업만 무조건 노출
    const dataSource = organizationsWithData; 
    return searchTerm 
      ? dataSource.filter(org => org.name?.toLowerCase().includes(searchTerm.toLowerCase()))
      : dataSource;
  }, [organizationsWithData, searchTerm]);

  const paginatedOrgs = useMemo(() => {
    const start = (dropdownPage - 1) * ORGS_PER_PAGE;
    return filteredDropdownOrgs.slice(start, start + ORGS_PER_PAGE);
  }, [filteredDropdownOrgs, dropdownPage]);

  const totalPages = Math.ceil(filteredDropdownOrgs.length / ORGS_PER_PAGE);

  const handleReset = () => {
    setSelectedOrg(null);
    setSelectedYear(null);
    setSelectedCategory("all");
    setSearchTerm("");
    setCurrentPage(0);
    setShowDropdown(false);
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="spinner"></div>
        <p style={{ color: '#64748b', fontWeight: 600 }}>긍정 소식 분석 중...</p>
      </div>
    );
  }

  const yearChartData = yearStats.map((stat) => ({
    year: stat.year,
    count: stat.count,
  }));

  const categoryChartData = categoryStats.map((stat) => ({
    name: stat.category,
    value: stat.count,
  }));

  return (
    <div className="detail-page">
      {/* 헤더 */}
      <header className="detail-header">
        <div className="header-inner">
          <button className="back-btn" onClick={() => navigate('/')} title="뒤로가기">
            <ArrowLeft size={22} color="#ffffff" />
          </button>
          <div className="header-icon-container">
            <Newspaper />
          </div>
          <div className="header-content">
            <h1 className="header-title">긍정 뉴스 분석</h1>
            <p className="header-subtitle">기업별 사회공헌 긍정 뉴스 트래킹 및 분석</p>
          </div>
          <div className="header-actions">
            <button className="export-btn glass" onClick={() => {}}>
              <Search size={14} />
              전체 뉴스 검색
            </button>
          </div>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="detail-main">
        {/* 필터 섹션 */}
        <section className="filter-card">
          <div className="filter-title">데이터 필터링</div>
          <div className="filter-grid" style={{ gridTemplateColumns: '1.5fr 1fr 1fr auto' }}>
            {/* 기업 검색 */}
            <div className="filter-group org-dropdown-container" style={{ position: 'relative' }}>
              <label className="filter-label">기업별 소식 찾기</label>
              <div style={{ position: 'relative' }}>
                <Search size={16} style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
                <input
                  className="filter-input"
                  placeholder={selectedOrg ? selectedOrg.name : "전체 기업 소식 보기..."}
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
                  <button className="dropdown-item" onClick={() => { setSelectedOrg(null); setShowDropdown(false); setSearchTerm(""); }}>
                    전체 보기
                  </button>
                  {paginatedOrgs.map(org => (
                    <button 
                      key={org.id} 
                      className="dropdown-item" 
                      onClick={() => { 
                        setSelectedOrg(org); 
                        setSearchTerm(org.name); 
                        setShowDropdown(false); 
                        setCurrentPage(0); // 페이지 리셋
                        setSelectedYear(null); // 연도 리셋
                        setSelectedCategory("all"); // 카테고리 리셋
                      }}
                    >
                      {org.name}
                    </button>
                  ))}
                  {totalPages > 1 && (
                    <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', padding: '10px', borderTop: '1px solid #f1f5f9' }}>
                      <Button variant="ghost" size="sm" onClick={() => setDropdownPage(p => Math.max(1, p - 1))} disabled={dropdownPage === 1}>이전</Button>
                      <span style={{ fontSize: '12px', alignSelf: 'center' }}>{dropdownPage} / {totalPages}</span>
                      <Button variant="ghost" size="sm" onClick={() => setDropdownPage(p => Math.min(totalPages, p + 1))} disabled={dropdownPage === totalPages}>다음</Button>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 연도 필터 */}
            <div className="filter-group">
              <label className="filter-label">연도</label>
              <select className="filter-select" value={selectedYear || ""} onChange={(e) => setSelectedYear(e.target.value ? Number(e.target.value) : null)}>
                <option value="">전체 연도</option>
                {yearStats.map(stat => (
                  <option key={stat.year} value={stat.year}>{stat.year}년 ({stat.count})</option>
                ))}
              </select>
            </div>

            {/* 카테고리 필터 */}
            <div className="filter-group">
              <label className="filter-label">카테고리</label>
              <select className="filter-select" value={selectedCategory} onChange={(e) => setSelectedCategory(e.target.value)}>
                <option value="all">전체 카테고리</option>
                {Object.keys(CATEGORY_COLORS).filter(c => c !== '전체').map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>

            <button className="reset-btn" onClick={handleReset} style={{ alignSelf: 'flex-end', height: '48px' }}>
              초기화
            </button>
          </div>
        </section>

          {/* 차트 섹션 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.5fr) minmax(0, 1fr)', gap: '24px' }}>
            <section className="content-card">
              <div className="card-header">
                <h3 className="card-title">연도별 뉴스 추이</h3>
              </div>
              <div className="card-content">
                {yearChartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <BarChart data={yearChartData}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                      <XAxis dataKey="year" axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: '#888' }} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: '#888' }} />
                      <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 10px 30px rgba(0,0,0,0.1)' }} />
                      <Bar dataKey="count" fill="#1a1a2e" radius={[4, 4, 0, 0]} barSize={40} />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="empty-state">데이터가 없습니다</div>
                )}
              </div>
            </section>

            <section className="content-card">
              <div className="card-header">
                <h3 className="card-title">카테고리별 분석</h3>
              </div>
              <div className="card-content">
                {categoryChartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <PieChart>
                      <Pie
                        data={categoryChartData}
                        cx="50%" cy="50%"
                        innerRadius={60}
                        outerRadius={100}
                        paddingAngle={5}
                        dataKey="value"
                      >
                        {categoryChartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={CATEGORY_COLORS[entry.name] || '#eee'} />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 10px 30px rgba(0,0,0,0.1)' }} />
                      <Legend iconType="circle" wrapperStyle={{ fontSize: '11px' }} />
                    </PieChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="empty-state">데이터가 없습니다</div>
                )}
              </div>
            </section>
          </div>

          {/* 뉴스 목록 섹션 */}
          <section className="content-card" style={{ border: 'none', background: 'transparent', boxShadow: 'none' }}>
            <div className="card-header" style={{ paddingLeft: 0, paddingRight: 0, marginBottom: '24px' }}>
              <h3 className="card-title" style={{ fontSize: '22px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Newspaper size={24} color="#1e293b" />
                뉴스 목록 
                {newsData && <span style={{ fontWeight: 400, color: '#64748b', fontSize: '16px' }}> ({fmt.format(newsData.totalElements)}건)</span>}
              </h3>
              {loadingNews && <div className="spinner-sm"></div>}
            </div>
            
            <div className="news-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(400px, 1fr))', gap: '24px' }}>
              {!newsData || newsData.content.length === 0 ? (
                <div className="empty-state" style={{ gridColumn: '1 / -1', padding: '100px', background: '#fff', borderRadius: '24px' }}>
                  <Newspaper size={48} color="#cbd5e1" style={{ marginBottom: '16px' }} />
                  <p style={{ color: '#64748b', fontWeight: 600 }}>조회된 뉴스가 없습니다.</p>
                </div>
              ) : (
                newsData.content.map((item) => (
                  <a
                    key={item.id}
                    href={item.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="news-card-premium"
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      padding: '30px',
                      background: '#fff',
                      borderRadius: '24px',
                      border: '1px solid #f1f5f9',
                      textDecoration: 'none',
                      transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                      position: 'relative',
                      boxShadow: '0 4px 6px -1px rgba(0,0,0,0.02), 0 2px 4px -1px rgba(0,0,0,0.01)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                      <span className="status-badge connected" style={{ 
                        background: CATEGORY_COLORS[item.category] + '20',
                        color: CATEGORY_COLORS[item.category],
                        border: `1px solid ${CATEGORY_COLORS[item.category]}40`,
                        padding: '4px 12px',
                        fontSize: '12px',
                        fontWeight: 700
                      }}>
                        {item.category}
                      </span>
                      <span style={{ fontSize: '13px', color: '#94a3b8', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Calendar size={14} />
                        {item.publishedDate}
                      </span>
                    </div>
                    
                    <h4 style={{ 
                      fontSize: '19px', 
                      fontWeight: 800, 
                      color: '#1e293b', 
                      marginBottom: '12px', 
                      lineHeight: 1.5,
                      letterSpacing: '-0.01em'
                    }}>
                      {item.title}
                    </h4>
                    
                    <p style={{ 
                      fontSize: '15px', 
                      color: '#64748b', 
                      lineHeight: 1.7, 
                      marginBottom: '20px', 
                      display: '-webkit-box', 
                      WebkitLineClamp: 3, 
                      WebkitBoxOrient: 'vertical', 
                      overflow: 'hidden',
                      flex: 1
                    }}>
                      {item.description}
                    </p>
                    
                    <div style={{ 
                      display: 'flex', 
                      justifyContent: 'space-between', 
                      alignItems: 'center',
                      paddingTop: '20px',
                      borderTop: '1px solid #f1f5f9'
                    }}>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        {item.matchedKeywords?.split(',').slice(0, 2).map(kw => (
                          <span key={kw} style={{ fontSize: '12px', color: '#475569', background: '#f8fafc', padding: '2px 8px', borderRadius: '4px' }}>
                            #{kw.trim()}
                          </span>
                        ))}
                      </div>
                      <div style={{ width: '32px', height: '32px', borderRadius: '10px', background: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#1e293b' }}>
                        <ExternalLink size={14} />
                      </div>
                    </div>
                  </a>
                ))
              )}
            </div>

            {/* 페이지네이션 (프리미엄 스타일) */}
            {newsData && newsData.totalPages > 1 && (
              <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '16px', marginTop: '60px' }}>
                <button 
                  className="back-btn"
                  onClick={() => setCurrentPage(p => Math.max(0, p - 1))}
                  disabled={currentPage === 0}
                  style={{ width: '44px', height: '44px', background: currentPage === 0 ? '#f1f5f9' : '#fff', borderColor: '#e2e8f0', color: '#1e293b' }}
                >
                  <ChevronLeft size={20} />
                </button>
                
                <div style={{ display: 'flex', gap: '8px' }}>
                  {Array.from({ length: Math.min(5, newsData.totalPages) }, (_, i) => {
                    const pageNum = i; // 간단히 0~4
                    return (
                      <button 
                        key={i}
                        onClick={() => setCurrentPage(pageNum)}
                        style={{
                          width: '44px',
                          height: '44px',
                          borderRadius: '12px',
                          border: '1px solid',
                          borderColor: currentPage === pageNum ? '#1e293b' : '#e2e8f0',
                          background: currentPage === pageNum ? '#1e293b' : '#fff',
                          color: currentPage === pageNum ? '#fff' : '#64748b',
                          fontWeight: 700,
                          cursor: 'pointer'
                        }}
                      >
                        {pageNum + 1}
                      </button>
                    );
                  })}
                </div>

                <button 
                  className="back-btn"
                  onClick={() => setCurrentPage(p => Math.min(newsData.totalPages - 1, p + 1))}
                  disabled={currentPage === newsData.totalPages - 1}
                  style={{ width: '44px', height: '44px', background: currentPage === newsData.totalPages - 1 ? '#f1f5f9' : '#fff', borderColor: '#e2e8f0', color: '#1e293b' }}
                >
                  <ChevronRight size={20} />
                </button>
              </div>
            )}
          </section>
      </main>

      <footer style={{ padding: '64px 0', textAlign: 'center', borderTop: '1px solid #eee', marginTop: '64px' }}>
        <p style={{ fontSize: '13px', color: '#888' }}>© 2024 Social Impact Tracker. All rights reserved.</p>
      </footer>
    </div>
  );
}
