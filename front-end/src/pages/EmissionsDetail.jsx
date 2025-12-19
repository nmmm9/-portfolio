import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Activity, Factory, ArrowLeft, Download, Search, Filter,
  TrendingUp, TrendingDown, Database, Loader2, Calendar,
  CheckCircle2, BarChart3, AlertCircle
} from 'lucide-react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts';
import './DetailPage.css';

const fmt = new Intl.NumberFormat('ko-KR');

// 네이비 & 블랙 모던 컨셉 팔레트
const COLORS = {
  primary: '#1a1a2e',      // 네이비 (홈페이지 히어로)
  secondary: '#666666',    // 그레이
  accent: '#121212',       // 모던 블랙
  background: '#ffffff',
  border: '#eeeeee',
  success: '#22c55e',
};

export default function EmissionsDetail() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
  const yearNow = new Date().getFullYear();
  
  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingEmissions, setLoadingEmissions] = useState(false);
  
  const [allOrganizations, setAllOrganizations] = useState([]);
  const [emissions, setEmissions] = useState([]);
  
  const [selectedOrg, setSelectedOrg] = useState('');
  const [range, setRange] = useState({ from: yearNow - 5, to: yearNow });
  const [searchTerm, setSearchTerm] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 8;

  useEffect(() => {
    initData();
  }, []);

  const initData = async () => {
  setLoading(true);
  try {
    // 병렬 API 호출로 성능 최적화 (2000개 로드 → 첫 페이지만)
    const [orgsWithEmissionsRes, emissionsRes] = await Promise.all([
      fetch(`${API_BASE}/api/organizations/with-data/emissions`),
      fetch(`${API_BASE}/api/emissions?size=50`)  // 첫 페이지만 로드
    ]);

    // 배출량 데이터가 있는 조직만 로드 (경량화)
    if (orgsWithEmissionsRes.ok) {
      const orgsWithEmissions = await orgsWithEmissionsRes.json();
      setAllOrganizations(orgsWithEmissions);
    }

    // 첫 페이지 배출량 데이터
    if (emissionsRes.ok) {
      const data = await emissionsRes.json();
      setEmissions(data);
      setIsApiConnected(true);
    }
  } catch (error) {
    console.error('❌ Data initialization failed:', error);
    setIsApiConnected(false);
  } finally {
    setLoading(false);
  }
};

  const loadEmissions = async () => {
    if (!isApiConnected) return;
    setLoadingEmissions(true);
    try {
      let url = `${API_BASE}/api/emissions?fromYear=${range.from}&toYear=${range.to}`;
      if (selectedOrg) url += `&orgId=${selectedOrg}`;
      
      const response = await fetch(url);
      if (response.ok) {
        const data = await response.json();
        setEmissions(data);
      }
    } catch (error) {
      console.error('❌ Emissions load failed:', error);
    } finally {
      setLoadingEmissions(false);
    }
  };

  useEffect(() => {
    if (isApiConnected && (selectedOrg || range.from)) {
      loadEmissions();
    }
  }, [selectedOrg, range]);

  const filteredEmissions = useMemo(() => {
    return emissions.filter(e => {
      const matchOrg = !selectedOrg || e.organizationId === Number(selectedOrg);
      const matchYear = e.year >= range.from && e.year <= range.to;
      return matchOrg && matchYear;
    });
  }, [emissions, selectedOrg, range]);

  // 실제 배출량 데이터가 있는 조직만 필터링
  const organizationsWithData = useMemo(() => {
    const orgIdsWithData = new Set(emissions.map(e => e.organizationId));
    return allOrganizations.filter(org => orgIdsWithData.has(org.id));
  }, [allOrganizations, emissions]);

  const filteredDropdownOrgs = useMemo(() => {
    const list = searchTerm 
      ? organizationsWithData.filter(org => org.name?.toLowerCase().includes(searchTerm.toLowerCase()))
      : organizationsWithData;
    return list;
  }, [organizationsWithData, searchTerm]);

  const paginatedOrgs = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage;
    return filteredDropdownOrgs.slice(start, start + itemsPerPage);
  }, [filteredDropdownOrgs, currentPage]);

  const totalPages = Math.ceil(filteredDropdownOrgs.length / itemsPerPage);

  const totals = useMemo(() => {
    const total = filteredEmissions.reduce((acc, e) => acc + (Number(e.totalEmissions) || 0), 0);
    const verifiedCnt = filteredEmissions.filter(e => 
      e.verificationStatus?.includes('완료') || e.verificationStatus?.includes('Verified')
    ).length;
    const rate = filteredEmissions.length > 0 ? Math.round((verifiedCnt / filteredEmissions.length) * 100) : 0;
    
    // 최근 2년 비교
    const years = [...new Set(filteredEmissions.map(e => e.year))].sort((a, b) => b - a);
    const latestYear = years[0];
    const prevYear = years[1];
    
    const latestTotal = filteredEmissions.filter(e => e.year === latestYear).reduce((acc, e) => acc + (e.totalEmissions || 0), 0);
    const prevTotal = filteredEmissions.filter(e => e.year === prevYear).reduce((acc, e) => acc + (e.totalEmissions || 0), 0);
    const trend = prevTotal > 0 ? Math.round(((latestTotal - prevTotal) / prevTotal) * 100) : 0;

    return { total, verifiedCnt, rate, trend, latestYear };
  }, [filteredEmissions]);

  const chartData = useMemo(() => {
    const map = new Map();
    filteredEmissions.forEach(e => {
      const val = map.get(e.year) || 0;
      map.set(e.year, val + (e.totalEmissions || 0));
    });
    return Array.from(map.entries())
      .map(([year, total]) => ({ year, total }))
      .sort((a, b) => a.year - b.year);
  }, [filteredEmissions]);

  const handleReset = () => {
    setSelectedOrg('');
    setSearchTerm('');
    setRange({ from: yearNow - 5, to: yearNow });
    setCurrentPage(1);
    setShowDropdown(false);
  };

  const exportToCSV = () => {
    const headers = ['조직명', '연도', '총배출량 (tCO₂e)', '검증상태'];
    const rows = filteredEmissions.map(e => [
      organizations.find(o => o.id === e.organizationId)?.name || '',
      e.year,
      e.totalEmissions,
      e.verificationStatus || '-'
    ]);
    const csv = [headers, ...rows].map(row => row.join(',')).join('\n');
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = '온실가스_배출량.csv';
    link.click();
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="spinner"></div>
        <p style={{ color: '#64748b', fontWeight: 600 }}>데이터 분석 중...</p>
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
            <Database />
          </div>
          <div className="header-content">
            <h1 className="header-title">온실가스 배출량 분석</h1>
            <p className="header-subtitle">탄소중립 이행 현황 및 기업별 배출 데이터 시각화</p>
          </div>
          <div className="header-actions">
            <button className="export-btn glass" onClick={exportToCSV}>
              <Download size={15} />
              보고서 추출
            </button>
          </div>
        </div>
      </header>

      <main className="detail-main">
        {/* 필터 및 검색 */}
        <section className="filter-card">
          <div className="filter-title">데이터 필터링</div>
          <div className="filter-grid">
            {/* 기업 검색 (데이터 보유 기업만) */}
            <div className="filter-group org-dropdown-container" style={{ position: 'relative' }}>
              <label className="filter-label">분석 대상 기업</label>
              <div style={{ position: 'relative' }}>
                <Search size={16} style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
                <input
                  className="filter-input"
                  placeholder="데이터 보유 기업 검색..."
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
                            setSelectedOrg(String(org.id));
                            setSearchTerm(org.name);
                            setShowDropdown(false);
                            setCurrentPage(1); // 페이지 초기화
                          }}
                        >
                          {org.name}
                        </button>
                      ))}
                      {totalPages > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', padding: '10px', borderTop: '1px solid #f1f5f9' }}>
                          <Button variant="ghost" size="sm" onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}>이전</Button>
                          <span style={{ fontSize: '12px', alignSelf: 'center' }}>{currentPage} / {totalPages}</span>
                          <Button variant="ghost" size="sm" onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}>다음</Button>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="empty-state" style={{ padding: '20px' }}>검색 결과가 없습니다.</div>
                  )}
                </div>
              )}
            </div>

            {/* 연도 범위 */}
            <div className="filter-group">
              <label className="filter-label">분석 기간 (연도)</label>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                <select className="filter-select" value={range.from} onChange={(e) => setRange(r => ({ ...r, from: Number(e.target.value) }))}>
                  {Array.from({ length: 15 }, (_, i) => yearNow - i).map(y => <option key={y} value={y}>{y}년</option>)}
                </select>
                <span style={{ color: '#cbd5e1' }}>~</span>
                <select className="filter-select" value={range.to} onChange={(e) => setRange(r => ({ ...r, to: Number(e.target.value) }))}>
                  {Array.from({ length: 15 }, (_, i) => yearNow - i).map(y => <option key={y} value={y}>{y}년</option>)}
                </select>
              </div>
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
              <TrendingUp size={16} color="#3b82f6" />
              누적 총 배출량
            </div>
            <div className="stat-value">
              {fmt.format(Math.round(totals.total))}
              <span className="stat-unit">tCO₂e</span>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">
              <CheckCircle2 size={16} color="#10b981" />
              데이터 검증률
            </div>
            <div className="stat-value">
              {totals.rate}
              <span className="stat-unit">%</span>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">
              <BarChart3 size={16} color="#6366f1" />
              전년 대비 증감
            </div>
            <div className="stat-value">
              {totals.trend > 0 ? '+' : ''}{totals.trend}
              <span className="stat-unit">%</span>
            </div>
          </div>
        </section>

        {/* 차트 영역 */}
        <section className="content-card">
          <div className="card-header">
            <h3 className="card-title">연도별 배출량 추이</h3>
          </div>
          <div className="card-content">
            <div style={{ height: '350px', width: '100%' }}>
              <ResponsiveContainer>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                  <XAxis dataKey="year" axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dy={10} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dx={-10} />
                  <Tooltip 
                    contentStyle={{ background: '#fff', borderRadius: '16px', border: '1px solid #e2e8f0', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }}
                  />
                  <Line 
                    type="monotone" 
                    dataKey="total" 
                    stroke="#1e293b" 
                    strokeWidth={4} 
                    dot={{ r: 6, fill: '#1e293b', strokeWidth: 2, stroke: '#fff' }}
                    activeDot={{ r: 8, strokeWidth: 0 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </section>

        {/* 상세 내역 테이블 */}
        <section className="content-card">
          <div className="card-header">
            <h3 className="card-title">배출량 상세 내역</h3>
          </div>
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>기업명</th>
                  <th>연도</th>
                  <th style={{ textAlign: 'right' }}>배출량 (tCO₂e)</th>
                  <th style={{ textAlign: 'center' }}>검증 상태</th>
                </tr>
              </thead>
              <tbody>
                {filteredEmissions.length > 0 ? (
                  filteredEmissions.map((e, idx) => (
                    <tr key={idx}>
                      <td style={{ fontWeight: 700, color: '#1e293b' }}>
                        {allOrganizations.find(org => org.id === e.organizationId)?.name || e.organizationName}
                      </td>
                      <td>{e.year}년</td>
                      <td style={{ textAlign: 'right', fontWeight: 600 }}>{fmt.format(e.totalEmissions)}</td>
                      <td style={{ textAlign: 'center' }}>
                        <span className={`status-badge ${e.verificationStatus?.includes('완료') ? 'connected' : 'disconnected'}`}>
                          {e.verificationStatus?.includes('완료') ? '검증 완료' : '검증 대기'}
                        </span>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="4" className="empty-state">조건에 맞는 데이터가 존재하지 않습니다.</td>
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
