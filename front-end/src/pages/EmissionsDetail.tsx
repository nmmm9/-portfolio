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

const fmt = new Intl.NumberFormat('ko-KR');

// 전문적인 색상 팔레트 - 더 세련되고 모던하게
const COLORS = {
  primary: '#0F172A',       // 다크 슬레이트
  secondary: '#64748B',     // 슬레이트 그레이
  accent: '#0EA5E9',        // 스카이 블루
  success: '#10B981',       // 에메랄드
  warning: '#F59E0B',       // 앰버
  background: '#F8FAFC',    // 라이트
  cardBg: '#FFFFFF',
  border: '#E2E8F0',
};

interface Organization {
  id: number;
  name: string;
  type?: string;
}

interface Emission {
  id: number;
  organizationId: number;
  organizationName?: string;
  year: number;
  totalEmissions: number;
  verificationStatus?: string;
}

export default function EmissionsDetail() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';
  const yearNow = new Date().getFullYear();
  
  const [isApiConnected, setIsApiConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingEmissions, setLoadingEmissions] = useState(false);
  
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [emissions, setEmissions] = useState<Emission[]>([]);
  
  const [selectedOrg, setSelectedOrg] = useState('');
  const [range, setRange] = useState({ from: yearNow - 4, to: yearNow });
  const [searchTerm, setSearchTerm] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  useEffect(() => {
    loadOrganizations();
  }, []);

  useEffect(() => {
    if (organizations.length > 0) {
      loadEmissions();
    }
  }, [selectedOrg, range]);

  // 초기 로드 시에만 전체 데이터의 연도 범위 설정
  const [initialRangeSet, setInitialRangeSet] = useState(false);
  
  useEffect(() => {
    if (emissions.length > 0 && !selectedOrg && !initialRangeSet) {
      const years = emissions.map(e => e.year).sort((a, b) => a - b);
      if (years.length > 0) {
        const minYear = years[0];
        const maxYear = years[years.length - 1];
        console.log(`📅 초기 전체 연도 범위 설정: ${minYear}년 ~ ${maxYear}년`);
        setRange({ from: minYear, to: maxYear });
        setInitialRangeSet(true);
      }
    }
  }, [emissions.length, selectedOrg]);

  useEffect(() => {
    const handleClickOutside = (e: any) => {
      if (!e.target.closest('.org-dropdown-container')) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, []);

  const loadOrganizations = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${API_BASE}/api/emissions/organizations`);
      setIsApiConnected(response.ok);

      if (response.ok) {
        const data = await response.json();
        console.log('✅ Organizations loaded:', data.length);
        setOrganizations(data);
      } else {
        console.warn('⚠️ Organizations API returned:', response.status);
      }
    } catch (error) {
      console.error('❌ Organization load failed:', error);
      setIsApiConnected(false);
    } finally {
      setLoading(false);
    }
  };

  const loadEmissions = async () => {
    setLoadingEmissions(true);
    try {
      let url = `${API_BASE}/api/emissions?fromYear=${range.from}&toYear=${range.to}`;
      if (selectedOrg) {
        url += `&orgId=${selectedOrg}`;
        console.log('🔍 조직별 배출량 조회:', url);
      } else {
        console.log('🔍 전체 배출량 조회:', url);
      }

      const response = await fetch(url);
      
      if (response.ok) {
        const data = await response.json();
        console.log(`✅ 배출량 로드 완료: ${data.length}개 기록`);
        setEmissions(data);
      } else {
        console.warn('⚠️ Emissions API returned:', response.status);
        setEmissions([]);
      }
    } catch (error) {
      console.error('❌ Emissions load failed:', error);
      setEmissions([]);
    } finally {
      setLoadingEmissions(false);
    }
  };

  const filteredEmissions = useMemo(() => {
    return emissions.filter(e => {
      const matchOrg = !selectedOrg || e.organizationId === Number(selectedOrg);
      const matchYear = e.year >= range.from && e.year <= range.to;
      return matchOrg && matchYear;
    });
  }, [emissions, selectedOrg, range]);

  const totals = useMemo(() => {
    const total = filteredEmissions.reduce((acc, e) => acc + (Number(e.totalEmissions) || 0), 0);
    const verifiedCnt = filteredEmissions.filter(e => 
      e.verificationStatus?.includes('검증완료') || e.verificationStatus?.includes('Verified')
    ).length;
    const rate = filteredEmissions.length > 0 
      ? Math.round((verifiedCnt / filteredEmissions.length) * 100) 
      : 0;

    const currYear = yearNow;
    const prevYear = yearNow - 1;
    const currTotal = filteredEmissions
      .filter(e => e.year === currYear)
      .reduce((acc, e) => acc + (Number(e.totalEmissions) || 0), 0);
    const prevTotal = filteredEmissions
      .filter(e => e.year === prevYear)
      .reduce((acc, e) => acc + (Number(e.totalEmissions) || 0), 0);
    
    const trend = prevTotal > 0 
      ? Math.round(((currTotal - prevTotal) / prevTotal) * 100) 
      : 0;
    
    return { total, verifiedCnt, rate, trend };
  }, [filteredEmissions, yearNow]);

  const byYear = useMemo(() => {
    const map = new Map();
    for (const e of filteredEmissions) {
      const m = map.get(e.year) ?? 0;
      map.set(e.year, m + (Number(e.totalEmissions) || 0));
    }
    return Array.from(map.entries())
      .map(([year, total]) => ({ year, total }))
      .sort((a, b) => a.year - b.year);
  }, [filteredEmissions]);

  const selectedOrgName = selectedOrg 
    ? organizations.find(o => o.id === Number(selectedOrg))?.name 
    : '';

  const availableYears = useMemo(() => {
    if (!selectedOrg) {
      // 조직이 선택되지 않았으면 최근 20년
      return Array.from({ length: 20 }, (_, i) => yearNow - i);
    }
    
    // 선택된 조직의 실제 배출량 데이터에서 연도 추출
    const orgEmissions = emissions.filter(e => e.organizationId === Number(selectedOrg));
    
    if (orgEmissions.length > 0) {
      // 실제 데이터가 있는 연도만 추출
      const years = [...new Set(orgEmissions.map(e => e.year))].sort((a, b) => b - a);
      console.log(`📅 ${selectedOrgName}: 사용 가능한 연도`, years);
      return years;
    }
    
    // 데이터가 없으면 전체 연도 범위
    return Array.from({ length: 20 }, (_, i) => yearNow - i);
  }, [selectedOrg, emissions, yearNow]);

  const filteredOrganizations = useMemo(() => {
    if (!searchTerm) return organizations;
    return organizations.filter(org => 
      org.name?.toLowerCase().includes(searchTerm.toLowerCase())
    );
  }, [organizations, searchTerm]);

  const paginatedOrganizations = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage;
    return filteredOrganizations.slice(start, start + itemsPerPage);
  }, [filteredOrganizations, currentPage]);

  const totalPages = Math.ceil(filteredOrganizations.length / itemsPerPage);

  const handleOrgSelect = async (orgId: number, orgName: string) => {
    console.log(`🏢 조직 선택: ${orgName} (ID: ${orgId})`);
    
    setSelectedOrg(String(orgId));
    setSearchTerm(orgName);
    setShowDropdown(false);
    setCurrentPage(1);
    
    // 해당 조직의 배출량 데이터를 API에서 가져와서 연도 범위 설정
    try {
      const response = await fetch(`${API_BASE}/api/emissions?orgId=${orgId}`);
      if (response.ok) {
        const data = await response.json();
        
        if (data.length > 0) {
          const years = data.map((e: Emission) => e.year).sort((a: number, b: number) => a - b);
          const minYear = years[0];
          const maxYear = years[years.length - 1];
          
          console.log(`📅 ${orgName}: 연도 범위 ${minYear}년 ~ ${maxYear}년 자동 설정`);
          
          // 연도 범위만 설정 (emissions는 useEffect가 자동으로 로드)
          setRange({ from: minYear, to: maxYear });
        } else {
          console.warn(`⚠️ ${orgName}: 배출량 데이터가 없습니다`);
          setRange({ from: yearNow - 4, to: yearNow });
        }
      }
    } catch (error) {
      console.error('❌ 조직 데이터 로드 실패:', error);
      setRange({ from: yearNow - 4, to: yearNow });
    }
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
      <div style={{ 
        minHeight: '100vh', 
        width: '100vw', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center',
        background: COLORS.background,
        margin: 0,
        padding: 0
      }}>
        <div style={{ textAlign: 'center' }}>
          <Loader2 style={{ 
            width: '56px', 
            height: '56px', 
            margin: '0 auto 20px auto', 
            display: 'block',
            animation: 'spin 1s linear infinite',
            color: COLORS.accent
          }} />
          <p style={{ color: COLORS.secondary, fontSize: '16px', fontWeight: 600 }}>
            데이터를 불러오는 중...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ 
      minHeight: '100vh', 
      width: '100vw', 
      background: COLORS.background,
      margin: 0,
      padding: 0,
      boxSizing: 'border-box'
    }}>
      {/* 헤더 */}
      <header style={{ 
        width: '100vw', 
        backgroundColor: 'rgba(255, 255, 255, 0.95)', 
        backdropFilter: 'blur(10px)', 
        borderBottom: `1px solid ${COLORS.border}`, 
        position: 'sticky', 
        top: 0, 
        zIndex: 50,
        boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)',
        margin: 0,
        padding: 0
      }}>
        <div style={{ 
          width: '100%',
          padding: '14px 28px',
          boxSizing: 'border-box'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <Button 
              variant="ghost" 
              size="icon" 
              onClick={() => navigate('/')}
              style={{ 
                borderRadius: '8px', 
                width: '36px', 
                height: '36px',
                transition: 'all 0.2s'
              }}
            >
              <ArrowLeft style={{ width: '18px', height: '18px' }} />
            </Button>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1 }}>
              <div style={{ 
                padding: '8px', 
                background: COLORS.success,
                borderRadius: '10px'
              }}>
                <Factory style={{ width: '20px', height: '20px', color: 'white' }} />
              </div>
              <div>
                <h1 style={{ 
                  fontSize: '20px', 
                  fontWeight: 700, 
                  color: COLORS.primary,
                  marginBottom: '1px',
                  letterSpacing: '-0.3px'
                }}>
                  온실가스 배출량 분석
                </h1>
                <p style={{ fontSize: '13px', color: COLORS.secondary, fontWeight: 500 }}>
                  기업별 온실가스 배출량 추적 및 분석 (단위: tCO₂e)
                </p>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Badge 
                variant={isApiConnected ? "default" : "secondary"} 
                style={{ 
                  borderRadius: '6px', 
                  padding: '5px 12px',
                  fontSize: '12px',
                  fontWeight: 600,
                  background: isApiConnected ? COLORS.success : COLORS.secondary
                }}
              >
                <Database style={{ width: '13px', height: '13px', marginRight: '5px' }} />
                <span>{isApiConnected ? "연결됨" : "대기"}</span>
              </Badge>
              <Button 
                variant="outline" 
                size="sm" 
                onClick={exportToCSV}
                style={{ 
                  height: '32px', 
                  padding: '0 14px',
                  borderRadius: '6px',
                  fontWeight: 600,
                  fontSize: '12px',
                  border: `1px solid ${COLORS.border}`
                }}
              >
                <Download style={{ width: '13px', height: '13px', marginRight: '5px' }} />
                <span>CSV 내보내기</span>
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main style={{ 
        width: '100vw',
        padding: '24px',
        boxSizing: 'border-box',
        margin: 0
      }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', width: '100%' }}>
          {/* 필터 섹션 */}
          <Card style={{ 
            borderRadius: '12px', 
            boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)', 
            border: `1px solid ${COLORS.border}`,
            background: COLORS.cardBg,
            width: '100%'
          }}>
            <CardContent style={{ padding: '20px' }}>
              <div style={{ 
                display: 'flex', 
                alignItems: 'center', 
                gap: '10px', 
                marginBottom: '16px' 
              }}>
                <Filter style={{ width: '18px', height: '18px', color: COLORS.primary }} />
                <h3 style={{ 
                  fontSize: '16px', 
                  fontWeight: 700, 
                  color: COLORS.primary 
                }}>필터 설정</h3>
              </div>
              
              <div style={{ 
                display: 'grid', 
                gridTemplateColumns: 'repeat(3, 1fr)', 
                gap: '14px' 
              }}>
                {/* 조직 검색 */}
                <div style={{ position: 'relative', flex: 1 }} className="org-dropdown-container">
                  <label style={{ 
                    display: 'block', 
                    fontSize: '13px', 
                    fontWeight: 600, 
                    color: COLORS.primary, 
                    marginBottom: '7px' 
                  }}>
                    조직 검색
                  </label>
                  <div style={{ position: 'relative' }}>
                    <Search style={{ 
                      position: 'absolute', 
                      left: '12px', 
                      top: '50%', 
                      transform: 'translateY(-50%)', 
                      width: '15px', 
                      height: '15px', 
                      color: COLORS.secondary, 
                      pointerEvents: 'none',
                      zIndex: 1
                    }} />
                    <Input
                      placeholder={selectedOrg ? selectedOrgName : "조직명 검색..."}
                      value={searchTerm}
                      onChange={(e) => {
                        setSearchTerm(e.target.value);
                        setShowDropdown(true);
                        setCurrentPage(1);
                      }}
                      onFocus={() => setShowDropdown(true)}
                      style={{ 
                        paddingLeft: '36px',
                        paddingRight: selectedOrg ? '36px' : '12px',
                        height: '36px',
                        borderRadius: '7px',
                        border: `1px solid ${selectedOrg ? COLORS.success : COLORS.border}`,
                        fontSize: '13px',
                        fontWeight: selectedOrg ? 600 : 400,
                        transition: 'all 0.2s'
                      }}
                    />
                    {selectedOrg && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          console.log('❌ 조직 선택 해제');
                          setSelectedOrg('');
                          setSearchTerm('');
                          setInitialRangeSet(false);
                          setRange({ from: yearNow - 4, to: yearNow });
                        }}
                        style={{
                          position: 'absolute',
                          right: '10px',
                          top: '50%',
                          transform: 'translateY(-50%)',
                          width: '20px',
                          height: '20px',
                          borderRadius: '50%',
                          border: 'none',
                          background: COLORS.secondary,
                          color: 'white',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '12px',
                          fontWeight: 'bold',
                          zIndex: 2,
                          transition: 'all 0.2s'
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.background = COLORS.primary;
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = COLORS.secondary;
                        }}
                      >
                        ×
                      </button>
                    )}
                  </div>
                  
                  {/* 드롭다운 */}
                  {showDropdown && (
                    <div style={{ 
                      position: 'absolute', 
                      top: '100%', 
                      left: 0, 
                      right: 0, 
                      marginTop: '6px',
                      background: 'white', 
                      borderRadius: '8px', 
                      boxShadow: '0 10px 40px rgba(15, 23, 42, 0.15)', 
                      zIndex: 50,
                      maxHeight: '280px',
                      overflow: 'hidden',
                      border: `1px solid ${COLORS.border}`
                    }}>
                      {paginatedOrganizations.length === 0 && searchTerm ? (
                        <div style={{ padding: '20px', textAlign: 'center', color: COLORS.secondary, fontSize: '13px' }}>
                          조직을 찾을 수 없습니다
                        </div>
                      ) : (
                        <>
                          <div style={{ maxHeight: '210px', overflowY: 'auto' }}>
                            {/* 전체 조직 옵션 */}
                            {!searchTerm && (
                              <button
                                onClick={() => {
                                  console.log('📊 전체 조직 선택');
                                  setSelectedOrg('');
                                  setSearchTerm('');
                                  setShowDropdown(false);
                                  setInitialRangeSet(false);
                                  setRange({ from: yearNow - 4, to: yearNow });
                                }}
                                style={{
                                  width: '100%',
                                  textAlign: 'left',
                                  padding: '10px 14px',
                                  border: 'none',
                                  background: !selectedOrg ? '#F0FDF4' : 'white',
                                  cursor: 'pointer',
                                  transition: 'all 0.15s',
                                  borderBottom: `1px solid ${COLORS.border}`,
                                  fontWeight: 700
                                }}
                                onMouseEnter={(e) => {
                                  e.currentTarget.style.background = !selectedOrg ? '#F0FDF4' : '#F8FAFC';
                                }}
                                onMouseLeave={(e) => {
                                  e.currentTarget.style.background = !selectedOrg ? '#F0FDF4' : 'white';
                                }}
                              >
                                <div style={{ fontWeight: 700, color: COLORS.success, fontSize: '13px' }}>
                                  📊 전체 조직
                                </div>
                                <div style={{ fontSize: '11px', color: COLORS.secondary }}>
                                  모든 조직의 데이터 보기
                                </div>
                              </button>
                            )}
                            
                            {paginatedOrganizations.map((org) => (
                              <button
                                key={org.id}
                                onClick={() => handleOrgSelect(org.id, org.name)}
                                style={{
                                  width: '100%',
                                  textAlign: 'left',
                                  padding: '10px 14px',
                                  border: 'none',
                                  background: selectedOrg === String(org.id) ? '#F0FDF4' : 'white',
                                  cursor: 'pointer',
                                  transition: 'all 0.15s',
                                  borderBottom: `1px solid ${COLORS.border}`
                                }}
                                onMouseEnter={(e) => {
                                  e.currentTarget.style.background = selectedOrg === String(org.id) ? '#F0FDF4' : '#F8FAFC';
                                  e.currentTarget.style.transform = 'translateX(3px)';
                                }}
                                onMouseLeave={(e) => {
                                  e.currentTarget.style.background = selectedOrg === String(org.id) ? '#F0FDF4' : 'white';
                                  e.currentTarget.style.transform = 'translateX(0)';
                                }}
                              >
                                <div style={{ fontWeight: 600, color: COLORS.primary, marginBottom: '3px', fontSize: '13px' }}>
                                  {org.name}
                                </div>
                                <div style={{ fontSize: '11px', color: COLORS.secondary }}>
                                  {org.type || '조직'}
                                </div>
                              </button>
                            ))}
                          </div>
                          
                          {totalPages > 1 && (
                            <div style={{ 
                              padding: '8px 14px', 
                              background: '#F8FAFC', 
                              borderTop: `1px solid ${COLORS.border}`,
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between'
                            }}>
                              <div style={{ fontSize: '11px', color: COLORS.secondary }}>
                                {filteredOrganizations.length}개 중 {((currentPage - 1) * itemsPerPage) + 1}-
                                {Math.min(currentPage * itemsPerPage, filteredOrganizations.length)}번째
                              </div>
                              <div style={{ display: 'flex', gap: '4px' }}>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                                  disabled={currentPage === 1}
                                  style={{ 
                                    height: '24px', 
                                    width: '24px', 
                                    padding: 0,
                                    borderRadius: '5px',
                                    fontSize: '11px'
                                  }}
                                >
                                  ←
                                </Button>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                                  disabled={currentPage === totalPages}
                                  style={{ 
                                    height: '24px', 
                                    width: '24px', 
                                    padding: 0,
                                    borderRadius: '5px',
                                    fontSize: '11px'
                                  }}
                                >
                                  →
                                </Button>
                              </div>
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  )}
                </div>

                {/* 연도 범위 */}
                <div>
                  <label style={{ 
                    display: 'block', 
                    fontSize: '13px', 
                    fontWeight: 600, 
                    color: COLORS.primary, 
                    marginBottom: '7px' 
                  }}>
                    연도 범위
                  </label>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <div style={{ flex: 1 }}>
                      <Select 
                        value={String(range.from)}
                        onValueChange={(value) => {
                          const newFrom = parseInt(value);
                          setRange(prev => {
                            if (newFrom > prev.to) {
                              return { from: newFrom, to: newFrom };
                            }
                            return { ...prev, from: newFrom };
                          });
                        }}
                      >
                        <SelectTrigger style={{ height: '36px', borderRadius: '7px', fontSize: '13px', border: `1px solid ${COLORS.border}` }}>
                          <SelectValue>{range.from}년</SelectValue>
                        </SelectTrigger>
                        <SelectContent style={{ maxHeight: '240px', overflowY: 'auto' }}>
                          {availableYears.map(year => (
                            <SelectItem key={year} value={String(year)}>
                              {year}년
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <span style={{ color: COLORS.secondary, fontWeight: 500, fontSize: '13px' }}>~</span>
                    <div style={{ flex: 1 }}>
                      <Select 
                        value={String(range.to)}
                        onValueChange={(value) => {
                          const newTo = parseInt(value);
                          setRange(prev => {
                            if (newTo < prev.from) {
                              return { from: newTo, to: newTo };
                            }
                            return { ...prev, to: newTo };
                          });
                        }}
                      >
                        <SelectTrigger style={{ height: '36px', borderRadius: '7px', fontSize: '13px', border: `1px solid ${COLORS.border}` }}>
                          <SelectValue>{range.to}년</SelectValue>
                        </SelectTrigger>
                        <SelectContent style={{ maxHeight: '240px', overflowY: 'auto' }}>
                          {availableYears.map(year => (
                            <SelectItem key={year} value={String(year)}>
                              {year}년
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                </div>

                {/* 필터 초기화 */}
                <div style={{ display: 'flex', alignItems: 'flex-end' }}>
                  <Button 
                    variant="outline" 
                    onClick={() => {
                      console.log('🔄 필터 초기화');
                      setSelectedOrg('');
                      setSearchTerm('');
                      setInitialRangeSet(false); // 초기 범위 설정 플래그 리셋
                      setRange({ from: yearNow - 4, to: yearNow });
                    }}
                    style={{ 
                      width: '100%',
                      height: '36px',
                      borderRadius: '7px',
                      fontWeight: 600,
                      fontSize: '13px',
                      border: `1px solid ${COLORS.border}`
                    }}
                  >
                    필터 초기화
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 통계 카드들 */}
          {loadingEmissions ? (
            <div style={{ 
              textAlign: 'center', 
              padding: '60px 0',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Loader2 style={{ 
                width: '44px', 
                height: '44px', 
                margin: '0 auto 14px auto', 
                display: 'block',
                animation: 'spin 1s linear infinite',
                color: COLORS.accent
              }} />
              <p style={{ fontSize: '14px', color: COLORS.secondary, fontWeight: 600 }}>
                배출량 데이터 조회 중...
              </p>
            </div>
          ) : (
            <React.Fragment>
              {/* 요약 통계 */}
              <div style={{ 
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: '16px',
                width: '100%'
              }}>
                <Card style={{ 
                  borderRadius: '12px', 
                  boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)', 
                  border: `1px solid ${COLORS.border}`,
                  background: COLORS.cardBg
                }}>
                  <CardContent style={{ padding: '20px' }}>
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'start', 
                      justifyContent: 'space-between', 
                      marginBottom: '14px' 
                    }}>
                      <div style={{ 
                        padding: '10px', 
                        background: `${COLORS.success}10`,
                        borderRadius: '10px'
                      }}>
                        <Activity style={{ width: '20px', height: '20px', color: COLORS.success }} />
                      </div>
                      {totals.trend !== 0 && (
                        <Badge 
                          variant={totals.trend < 0 ? "default" : "destructive"} 
                          style={{ 
                            borderRadius: '5px',
                            padding: '3px 8px',
                            fontSize: '11px',
                            fontWeight: 600
                          }}
                        >
                          {totals.trend < 0 ? <TrendingDown size={12} style={{ marginRight: '3px' }} /> 
                                            : <TrendingUp size={12} style={{ marginRight: '3px' }} />}
                          {Math.abs(totals.trend)}%
                        </Badge>
                      )}
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: COLORS.secondary, marginBottom: '7px' }}>
                      총 배출량
                    </div>
                    <div style={{ fontSize: '28px', fontWeight: 700, color: COLORS.primary, marginBottom: '4px' }}>
                      {fmt.format(Math.round(totals.total))}
                    </div>
                    <div style={{ fontSize: '13px', color: COLORS.secondary }}>tCO₂e</div>
                  </CardContent>
                </Card>

                <Card style={{ 
                  borderRadius: '12px', 
                  boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)', 
                  border: `1px solid ${COLORS.border}`,
                  background: COLORS.cardBg
                }}>
                  <CardContent style={{ padding: '20px' }}>
                    <div style={{ 
                      padding: '10px', 
                      background: `${COLORS.accent}10`,
                      borderRadius: '10px',
                      marginBottom: '14px',
                      width: 'fit-content'
                    }}>
                      <BarChart3 style={{ width: '20px', height: '20px', color: COLORS.accent }} />
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: COLORS.secondary, marginBottom: '7px' }}>
                      데이터 수
                    </div>
                    <div style={{ fontSize: '28px', fontWeight: 700, color: COLORS.primary, marginBottom: '4px' }}>
                      {filteredEmissions.length}
                    </div>
                    <div style={{ fontSize: '13px', color: COLORS.secondary }}>개 기록</div>
                  </CardContent>
                </Card>

                <Card style={{ 
                  borderRadius: '12px', 
                  boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)', 
                  border: `1px solid ${COLORS.border}`,
                  background: COLORS.cardBg
                }}>
                  <CardContent style={{ padding: '20px' }}>
                    <div style={{ 
                      padding: '10px', 
                      background: `${COLORS.warning}10`,
                      borderRadius: '10px',
                      marginBottom: '14px',
                      width: 'fit-content'
                    }}>
                      <CheckCircle2 style={{ width: '20px', height: '20px', color: COLORS.warning }} />
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: COLORS.secondary, marginBottom: '7px' }}>
                      검증 완료율
                    </div>
                    <div style={{ fontSize: '28px', fontWeight: 700, color: COLORS.primary, marginBottom: '4px' }}>
                      {totals.rate}%
                    </div>
                    <div style={{ fontSize: '13px', color: COLORS.secondary }}>
                      {totals.verifiedCnt} / {filteredEmissions.length}개
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* 차트 */}
              {byYear.length > 0 && (
                <Card style={{ 
                  borderRadius: '12px', 
                  boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)', 
                  border: `1px solid ${COLORS.border}`,
                  background: COLORS.cardBg,
                  width: '100%'
                }}>
                  <CardHeader style={{ padding: '20px 20px 0 20px' }}>
                    <CardTitle style={{ fontSize: '16px', fontWeight: 700, color: COLORS.primary }}>
                      연도별 배출량 추이
                    </CardTitle>
                  </CardHeader>
                  <CardContent style={{ padding: '20px' }}>
                    <ResponsiveContainer width="100%" height={360}>
                      <LineChart data={byYear}>
                        <CartesianGrid strokeDasharray="3 3" stroke={COLORS.border} />
                        <XAxis 
                          dataKey="year" 
                          stroke={COLORS.secondary}
                          style={{ fontSize: '12px', fontWeight: 500 }}
                        />
                        <YAxis 
                          stroke={COLORS.secondary}
                          style={{ fontSize: '12px', fontWeight: 500 }}
                        />
                        <Tooltip 
                          contentStyle={{ 
                            borderRadius: '8px',
                            border: `1px solid ${COLORS.border}`,
                            boxShadow: '0 4px 12px rgba(15, 23, 42, 0.1)',
                            fontSize: '12px',
                            padding: '10px'
                          }}
                        />
                        <Legend wrapperStyle={{ fontSize: '12px', fontWeight: 500 }} />
                        <Line 
                          type="monotone" 
                          dataKey="total" 
                          stroke={COLORS.success}
                          strokeWidth={3}
                          name="총 배출량 (tCO₂e)" 
                          dot={{ fill: COLORS.success, r: 5 }}
                          activeDot={{ r: 7 }}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>
              )}

              {/* 데이터 테이블 */}
              <Card style={{ 
                borderRadius: '12px', 
                boxShadow: '0 1px 3px rgba(15, 23, 42, 0.08)', 
                border: `1px solid ${COLORS.border}`,
                background: COLORS.cardBg,
                width: '100%'
              }}>
                <CardHeader style={{ padding: '20px 20px 16px 20px' }}>
                  <CardTitle style={{ fontSize: '16px', fontWeight: 700, color: COLORS.primary }}>
                    배출량 상세 데이터
                  </CardTitle>
                </CardHeader>
                <CardContent style={{ padding: '0 20px 20px 20px' }}>
                  {filteredEmissions.length === 0 ? (
                    <div style={{ 
                      textAlign: 'center', 
                      padding: '50px 0', 
                      color: COLORS.secondary 
                    }}>
                      <AlertCircle style={{ 
                        width: '48px', 
                        height: '48px', 
                        margin: '0 auto 16px auto', 
                        color: COLORS.secondary 
                      }} />
                      <p style={{ fontSize: '14px', fontWeight: 600 }}>
                        선택한 기간에 배출량 데이터가 없습니다.
                      </p>
                    </div>
                  ) : (
                    <div style={{ overflowX: 'auto', borderRadius: '8px', border: `1px solid ${COLORS.border}` }}>
                      <table style={{ width: '100%' }}>
                        <thead>
                          <tr style={{ 
                            borderBottom: `2px solid ${COLORS.border}`, 
                            background: '#F8FAFC' 
                          }}>
                            <th style={{ 
                              padding: '14px 18px', 
                              textAlign: 'left', 
                              fontSize: '13px', 
                              fontWeight: 700, 
                              color: COLORS.primary 
                            }}>조직명</th>
                            <th style={{ 
                              padding: '14px 18px', 
                              textAlign: 'center', 
                              fontSize: '13px', 
                              fontWeight: 700, 
                              color: COLORS.primary 
                            }}>연도</th>
                            <th style={{ 
                              padding: '14px 18px', 
                              textAlign: 'right', 
                              fontSize: '13px', 
                              fontWeight: 700, 
                              color: COLORS.primary 
                            }}>총배출량 (tCO₂e)</th>
                            <th style={{ 
                              padding: '14px 18px', 
                              textAlign: 'center', 
                              fontSize: '13px', 
                              fontWeight: 700, 
                              color: COLORS.primary 
                            }}>검증상태</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredEmissions.slice(0, 50).map((emission, idx) => (
                            <tr 
                              key={emission.id} 
                              style={{ 
                                borderBottom: `1px solid ${COLORS.border}`, 
                                background: idx % 2 === 0 ? 'white' : '#F8FAFC',
                                transition: 'background 0.15s'
                              }}
                              onMouseEnter={(e) => {
                                e.currentTarget.style.background = '#F1F5F9';
                              }}
                              onMouseLeave={(e) => {
                                e.currentTarget.style.background = idx % 2 === 0 ? 'white' : '#F8FAFC';
                              }}
                            >
                              <td style={{ 
                                padding: '12px 18px', 
                                fontSize: '13px', 
                                fontWeight: 600, 
                                color: COLORS.primary 
                              }}>
                                {organizations.find(o => o.id === emission.organizationId)?.name || '-'}
                              </td>
                              <td style={{ 
                                padding: '12px 18px', 
                                textAlign: 'center', 
                                fontSize: '13px', 
                                color: COLORS.secondary,
                                fontWeight: 500
                              }}>
                                {emission.year}
                              </td>
                              <td style={{ 
                                padding: '12px 18px', 
                                textAlign: 'right', 
                                fontSize: '13px', 
                                fontWeight: 700, 
                                color: COLORS.primary 
                              }}>
                                {fmt.format(emission.totalEmissions)}
                              </td>
                              <td style={{ 
                                padding: '12px 18px', 
                                textAlign: 'center' 
                              }}>
                                <Badge 
                                  variant={
                                    emission.verificationStatus?.includes('검증완료') || 
                                    emission.verificationStatus?.includes('Verified') 
                                      ? "default" 
                                      : "secondary"
                                  }
                                  style={{ 
                                    borderRadius: '5px', 
                                    padding: '3px 10px', 
                                    fontSize: '11px',
                                    fontWeight: 600
                                  }}
                                >
                                  {emission.verificationStatus || '미검증'}
                                </Badge>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      {filteredEmissions.length > 50 && (
                        <div style={{ 
                          padding: '12px 18px', 
                          textAlign: 'center', 
                          background: '#F8FAFC', 
                          borderTop: `1px solid ${COLORS.border}`,
                          fontSize: '12px',
                          color: COLORS.secondary,
                          fontWeight: 500
                        }}>
                          상위 50개 항목만 표시됩니다 (전체: {filteredEmissions.length}개)
                        </div>
                      )}
                    </div>
                  )}
                </CardContent>
              </Card>
            </React.Fragment>
          )}
        </div>
      </main>
    </div>
  );
}