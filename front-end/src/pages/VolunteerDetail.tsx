import React, { useState, useMemo } from 'react';
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

const fmt = new Intl.NumberFormat('ko-KR');
const yearNow = new Date().getFullYear();

const MOCK_ORGS = [
  { id: 1, name: "삼성전자", type: "상장사" },
  { id: 2, name: "SK하이닉스", type: "상장사" },
  { id: 3, name: "현대자동차", type: "상장사" }
];

const PROJECT_TYPES = [
  '교육 지원',
  '환경 보호',
  '지역사회 봉사',
  '재능 기부',
  '재난 구호',
  '기타'
];

const MOCK_VOLUNTEER_DATA = [
  { 
    id: 1, 
    year: yearNow - 2, 
    hours: 3200,
    project: '방과후 학습 멘토링',
    projectType: '교육 지원',
    participants: 85,
    verificationStatus: '검증완료',
    dataSource: 'VMS',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 2, 
    year: yearNow - 2, 
    hours: 1800,
    project: '해안 정화 활동',
    projectType: '환경 보호',
    participants: 120,
    verificationStatus: '검증완료',
    dataSource: 'VMS',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 3, 
    year: yearNow - 1, 
    hours: 3800,
    project: '방과후 학습 멘토링',
    projectType: '교육 지원',
    participants: 95,
    verificationStatus: '검증완료',
    dataSource: 'VMS',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 4, 
    year: yearNow - 1, 
    hours: 2200,
    project: '독거노인 돌봄',
    projectType: '지역사회 봉사',
    participants: 68,
    verificationStatus: '검증완료',
    dataSource: 'VMS',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 5, 
    year: yearNow, 
    hours: 4200,
    project: '방과후 학습 멘토링',
    projectType: '교육 지원',
    participants: 110,
    verificationStatus: '검증중',
    dataSource: '내부 시스템',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 6, 
    year: yearNow, 
    hours: 1500,
    project: 'IT 교육 봉사',
    projectType: '재능 기부',
    participants: 45,
    verificationStatus: '검증중',
    dataSource: '내부 시스템',
    organization: { id: 1, name: "삼성전자" }
  },
  { 
    id: 7, 
    year: yearNow - 2, 
    hours: 2400,
    project: '지역아동센터 지원',
    projectType: '교육 지원',
    participants: 72,
    verificationStatus: '검증완료',
    dataSource: 'VMS',
    organization: { id: 2, name: "SK하이닉스" }
  },
  { 
    id: 8, 
    year: yearNow - 1, 
    hours: 2800,
    project: '나무 심기 캠페인',
    projectType: '환경 보호',
    participants: 156,
    verificationStatus: '검증완료',
    dataSource: 'VMS',
    organization: { id: 2, name: "SK하이닉스" }
  },
  { 
    id: 9, 
    year: yearNow, 
    hours: 3500,
    project: '푸드뱅크 운영',
    projectType: '지역사회 봉사',
    participants: 88,
    verificationStatus: '검증중',
    dataSource: '내부 시스템',
    organization: { id: 2, name: "SK하이닉스" }
  }
];

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];

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

