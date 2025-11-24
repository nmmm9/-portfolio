import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { 
  Users, 
  Download, 
  Database, 
  ArrowLeft,
  TrendingUp,
  TrendingDown,
  MapPin,
  Heart,
  Target
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
  ComposedChart,
  Area
} from 'recharts';

const fmt = new Intl.NumberFormat('ko-KR');
const yearNow = new Date().getFullYear();

const MOCK_ORGS = [
  { id: 1, name: "삼성전자", type: "상장사" },
  { id: 2, name: "SK하이닉스", type: "상장사" },
  { id: 3, name: "현대자동차", type: "상장사" }
];

const BENEFIT_CATEGORIES = [
  '아동/청소년',
  '노인',
  '장애인',
  '저소득층',
  '다문화가정',
  '지역주민',
  '기타'
];

const REGIONS = [
  '서울',
  '경기',
  '인천',
  '강원',
  '충청',
  '전라',
  '경상',
  '제주',
  '기타'
];

const MOCK_PEOPLE_SERVED_DATA = [
  { 
    id: 1, 
    year: yearNow - 2, 
    count: 12500,
    category: '아동/청소년',
    program: '방과후 교육 지원',
    region: '서울',
    verificationStatus: '검증완료',
    dataSource: '복지부',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 2, 
    year: yearNow - 2, 
    count: 8300,
    category: '노인',
    program: '독거노인 돌봄',
    region: '경기',
    verificationStatus: '검증완료',
    dataSource: '복지부',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 3, 
    year: yearNow - 1, 
    count: 15200,
    category: '아동/청소년',
    program: '급식 지원',
    region: '서울',
    verificationStatus: '검증완료',
    dataSource: '복지부',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 4, 
    year: yearNow - 1, 
    count: 9800,
    category: '저소득층',
    program: '주거 환경 개선',
    region: '인천',
    verificationStatus: '검증완료',
    dataSource: '복지부',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 5, 
    year: yearNow, 
    count: 18500,
    category: '아동/청소년',
    program: '장학금 지원',
    region: '서울',
    verificationStatus: '검증중',
    dataSource: '내부 시스템',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 6, 
    year: yearNow, 
    count: 6200,
    category: '장애인',
    program: '재활 프로그램',
    region: '경기',
    verificationStatus: '검증중',
    dataSource: '내부 시스템',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 7, 
    year: yearNow - 2, 
    count: 10500,
    category: '저소득층',
    program: '푸드뱅크 운영',
    region: '충청',
    verificationStatus: '검증완료',
    dataSource: '복지부',
    organization: { id: 2, name: "SK하이닉스" }
  },
  { 
    id: 8, 
    year: yearNow - 1, 
    count: 13200,
    category: '지역주민',
    program: '무료 건강검진',
    region: '경상',
    verificationStatus: '검증완료',
    dataSource: '복지부',
    organization: { id: 2, name: "SK하이닉스" }
  },
  { 
    id: 9, 
    year: yearNow, 
    count: 16800,
    category: '다문화가정',
    program: '한국어 교육',
    region: '경기',
    verificationStatus: '검증중',
    dataSource: '내부 시스템',
    organization: { id: 2, name: "SK하이닉스" }
  }
];

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4'];

