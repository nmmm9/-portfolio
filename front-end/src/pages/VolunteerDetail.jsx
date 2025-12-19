import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { 
  Clock, 
  Download, 
  Database, 
  ArrowLeft,
  TrendingUp,
  TrendingDown,
  Users,
  Target,
  Award
} from 'lucide-react';
import { 
  ResponsiveContainer, 
  BarChart, 
  Bar, 
  XAxis, 
  YAxis, 
  Tooltip, 
  Legend, 
  PieChart, 
  Pie, 
  Cell,
  LineChart,
  Line,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar
} from 'recharts';
import './DetailPage.css';

const fmt = new Intl.NumberFormat('ko-KR');
const yearNow = new Date().getFullYear();

const sum = (arr, fn) => arr.reduce((acc, item) => acc + (fn(item) || 0), 0);

export default function VolunteerDetailPage() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
  
  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [volunteers, setVolunteers] = useState([]);
  const [allOrganizations, setAllOrganizations] = useState([]);

  const [selectedOrg, setSelectedOrg] = useState(null);
  const [selectedProjectType, setSelectedProjectType] = useState('all');
  const [range, setRange] = useState({ from: yearNow - 5, to: yearNow });
  const [searchTerm, setSearchTerm] = useState("");
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
    const [orgRes, volunteerRes] = await Promise.all([
      fetch(`${API_BASE}/api/organizations`),
      fetch(`${API_BASE}/api/volunteering?size=50`)  // 첫 페이지만 로드
    ]);

    // 조직 정보 로드
    if (orgRes.ok) {
      const orgData = await orgRes.json();
      setAllOrganizations(orgData);
    }

    // 첫 페이지 봉사활동 데이터
    if (volunteerRes.ok) {
      const data = await volunteerRes.json();
      const normalized = data.map(item => ({
        ...item,
        hours: Number(item.hours || 0),
        participants: Number(item.participants || 0),
        organizationId: item.organizationId || item.organization?.id,
        organizationName: item.organizationName || item.organization?.name,
        verificationStatus: item.verificationStatus || "검증 완료"
      }));
      setVolunteers(normalized);
      setIsApiConnected(true);
    }
  } catch (error) {
    console.error('❌ Volunteer data load failed:', error);
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
    const orgIdsWithData = new Set(volunteers.map(v => v.organizationId));
    return allOrganizations.filter(org => orgIdsWithData.has(org.id));
  }, [allOrganizations, volunteers]);

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

  const filteredData = useMemo(() => {
    return volunteers.filter(v => 
      (!selectedOrg || v.organizationId === selectedOrg.id) &&
      (selectedProjectType === 'all' || v.projectType === selectedProjectType) &&
      v.year >= range.from &&
      v.year <= range.to
    );
  }, [volunteers, selectedOrg, selectedProjectType, range]);

  const totals = useMemo(() => {
    const totalHours = sum(filteredData, v => v.hours);
    const totalParticipants = sum(filteredData, v => v.participants);
    const verifiedCnt = filteredData.filter(v => 
      (v.verificationStatus ?? "").includes("검증완료")
    ).length;
    const rate = filteredData.length ? Math.round((verifiedCnt / filteredData.length) * 100) : 0;
    
    // 추세 계산
    const prevYear = filteredData.filter(v => v.year === yearNow - 1);
    const currYear = filteredData.filter(v => v.year === yearNow);
    const prevTotal = sum(prevYear, v => v.hours);
    const currTotal = sum(currYear, v => v.hours);
    const trend = prevTotal > 0 ? Math.round(((currTotal - prevTotal) / prevTotal) * 100) : 0;
    
    const avgHoursPerPerson = totalParticipants > 0 ? Math.round(totalHours / totalParticipants) : 0;
    
    return { 
      totalHours, 
      totalParticipants,
      verifiedCnt, 
      rate, 
      trend,
      count: filteredData.length,
      avgHoursPerPerson
    };
  }, [filteredData]);

  const byYear = useMemo(() => {
    const map = new Map();
    for (const v of filteredData) {
      const m = map.get(v.year) ?? { year: v.year, hours: 0, participants: 0 };
      m.hours += v.hours || 0;
      m.participants += v.participants || 0;
      map.set(v.year, m);
    }
    return [...map.values()].sort((a, b) => a.year - b.year);
  }, [filteredData]);

  const pieData = useMemo(() => {
    const typeMap = new Map();
    filteredData.forEach(v => {
      const current = typeMap.get(v.projectType) || 0;
      typeMap.set(v.projectType, current + v.hours);
    });
    
    return Array.from(typeMap.entries()).map(([name, value]) => ({
      name,
      value
    }));
  }, [filteredData]);

  const topProjects = useMemo(() => {
    const projectMap = new Map();
    filteredData.forEach(v => {
      const key = v.project;
      const current = projectMap.get(key) || { hours: 0, participants: 0 };
      projectMap.set(key, {
        hours: current.hours + v.hours,
        participants: current.participants + v.participants
      });
    });
    
    return Array.from(projectMap.entries())
      .map(([name, data]) => ({ name, ...data }))
      .sort((a, b) => b.hours - a.hours)
      .slice(0, 5);
  }, [filteredData]);

  const radarData = useMemo(() => {
    const typeMap = new Map();
    PROJECT_TYPES.forEach(type => typeMap.set(type, 0));
    
    filteredData.forEach(v => {
      const current = typeMap.get(v.projectType) || 0;
      typeMap.set(v.projectType, current + v.hours);
    });
    
    const maxHours = Math.max(...Array.from(typeMap.values()), 1);
    
    return Array.from(typeMap.entries()).map(([subject, hours]) => ({
      subject,
      hours,
      fullMark: maxHours * 1.2
    }));
  }, [filteredData]);

  const handleReset = () => {
    setSelectedOrg(null);
    setSelectedProjectType('all');
    setSearchTerm("");
    setRange({ from: yearNow - 5, to: yearNow });
    setCurrentPage(1);
    setShowDropdown(false);
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="spinner"></div>
        <p style={{ color: '#64748b', fontWeight: 600 }}>봉사 실적 분석 중...</p>
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
            <Users />
          </div>
          <div className="header-content">
            <h1 className="header-title">임직원 봉사활동 분석</h1>
            <p className="header-subtitle">기업별 사회공헌 봉사 실적 및 임직원 참여 현황</p>
          </div>
          <div className="header-actions">
            <button className="export-btn glass" onClick={() => {}}>
              <Download size={15} />
              보고서 추출
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
              <label className="filter-label">참여 기업 검색</label>
              <div style={{ position: 'relative' }}>
                <Users size={16} style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
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
                            setCurrentPage(1); // 페이지 초기화
                            setSelectedProjectType('all'); // 프로젝트 타입 초기화
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

            {/* 프로젝트 유형 */}
            <div className="filter-group">
              <label className="filter-label">프로젝트 유형</label>
              <select className="filter-select" value={selectedProjectType} onChange={(e) => setSelectedProjectType(e.target.value)}>
                <option value="all">전체 유형</option>
                {[...new Set(volunteers.map(v => v.projectType))].filter(Boolean).map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>

            {/* 연도 범위 */}
            <div className="filter-group">
              <label className="filter-label">분석 기간</label>
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
              필터 초기화
            </button>
          </div>
        </section>

        {/* 주요 지표 */}
        <section className="stats-grid">
          <div className="stat-card">
            <div className="stat-label">
              <Clock size={16} color="#3b82f6" />
              총 봉사 시간
            </div>
            <div className="stat-value">
              {fmt.format(totals.totalHours)}
              <span className="stat-unit">시간</span>
            </div>
            {totals.trend !== 0 && (
              <div className={`stat-trend ${totals.trend < 0 ? 'down' : 'up'}`} style={{ marginTop: '8px' }}>
                {totals.trend < 0 ? <TrendingDown size={14} /> : <TrendingUp size={14} />}
                {Math.abs(totals.trend)}% 전년 대비
              </div>
            )}
          </div>
          <div className="stat-card">
            <div className="stat-label">
              <Target size={16} color="#10b981" />
              참여 인원 (누적)
            </div>
            <div className="stat-value">
              {fmt.format(totals.totalParticipants)}
              <span className="stat-unit">명</span>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">
              <Award size={16} color="#6366f1" />
              1인당 평균
            </div>
            <div className="stat-value">
              {totals.avgHoursPerPerson}
              <span className="stat-unit">시간</span>
            </div>
          </div>
        </section>

        {/* 차트 영역 */}
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.5fr) minmax(0, 1fr)', gap: '24px' }}>
          <section className="content-card">
            <div className="card-header">
              <h3 className="card-title">연도별 활동 추이</h3>
            </div>
            <div className="card-content">
              <div style={{ height: '350px', width: '100%' }}>
                <ResponsiveContainer>
                  <LineChart data={byYear}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                    <XAxis dataKey="year" axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dy={10} />
                    <YAxis yAxisId="left" axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dx={-10} />
                    <YAxis yAxisId="right" orientation="right" axisLine={false} tickLine={false} tick={{ fill: '#64748b', fontSize: 12 }} dx={10} />
                    <Tooltip contentStyle={{ background: '#fff', borderRadius: '16px', border: '1px solid #e2e8f0', boxShadow: '0 10px 15px -1px rgba(0,0,0,0.1)' }} />
                    <Legend iconType="circle" />
                    <Line yAxisId="left" type="monotone" dataKey="hours" name="봉사시간(h)" stroke="#1e293b" strokeWidth={4} dot={{ r: 6, fill: '#1e293b', strokeWidth: 2, stroke: '#fff' }} />
                    <Line yAxisId="right" type="monotone" dataKey="participants" name="참여인원(명)" stroke="#3b82f6" strokeWidth={4} dot={{ r: 6, fill: '#3b82f6', strokeWidth: 2, stroke: '#fff' }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>

          <section className="content-card">
            <div className="card-header">
              <h3 className="card-title">유형별 비중 (시간 기준)</h3>
            </div>
            <div className="card-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div style={{ height: '350px', width: '100%' }}>
                <ResponsiveContainer>
                  <PieChart>
                    <Pie
                      data={pieData}
                      cx="50%"
                      cy="50%"
                      innerRadius={80}
                      outerRadius={110}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {pieData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={[ '#1e293b', '#3b82f6', '#6366f1', '#8b5cf6', '#ec4899', '#f97316' ][index % 6]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend iconType="circle" layout="vertical" align="right" verticalAlign="middle" />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>
        </div>

        {/* 상세 내역 테이블 */}
        <section className="content-card">
          <div className="card-header">
            <h3 className="card-title">봉사활동 상세 리스트</h3>
          </div>
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>연도</th>
                  <th>기업명</th>
                  <th>프로젝트명</th>
                  <th>유형</th>
                  <th style={{ textAlign: 'right' }}>봉사시간</th>
                  <th style={{ textAlign: 'right' }}>참여인원</th>
                  <th style={{ textAlign: 'center' }}>검증 상태</th>
                </tr>
              </thead>
              <tbody>
                {filteredData.length > 0 ? (
                  filteredData
                    .sort((a, b) => b.year - a.year)
                    .map((v, idx) => (
                      <tr key={idx}>
                        <td>{v.year}년</td>
                        <td style={{ fontWeight: 700, color: '#1e293b' }}>
                          {allOrganizations.find(org => org.id === v.organizationId)?.name || v.organizationName}
                        </td>
                        <td>{v.project}</td>
                        <td style={{ fontSize: '12px', color: '#64748b' }}>{v.projectType}</td>
                        <td style={{ textAlign: 'right', fontWeight: 600 }}>{fmt.format(v.hours)} h</td>
                        <td style={{ textAlign: 'right' }}>{fmt.format(v.participants)} 명</td>
                        <td style={{ textAlign: 'center' }}>
                          <span className={`status-badge ${v.verificationStatus?.includes('완료') ? 'connected' : 'disconnected'}`}>
                            {v.verificationStatus}
                          </span>
                        </td>
                      </tr>
                    ))
                ) : (
                  <tr>
                    <td colSpan="7" className="empty-state">조회된 데이터가 없습니다.</td>
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