export default function VolunteerDetailPage() {
  const [selectedOrg, setSelectedOrg] = useState(undefined);
  const [selectedProjectType, setSelectedProjectType] = useState('all');
  const [range, setRange] = useState({ from: yearNow - 2, to: yearNow });
  const [quickRange, setQuickRange] = useState('3years');

  const filteredData = useMemo(() => {
    return MOCK_VOLUNTEER_DATA.filter(v => 
      (!selectedOrg || v.organization?.id === selectedOrg) &&
      (selectedProjectType === 'all' || v.projectType === selectedProjectType) &&
      v.year >= range.from &&
      v.year <= range.to
    );
  }, [selectedOrg, selectedProjectType, range]);

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
    
    const maxHours = Math.max(...Array.from(typeMap.values()));
    
    return Array.from(typeMap.entries()).map(([subject, hours]) => ({
      subject,
      hours,
      fullMark: maxHours * 1.2
    }));
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
              <h1 className="text-2xl font-bold">봉사활동 시간 상세</h1>
              <p className="text-sm text-muted-foreground">임직원 봉사활동 현황 및 분석 (단위: 시간)</p>
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
            <div className="grid md:grid-cols-6 gap-4">
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
                <label className="text-sm font-medium">프로젝트 유형</label>
                <Select 
                  value={selectedProjectType}
                  onValueChange={setSelectedProjectType}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="전체 유형" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">전체 유형</SelectItem>
                    {PROJECT_TYPES.map(type => (
                      <SelectItem key={type} value={type}>{type}</SelectItem>
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
            label="총 봉사시간" 
            value={`${fmt.format(totals.totalHours)} 시간`}
            sub={`${range.from}–${range.to}년${selectedOrg ? " · 선택된 조직" : " · 전체"}`}
            trend={totals.trend}
            icon={Clock}
          />
          <KpiCard 
            label="참여 인원" 
            value={`${fmt.format(totals.totalParticipants)} 명`}
            sub="누적 참여자"
            icon={Users}
          />
          <KpiCard 
            label="1인당 평균" 
            value={`${totals.avgHoursPerPerson} 시간`}
            sub="참여자당 평균 봉사시간"
            icon={Target}
          />
          <KpiCard 
            label="검증 완료율" 
            value={`${totals.rate}%`}
            sub={`${totals.verifiedCnt}/${totals.count} 건`}
            icon={Award}
          />
        </div>

        {/* 차트 섹션 */}
        <div className="grid lg:grid-cols-2 gap-6">
          {/* 연도별 봉사시간 추이 */}
          <Card className="rounded-2xl">
            <CardHeader>
              <CardTitle>연도별 봉사시간 추이</CardTitle>
              <p className="text-xs text-muted-foreground">총 봉사시간 및 참여 인원</p>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={320}>
                <LineChart data={byYear} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                  <XAxis dataKey="year" />
                  <YAxis yAxisId="left" />
                  <YAxis yAxisId="right" orientation="right" />
                  <Tooltip />
                  <Legend />
                  <Line 
                    yAxisId="left"
                    type="monotone" 
                    dataKey="hours" 
                    name="봉사시간" 
                    stroke="#8b5cf6" 
                    strokeWidth={2}
                    dot={{ r: 4 }}
                  />
                  <Line 
                    yAxisId="right"
                    type="monotone" 
                    dataKey="participants" 
                    name="참여인원" 
                    stroke="#3b82f6" 
                    strokeWidth={2}
                    dot={{ r: 4 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          {/* 프로젝트 유형별 분포 */}
          <Card className="rounded-2xl">
            <CardHeader>
              <CardTitle>프로젝트 유형별 분포</CardTitle>
              <p className="text-xs text-muted-foreground">활동 유형별 시간 비율</p>
            </CardHeader>
            <CardContent className="flex items-center justify-center">
              <ResponsiveContainer width="100%" height={320}>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                      label={({ name, percent }: any) => `${name?.split(' ')[0] || ''} ${((percent as number) * 100).toFixed(0)}%`}
                    outerRadius={100}
                    fill="#8884d8"
                    dataKey="value"
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                    <Tooltip formatter={(value: any) => `${fmt.format(Number(value))} t`} />
                </PieChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </div>

        {/* 활동 유형별 레이더 차트 */}
        <Card className="rounded-2xl">
          <CardHeader>
            <CardTitle>활동 유형별 시간 분석</CardTitle>
            <p className="text-xs text-muted-foreground">6가지 봉사 유형별 활동 시간 비교</p>
          </CardHeader>
          <CardContent className="flex items-center justify-center">
            <ResponsiveContainer width="100%" height={400}>
              <RadarChart data={radarData}>
                <PolarGrid />
                <PolarAngleAxis dataKey="subject" />
                <PolarRadiusAxis />
                <Radar 
                  name="봉사시간" 
                  dataKey="hours" 
                  stroke="#8b5cf6" 
                  fill="#8b5cf6" 
                  fillOpacity={0.6} 
                />
                  <Tooltip formatter={(value: any) => `${fmt.format(Number(value))} t`} />
                <Legend />
              </RadarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* 주요 프로젝트 TOP 5 */}
        <Card className="rounded-2xl">
          <CardHeader>
            <CardTitle>주요 프로젝트 TOP 5</CardTitle>
            <p className="text-xs text-muted-foreground">봉사시간이 가장 많은 프로젝트</p>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {topProjects.map((project, idx) => (
                <div key={project.name} className="flex items-center gap-4 p-4 bg-gray-50 rounded-xl">
                  <div className="flex items-center justify-center w-10 h-10 bg-purple-100 rounded-full font-bold text-purple-600">
                    {idx + 1}
                  </div>
                  <div className="flex-1">
                    <div className="font-medium">{project.name}</div>
                    <div className="text-sm text-muted-foreground">
                      {fmt.format(project.hours)} 시간 · {fmt.format(project.participants)} 명 참여
                    </div>
                  </div>
                  <div className="w-48 bg-gray-200 rounded-full h-2">
                    <div 
                      className="bg-purple-600 h-2 rounded-full" 
                      style={{ width: `${(project.hours / topProjects[0].hours) * 100}%` }}
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
            <p className="text-xs text-muted-foreground">봉사활동 내역 상세 정보</p>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b-2">
                  <tr>
                    <th className="py-3 px-4 text-left font-semibold">연도</th>
                    <th className="py-3 px-4 text-left font-semibold">조직명</th>
                    <th className="py-3 px-4 text-left font-semibold">프로젝트명</th>
                    <th className="py-3 px-4 text-left font-semibold">활동 유형</th>
                    <th className="py-3 px-4 text-right font-semibold">봉사시간</th>
                    <th className="py-3 px-4 text-right font-semibold">참여인원</th>
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
                      .sort((a, b) => (b.year - a.year) || (b.hours - a.hours))
                      .map(v => (
                        <tr key={v.id} className="border-b hover:bg-gray-50">
                          <td className="py-3 px-4">{v.year}</td>
                          <td className="py-3 px-4 font-medium">{v.organization?.name || '-'}</td>
                          <td className="py-3 px-4">{v.project}</td>
                          <td className="py-3 px-4">
                            <Badge variant="outline">{v.projectType}</Badge>
                          </td>
                          <td className="py-3 px-4 text-right font-semibold">
                            {fmt.format(v.hours)}
                          </td>
                          <td className="py-3 px-4 text-right">
                            {fmt.format(v.participants)}
                          </td>
                          <td className="py-3 px-4 text-center">
                            <Badge variant={(v.verificationStatus || "").includes("검증완료") ? "default" : "secondary"}>
                              {v.verificationStatus || "-"}
                            </Badge>
                          </td>
                          <td className="py-3 px-4 text-center text-xs">{v.dataSource || "-"}</td>
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