function KpiCard({ label, value, sub, trend, icon: Icon }: {
  label: string;
  value: string;
  sub?: string;
  trend?: number;
  icon?: any;  // 이 줄 추가!
}) {
  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm text-muted-foreground">{label}</CardTitle>
          {Icon && (
            <div className="p-2 bg-purple-50 rounded-lg">  {/* VolunteerDetail */}
              <Icon className="w-4 h-4 text-purple-600" />
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex items-end justify-between">
          <div>
            <div className="text-3xl font-semibold tracking-tight">{value}</div>
            {sub && <div className="text-xs text-muted-foreground mt-1">{sub}</div>}
          </div>
          {trend !== undefined && (
            <Badge variant={trend > 0 ? "default" : "secondary"} className="rounded-full">
              {trend > 0 ? <TrendingUp className="w-3 h-3 mr-1" /> : <TrendingDown className="w-3 h-3 mr-1" />}
              {Math.abs(trend)}%
            </Badge>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function sum(arr, f) {
  return arr.reduce((acc, x) => acc + (Number(f(x)) || 0), 0);
}

export default function PeopleServedDetailPage() {
  const [selectedOrg, setSelectedOrg] = useState(undefined);
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [selectedRegion, setSelectedRegion] = useState('all');
  const [range, setRange] = useState({ from: yearNow - 2, to: yearNow });
  const [quickRange, setQuickRange] = useState('3years');

  const filteredData = useMemo(() => {
    return MOCK_PEOPLE_SERVED_DATA.filter(p => 
      (!selectedOrg || p.organization?.id === selectedOrg) &&
      (selectedCategory === 'all' || p.category === selectedCategory) &&
      (selectedRegion === 'all' || p.region === selectedRegion) &&
      p.year >= range.from &&
      p.year <= range.to
    );
  }, [selectedOrg, selectedCategory, selectedRegion, range]);

  const totals = useMemo(() => {
    const totalPeople = sum(filteredData, p => p.count);
    const verifiedCnt = filteredData.filter(p => 
      (p.verificationStatus ?? "").includes("검증완료")
    ).length;
    const rate = filteredData.length ? Math.round((verifiedCnt / filteredData.length) * 100) : 0;
    
    // 추세 계산
    const prevYear = filteredData.filter(p => p.year === yearNow - 1);
    const currYear = filteredData.filter(p => p.year === yearNow);
    const prevTotal = sum(prevYear, p => p.count);
    const currTotal = sum(currYear, p => p.count);
    const trend = prevTotal > 0 ? Math.round(((currTotal - prevTotal) / prevTotal) * 100) : 0;
    
    const avgPerProgram = filteredData.length > 0 ? Math.round(totalPeople / filteredData.length) : 0;
    
    // 카테고리별 수혜자 수
    const uniqueCategories = new Set(filteredData.map(p => p.category)).size;
    
    return { 
      totalPeople, 
      verifiedCnt, 
      rate, 
      trend,
      programCount: filteredData.length,
      avgPerProgram,
      uniqueCategories
    };
  }, [filteredData]);

  const byYear = useMemo(() => {
    const map = new Map();
    for (const p of filteredData) {
      const m = map.get(p.year) ?? { year: p.year, count: 0, programs: 0 };
      m.count += p.count || 0;
      m.programs += 1;
      map.set(p.year, m);
    }
    return [...map.values()].sort((a, b) => a.year - b.year);
  }, [filteredData]);

  const pieDataCategory = useMemo(() => {
    const categoryMap = new Map();
    filteredData.forEach(p => {
      const current = categoryMap.get(p.category) || 0;
      categoryMap.set(p.category, current + p.count);
    });
    
    return Array.from(categoryMap.entries()).map(([name, value]) => ({
      name,
      value
    }));
  }, [filteredData]);

  const pieDataRegion = useMemo(() => {
    const regionMap = new Map();
    filteredData.forEach(p => {
      const current = regionMap.get(p.region) || 0;
      regionMap.set(p.region, current + p.count);
    });
    
    return Array.from(regionMap.entries()).map(([name, value]) => ({
      name,
      value
    }));
  }, [filteredData]);

  const topPrograms = useMemo(() => {
    const programMap = new Map();
    filteredData.forEach(p => {
      const key = p.program;
      const current = programMap.get(key) || 0;
      programMap.set(key, current + p.count);
    });
    
    return Array.from(programMap.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);
  }, [filteredData]);

  const categoryByYear = useMemo(() => {
    const yearMap = new Map();
    
    filteredData.forEach(p => {
      if (!yearMap.has(p.year)) {
        yearMap.set(p.year, {});
      }
      const yearData = yearMap.get(p.year);
      yearData[p.category] = (yearData[p.category] || 0) + p.count;
    });
    
    return Array.from(yearMap.entries())
      .map(([year, categories]) => ({ year, ...categories }))
      .sort((a, b) => a.year - b.year);
  }, [filteredData]);

  const handleQuickRange = (rangeType) => {
    setQuickRange(rangeType);
    switch(rangeType) {
      case '1year':
        setRange({ from: yearNow, to: yearNow });
        break;
      case '3years':
        setRange({ from: yearNow - 2, to: yearNow });
        break;
      case '5years':
        setRange({ from: yearNow - 4, to: yearNow });
        break;
    }
  };

  const handleGoBack = () => {
    alert('메인 대시보드로 돌아가기\n(실제로는 React Router로 이동)');
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-6 py-4">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={handleGoBack}>
              <ArrowLeft className="w-5 h-5" />
            </Button>
            <div className="flex-1">
              <h1 className="text-2xl font-bold">수혜 인원 상세</h1>
              <p className="text-sm text-muted-foreground">사회공헌 프로그램 수혜자 현황 및 분석 (단위: 명)</p>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant="default" className="rounded-full">
                <Database className="w-4 h-4 mr-1" /> Mock 데이터
              </Badge>
              <Button variant="outline" size="sm">
                <Download className="w-4 h-4 mr-1" /> CSV 내보내기
              </Button>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-8 space-y-6">
        {/* 필터 섹션 */}
        <Card className="rounded-2xl shadow-sm">
          <CardContent className="p-6">
            <div className="grid md:grid-cols-7 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">조직 선택</label>
                <Select 
                  value={selectedOrg ? String(selectedOrg) : "all"}
                  onValueChange={(v) => setSelectedOrg(v === "all" ? undefined : Number(v))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="전체 조직" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">전체 조직</SelectItem>
                    {MOCK_ORGS.map(o => (
                      <SelectItem key={o.id} value={String(o.id)}>{o.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">수혜자 유형</label>
                <Select 
                  value={selectedCategory}
                  onValueChange={setSelectedCategory}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="전체 유형" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">전체 유형</SelectItem>
                    {BENEFIT_CATEGORIES.map(cat => (
                      <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">지역</label>
                <Select 
                  value={selectedRegion}
                  onValueChange={setSelectedRegion}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="전체 지역" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">전체 지역</SelectItem>
                    {REGIONS.map(region => (
                      <SelectItem key={region} value={region}>{region}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">시작 연도</label>
                <Input
                  type="number"
                  value={range.from}
                  onChange={(e) => setRange(r => ({ ...r, from: Number(e.target.value) }))}
                  min={2015}
                  max={yearNow}
                />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">종료 연도</label>
                <Input
                  type="number"
                  value={range.to}
                  onChange={(e) => setRange(r => ({ ...r, to: Number(e.target.value) }))}
                  min={2015}
                  max={yearNow}
                />
              </div>

              <div className="space-y-2 md:col-span-2">
                <label className="text-sm font-medium">빠른 선택</label>
                <div className="flex gap-2">
                  <Button 
                    variant={quickRange === '1year' ? 'default' : 'outline'}
                    size="sm"
                    className="flex-1"
                    onClick={() => handleQuickRange('1year')}
                  >
                    올해
                  </Button>
                  <Button 
                    variant={quickRange === '3years' ? 'default' : 'outline'}
                    size="sm"
                    className="flex-1"
                    onClick={() => handleQuickRange('3years')}
                  >
                    최근 3년
                  </Button>
                  <Button 
                    variant={quickRange === '5years' ? 'default' : 'outline'}
                    size="sm"
                    className="flex-1"
                    onClick={() => handleQuickRange('5years')}
                  >
                    최근 5년
                  </Button>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 카드 섹션 */}
        <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard 
            label="총 수혜 인원" 
            value={`${fmt.format(totals.totalPeople)} 명`}
            sub={`${range.from}–${range.to}년${selectedOrg ? " · 선택된 조직" : " · 전체"}`}
            trend={totals.trend}
            icon={Users}
          />
          <KpiCard 
            label="프로그램 수" 
            value={`${totals.programCount} 개`}
            sub="운영된 프로그램"
            icon={Target}
          />
          <KpiCard 
            label="프로그램당 평균" 
            value={`${fmt.format(totals.avgPerProgram)} 명`}
            sub="프로그램당 수혜자"
            icon={Heart}
          />
          <KpiCard 
            label="수혜자 유형" 
            value={`${totals.uniqueCategories} 개`}
            sub={`검증률 ${totals.rate}%`}
            icon={MapPin}
          />
        </div>

        {/* 차트 섹션 */}
        <div className="grid lg:grid-cols-2 gap-6">
          {/* 연도별 수혜 인원 추이 */}
          <Card className="rounded-2xl">
            <CardHeader>
              <CardTitle>연도별 수혜 인원 추이</CardTitle>
              <p className="text-xs text-muted-foreground">총 수혜자 및 프로그램 수</p>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={320}>
                <ComposedChart data={byYear} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                  <XAxis dataKey="year" />
                  <YAxis yAxisId="left" />
                  <YAxis yAxisId="right" orientation="right" />
                  <Tooltip />
                  <Legend />
                  <Bar 
                    yAxisId="left"
                    dataKey="count" 
                    name="수혜 인원" 
                    fill="#f59e0b"
                    radius={[8, 8, 0, 0]}
                  />
                  <Line 
                    yAxisId="right"
                    type="monotone" 
                    dataKey="programs" 
                    name="프로그램 수" 
                    stroke="#3b82f6" 
                    strokeWidth={2}
                    dot={{ r: 5 }}
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          {/* 수혜자 유형별 분포 */}
          <Card className="rounded-2xl">
            <CardHeader>
              <CardTitle>수혜자 유형별 분포</CardTitle>
              <p className="text-xs text-muted-foreground">대상자 유형별 인원 비율</p>
            </CardHeader>
            <CardContent className="flex items-center justify-center">
              <ResponsiveContainer width="100%" height={320}>
                <PieChart>
                  <Pie
                    data={pieDataCategory}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                      label={({ name, percent }: any) => `${name?.split(' ')[0] || ''} ${((percent as number) * 100).toFixed(0)}%`}
                    outerRadius={100}
                    fill="#8884d8"
                    dataKey="value"
                  >
                    {pieDataCategory.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                      <Tooltip formatter={(value: any) => `${fmt.format(Number(value))} t`} />
                </PieChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </div>

        {/* 지역별 분포 */}
        <Card className="rounded-2xl">
          <CardHeader>
            <CardTitle>지역별 수혜 인원 분포</CardTitle>
            <p className="text-xs text-muted-foreground">전국 지역별 수혜자 현황</p>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={pieDataRegion} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                <XAxis dataKey="name" />
                <YAxis />
                  <Tooltip formatter={(value: any) => `${fmt.format(Number(value))} t`} />
                <Bar dataKey="value" name="수혜 인원" fill="#10b981" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* 주요 프로그램 TOP 5 */}
        <Card className="rounded-2xl">
          <CardHeader>
            <CardTitle>주요 프로그램 TOP 5</CardTitle>
            <p className="text-xs text-muted-foreground">수혜 인원이 가장 많은 프로그램</p>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {topPrograms.map((program, idx) => (
                <div key={program.name} className="flex items-center gap-4 p-4 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-center w-10 h-10 bg-orange-100 rounded-full font-bold text-orange-600">
                    {idx + 1}
                  </div>
                  <div className="flex-1">
                    <div className="font-medium">{program.name}</div>
                    <div className="text-sm text-muted-foreground">
                      {fmt.format(program.count)} 명 수혜
                    </div>
                  </div>
                  <div className="w-48 bg-gray-200 rounded-full h-2">
                    <div 
                      className="bg-orange-600 h-2 rounded-full" 
                      style={{ width: `${(program.count / topPrograms[0].count) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* 상세 데이터 테이블 */}
        <Card className="rounded-2xl">
          <CardHeader>
            <CardTitle>상세 데이터</CardTitle>
            <p className="text-xs text-muted-foreground">프로그램별 수혜자 내역 상세 정보</p>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b-2">
                  <tr>
                    <th className="py-3 px-4 text-left font-semibold">연도</th>
                    <th className="py-3 px-4 text-left font-semibold">조직명</th>
                    <th className="py-3 px-4 text-left font-semibold">프로그램</th>
                    <th className="py-3 px-4 text-left font-semibold">수혜자 유형</th>
                    <th className="py-3 px-4 text-left font-semibold">지역</th>
                    <th className="py-3 px-4 text-right font-semibold">수혜 인원</th>
                    <th className="py-3 px-4 text-center font-semibold">검증 상태</th>
                    <th className="py-3 px-4 text-center font-semibold">출처</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredData.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="p-8 text-center text-muted-foreground">
                        데이터가 없습니다
                      </td>
                    </tr>
                  ) : (
                    filteredData
                      .sort((a, b) => (b.year - a.year) || (b.count - a.count))
                      .map(p => (
                        <tr key={p.id} className="border-b hover:bg-gray-50">
                          <td className="py-3 px-4">{p.year}</td>
                          <td className="py-3 px-4 font-medium">{p.organization?.name || '-'}</td>
                          <td className="py-3 px-4">{p.program}</td>
                          <td className="py-3 px-4">
                            <Badge variant="outline">{p.category}</Badge>
                          </td>
                          <td className="py-3 px-4">{p.region}</td>
                          <td className="py-3 px-4 text-right font-semibold">
                            {fmt.format(p.count)}
                          </td>
                          <td className="py-3 px-4 text-center">
                            <Badge variant={(p.verificationStatus || "").includes("검증완료") ? "default" : "secondary"}>
                              {p.verificationStatus || "-"}
                            </Badge>
                          </td>
                          <td className="py-3 px-4 text-center text-xs">{p.dataSource || "-"}</td>
                        </tr>
                      ))
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}