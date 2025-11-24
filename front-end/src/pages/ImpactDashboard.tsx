import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Leaf,
  DollarSign,
  Newspaper,
  Database,
  TrendingUp,
  ChevronRight,
  Calendar,
  ExternalLink,
  ArrowRight,
  CheckCircle2,
  BarChart3,
  Activity,
  Building2,
  MessageCircle,
  Sparkles,
  Zap,
  Brain,
  Bot,
} from "lucide-react";

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
  aiPurple: "#8B5CF6",
  aiPink: "#EC4899",
};

// 슬라이드 이미지 데이터
const SLIDE_IMAGES = [
  {
    url: "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=1200&h=600&fit=crop",
    title: "기업의 사회적 책임",
    subtitle: "지속가능한 미래를 위한 ESG 경영",
  },
  {
    url: "https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=1200&h=600&fit=crop",
    title: "환경 보호 캠페인",
    subtitle: "탄소중립을 향한 기업들의 노력",
  },
  {
    url: "https://images.unsplash.com/photo-1532629345422-7515f3d16bb6?w=1200&h=600&fit=crop",
    title: "지역사회 공헌",
    subtitle: "나눔과 상생의 기업 문화",
  },
  {
    url: "https://images.unsplash.com/photo-1497515114629-f71d768fd07c?w=1200&h=600&fit=crop",
    title: "혁신과 성장",
    subtitle: "기술로 만드는 더 나은 세상",
  },
];

export default function ImpactDashboard() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
  console.log("🌐 API_BASE:", API_BASE);

  const [isApiConnected, setIsApiConnected] = useState(false);
  const [currentSlide, setCurrentSlide] = useState(0);
  const [recentNews, setRecentNews] = useState<any[]>([]);
  const [newsStats, setNewsStats] = useState({ total: 0, thisMonth: 0 });

  // 온실가스 배출량 전용
  const [emissionsOrgs, setEmissionsOrgs] = useState<any[]>([]);
  const [emissionsOrgIndex, setEmissionsOrgIndex] = useState(0);
  const [emissionsOrgData, setEmissionsOrgData] = useState<{
    id: number;
    name: string;
    emissions: number;
    emissionsYear: number;
  } | null>(null);
  const [isEmissionsTransitioning, setIsEmissionsTransitioning] =
    useState(false);

  // 기부금 전용
  const [donationsOrgs, setDonationsOrgs] = useState<any[]>([]);
  const [donationsOrgIndex, setDonationsOrgIndex] = useState(0);
  const [donationsOrgData, setDonationsOrgData] = useState<{
    id: number;
    name: string;
    donations: number;
    donationsYear: number;
  } | null>(null);
  const [isDonationsTransitioning, setIsDonationsTransitioning] =
    useState(false);

  // 데이터 캐시 (성능 최적화)
  const [emissionsCache, setEmissionsCache] = useState<Map<number, any[]>>(
    new Map()
  );
  const [donationsCache, setDonationsCache] = useState<Map<number, any[]>>(
    new Map()
  );

  // API 연결 확인
  useEffect(() => {
    console.log("🚀 ImpactDashboard 마운트됨 - 초기 데이터 로딩 시작");
    checkApiConnection();
    loadInitialData();
  }, []);

  // 자동 슬라이드 (이미지)
  useEffect(() => {
    const interval = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % SLIDE_IMAGES.length);
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  // 온실가스 자동 순환 (3초마다 랜덤)
  useEffect(() => {
    if (emissionsOrgs.length === 0) return;

    const interval = setInterval(() => {
      setIsEmissionsTransitioning(true);
      setTimeout(() => {
        let newIndex;
        do {
          newIndex = Math.floor(Math.random() * emissionsOrgs.length);
        } while (newIndex === emissionsOrgIndex && emissionsOrgs.length > 1);

        console.log(`🌱 온실가스 조직 변경: ${emissionsOrgs[newIndex]?.name}`);
        setEmissionsOrgIndex(newIndex);
        setIsEmissionsTransitioning(false);
      }, 300);
    }, 3000);

    return () => clearInterval(interval);
  }, [emissionsOrgs, emissionsOrgIndex]);

  // 온실가스 조직 변경 시 데이터 로드
  useEffect(() => {
    if (emissionsOrgs.length > 0) {
      loadEmissionsDataFromCache(emissionsOrgs[emissionsOrgIndex].id);
    }
  }, [emissionsOrgIndex, emissionsOrgs]);

  // 기부금 자동 순환 (3초마다 랜덤)
  useEffect(() => {
    if (donationsOrgs.length === 0) return;

    const interval = setInterval(() => {
      setIsDonationsTransitioning(true);
      setTimeout(() => {
        let newIndex;
        do {
          newIndex = Math.floor(Math.random() * donationsOrgs.length);
        } while (newIndex === donationsOrgIndex && donationsOrgs.length > 1);

        console.log(`💰 기부금 조직 변경: ${donationsOrgs[newIndex]?.name}`);
        setDonationsOrgIndex(newIndex);
        setIsDonationsTransitioning(false);
      }, 300);
    }, 3000);

    return () => clearInterval(interval);
  }, [donationsOrgs, donationsOrgIndex]);

  // 기부금 조직 변경 시 데이터 로드
  useEffect(() => {
    if (donationsOrgs.length > 0) {
      loadDonationsDataFromCache(donationsOrgs[donationsOrgIndex].id);
    }
  }, [donationsOrgIndex, donationsOrgs]);

  const checkApiConnection = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/organizations`);
      setIsApiConnected(response.ok);
    } catch (error) {
      setIsApiConnected(false);
    }
  };

  // 🚀 최적화된 필터링: 한 번의 API 호출로 모든 데이터 가져오기
  const filterOrgsWithData = async (orgs: any[]) => {
    console.log(`🔍 조직 데이터 필터링 시작 (최적화됨)...`);

    try {
      // 모든 배출량 데이터를 한 번에 가져오기
      const emissionsRes = await fetch(`${API_BASE}/api/emissions`);
      const allEmissions = emissionsRes.ok ? await emissionsRes.json() : [];

      // 모든 기부금 데이터를 한 번에 가져오기
      const donationsRes = await fetch(`${API_BASE}/api/donations`);
      const allDonations = donationsRes.ok ? await donationsRes.json() : [];

      console.log(
        `📊 전체 배출량: ${allEmissions.length}개, 기부금: ${allDonations.length}개`
      );

      // 조직별로 데이터 그룹화 - 배출량
      const emissionsMap = new Map();
      allEmissions.forEach((e: any) => {
        const orgId = e.organizationId || e.organization?.id;
        if (orgId) {
          if (!emissionsMap.has(orgId)) {
            emissionsMap.set(orgId, []);
          }
          emissionsMap.get(orgId).push(e);
        }
      });

      // 조직별로 데이터 그룹화 - 기부금
      const donationsMap = new Map();
      allDonations.forEach((d: any) => {
        const orgId = d.organizationId || d.organization?.id;

        if (orgId) {
          if (!donationsMap.has(orgId)) {
            donationsMap.set(orgId, []);
          }
          donationsMap.get(orgId).push({
            ...d,
            amount:
              typeof d.donationAmount === "number"
                ? d.donationAmount
                : Number(d.donationAmount || 0),
            year: d.year,
          });
        } else {
          console.warn("⚠️ 기부금 데이터에 조직 ID 없음:", d);
        }
      });

      // 배출량이 있는 조직
      const emissionsOrgs = orgs.filter((org) => emissionsMap.has(org.id));
      console.log(`🌱 배출량 데이터가 있는 조직: ${emissionsOrgs.length}개`);

      // 기부금이 있는 조직
      const donationsOrgs = orgs.filter((org) => {
        const hasDonations = donationsMap.has(org.id);
        if (hasDonations) {
          console.log(`✅ 기부금 있음: ${org.name} (ID: ${org.id})`);
        }
        return hasDonations;
      });
      console.log(`💰 기부금 데이터가 있는 조직: ${donationsOrgs.length}개`);
      console.log(`📊 기부금 맵 크기: ${donationsMap.size}개`);

      return {
        emissionsOrgs,
        donationsOrgs,
        emissionsMap,
        donationsMap,
      };
    } catch (error) {
      console.error("❌ 데이터 필터링 실패:", error);
      return {
        emissionsOrgs: [],
        donationsOrgs: [],
        emissionsMap: new Map(),
        donationsMap: new Map(),
      };
    }
  };

  const loadInitialData = async () => {
    try {
      console.log("📡 API 호출: 조직 목록 가져오기");
      const orgsRes = await fetch(`${API_BASE}/api/organizations`);
      console.log(`📡 API 응답 상태: ${orgsRes.status} ${orgsRes.statusText}`);

      if (orgsRes.ok) {
        const orgs = await orgsRes.json();
        console.log(`✅ 전체 조직 수: ${orgs.length}개`);

        // 🚀 최적화: 한 번에 모든 데이터 필터링
        const { emissionsOrgs, donationsOrgs, emissionsMap, donationsMap } =
          await filterOrgsWithData(orgs);

        // 캐시 저장
        setEmissionsCache(emissionsMap);
        setDonationsCache(donationsMap);

        // 온실가스 조직 설정
        if (emissionsOrgs.length > 0) {
          const shuffled = emissionsOrgs.sort(() => Math.random() - 0.5);
          setEmissionsOrgs(shuffled);
          loadEmissionsDataFromCache(shuffled[0].id, emissionsMap);
        } else {
          console.warn("⚠️ 배출량 데이터가 있는 조직이 없습니다");
        }

        // 기부금 조직 설정
        if (donationsOrgs.length > 0) {
          const shuffled = donationsOrgs.sort(() => Math.random() - 0.5);
          setDonationsOrgs(shuffled);
          loadDonationsDataFromCache(shuffled[0].id, donationsMap);
        } else {
          console.warn("⚠️ 기부금 데이터가 있는 조직이 없습니다");
        }
      } else {
        console.error(`❌ API 호출 실패: ${orgsRes.status}`);
      }

      // 뉴스 통계
      const newsCountRes = await fetch(
        `${API_BASE}/api/positive-news/total-count`
      );
      if (newsCountRes.ok) {
        const data = await newsCountRes.json();
        setNewsStats({
          total: data.total,
          thisMonth: Math.floor(data.total * 0.15),
        });
      }
    } catch (error) {
      console.error("초기 데이터 로드 실패:", error);
    }
  };

  // 🚀 캐시에서 온실가스 데이터 로드 (API 호출 없음)
  const loadEmissionsDataFromCache = (
    orgId: number,
    cache?: Map<number, any[]>
  ) => {
    const org = emissionsOrgs.find((o) => o.id === orgId);
    if (!org) {
      console.warn(`⚠️ 온실가스 조직을 찾을 수 없음: ID ${orgId}`);
      return;
    }

    const dataCache = cache || emissionsCache;
    const emissions = dataCache.get(orgId) || [];

    console.log(`📊 ${org.name} 온실가스 데이터:`, emissions);

    if (emissions.length > 0) {
      const latest = emissions.sort((a: any, b: any) => b.year - a.year)[0];
      setEmissionsOrgData({
        id: orgId,
        name: org.name,
        emissions: latest.totalEmissions || 0,
        emissionsYear: latest.year || 0,
      });
      console.log(
        `✅ ${org.name} 배출량: ${fmt.format(latest.totalEmissions)} tCO₂e (${
          latest.year
        }년)`
      );
    } else {
      console.warn(`⚠️ ${org.name}에 온실가스 데이터가 없습니다`);
      setEmissionsOrgData(null);
    }
  };

  // 🚀 캐시에서 기부금 데이터 로드 (API 호출 없음)
  const loadDonationsDataFromCache = (
    orgId: number,
    cache?: Map<number, any[]>
  ) => {
    const org = donationsOrgs.find((o) => o.id === orgId);
    if (!org) {
      console.warn(`⚠️ 기부금 조직을 찾을 수 없음: ID ${orgId}`);
      return;
    }

    const dataCache = cache || donationsCache;
    const donations = dataCache.get(orgId) || [];

    console.log(`📊 ${org.name} 기부금 데이터:`, donations);

    if (donations.length > 0) {
      const latest = donations.sort((a: any, b: any) => b.year - a.year)[0];

      const amount = latest.amount || 0;

      setDonationsOrgData({
        id: orgId,
        name: org.name,
        donations: amount,
        donationsYear: latest.year || 0,
      });
      console.log(
        `✅ ${org.name} 기부금: ${fmt.format(amount)}원 (${latest.year}년)`
      );
    } else {
      console.warn(`⚠️ ${org.name}에 기부금 데이터가 없습니다`);
      setDonationsOrgData(null);
    }
  };

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
            <div
              style={{
                padding: "10px",
                background: COLORS.accent,
                borderRadius: "12px",
              }}
            >
              <Activity
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
                Social Impact Tracker
              </h1>
              <p
                style={{
                  fontSize: "14px",
                  color: COLORS.secondary,
                  fontWeight: 500,
                }}
              >
                실시간 기업 ESG 데이터 분석 플랫폼
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

      {/* 메인 콘텐츠 */}
      <main
        style={{
          maxWidth: "1400px",
          margin: "0 auto",
          padding: "32px",
        }}
      >
        {/* Hero 슬라이드 섹션 */}
        <div
          style={{
            position: "relative",
            height: "400px",
            borderRadius: "16px",
            overflow: "hidden",
            marginBottom: "32px",
            boxShadow: "0 4px 20px rgba(0,0,0,0.1)",
          }}
        >
          {SLIDE_IMAGES.map((slide, index) => (
            <div
              key={index}
              style={{
                position: "absolute",
                top: 0,
                left: 0,
                width: "100%",
                height: "100%",
                opacity: currentSlide === index ? 1 : 0,
                transition: "opacity 1s ease-in-out",
                backgroundImage: `linear-gradient(rgba(15, 23, 42, 0.6), rgba(15, 23, 42, 0.6)), url(${slide.url})`,
                backgroundSize: "cover",
                backgroundPosition: "center",
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
                color: "white",
              }}
            >
              <Badge
                style={{
                  marginBottom: "16px",
                  background: "rgba(255,255,255,0.2)",
                  color: "white",
                  border: "1px solid rgba(255,255,255,0.3)",
                  padding: "6px 14px",
                  fontSize: "13px",
                  fontWeight: 600,
                }}
              >
                <CheckCircle2
                  style={{ width: "14px", height: "14px", marginRight: "6px" }}
                />
                실시간 업데이트
              </Badge>
              <h2
                style={{
                  fontSize: "48px",
                  fontWeight: 800,
                  marginBottom: "12px",
                  textAlign: "center",
                  textShadow: "0 2px 10px rgba(0,0,0,0.3)",
                }}
              >
                {slide.title}
              </h2>
              <p
                style={{
                  fontSize: "20px",
                  color: "rgba(255,255,255,0.9)",
                  textAlign: "center",
                  maxWidth: "600px",
                  textShadow: "0 1px 5px rgba(0,0,0,0.3)",
                }}
              >
                {slide.subtitle}
              </p>
            </div>
          ))}

          {/* 슬라이드 인디케이터 */}
          <div
            style={{
              position: "absolute",
              bottom: "24px",
              left: "50%",
              transform: "translateX(-50%)",
              display: "flex",
              gap: "8px",
              zIndex: 10,
            }}
          >
            {SLIDE_IMAGES.map((_, index) => (
              <button
                key={index}
                onClick={() => setCurrentSlide(index)}
                style={{
                  width: currentSlide === index ? "32px" : "8px",
                  height: "8px",
                  borderRadius: "4px",
                  background:
                    currentSlide === index ? "white" : "rgba(255,255,255,0.5)",
                  border: "none",
                  cursor: "pointer",
                  transition: "all 0.3s",
                }}
              />
            ))}
          </div>
        </div>

        {/* 메인 카드 섹션 */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "2fr 1fr 1fr",
            gap: "24px",
            marginBottom: "32px",
          }}
        >
          {/* 뉴스 메인 카드 */}
          <Card
            onClick={() => navigate("/news")}
            style={{
              borderRadius: "16px",
              border: `2px solid ${COLORS.accent}`,
              background: `linear-gradient(135deg, ${COLORS.accent}15 0%, ${COLORS.accent}05 100%)`,
              cursor: "pointer",
              transition: "all 0.3s",
              position: "relative",
              overflow: "hidden",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "translateY(-4px)";
              e.currentTarget.style.boxShadow =
                "0 8px 30px rgba(14, 165, 233, 0.25)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "translateY(0)";
              e.currentTarget.style.boxShadow = "none";
            }}
          >
            <div
              style={{
                position: "absolute",
                top: 0,
                right: 0,
                width: "200px",
                height: "200px",
                background: `radial-gradient(circle, ${COLORS.accent}20 0%, transparent 70%)`,
                pointerEvents: "none",
              }}
            />

            <CardContent
              style={{ padding: "32px", position: "relative", zIndex: 1 }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "start",
                  justifyContent: "space-between",
                  marginBottom: "20px",
                }}
              >
                <div
                  style={{
                    padding: "14px",
                    background: COLORS.accent,
                    borderRadius: "14px",
                    boxShadow: "0 4px 12px rgba(14, 165, 233, 0.3)",
                  }}
                >
                  <Newspaper
                    style={{ width: "32px", height: "32px", color: "white" }}
                  />
                </div>
                <Badge
                  style={{
                    background: COLORS.success,
                    color: "white",
                    padding: "6px 12px",
                    fontSize: "13px",
                    fontWeight: 600,
                  }}
                >
                  <TrendingUp
                    style={{
                      width: "14px",
                      height: "14px",
                      marginRight: "4px",
                    }}
                  />
                  실시간 업데이트
                </Badge>
              </div>

              <h3
                style={{
                  fontSize: "28px",
                  fontWeight: 800,
                  color: COLORS.primary,
                  marginBottom: "8px",
                }}
              >
                긍정 뉴스
              </h3>

              <p
                style={{
                  fontSize: "15px",
                  color: COLORS.secondary,
                  marginBottom: "24px",
                  lineHeight: "1.6",
                }}
              >
                기업의 ESG 활동과 사회공헌 소식
              </p>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "16px",
                  marginBottom: "20px",
                }}
              >
                <div
                  style={{
                    padding: "16px",
                    background: "white",
                    borderRadius: "12px",
                    border: `1px solid ${COLORS.border}`,
                  }}
                >
                  <div
                    style={{
                      fontSize: "28px",
                      fontWeight: 800,
                      color: COLORS.accent,
                      marginBottom: "4px",
                    }}
                  >
                    {fmt.format(newsStats.total)}
                  </div>
                  <div
                    style={{
                      fontSize: "12px",
                      color: COLORS.secondary,
                      fontWeight: 600,
                    }}
                  >
                    총 뉴스 수
                  </div>
                </div>

                <div
                  style={{
                    padding: "16px",
                    background: "white",
                    borderRadius: "12px",
                    border: `1px solid ${COLORS.border}`,
                  }}
                >
                  <div
                    style={{
                      fontSize: "28px",
                      fontWeight: 800,
                      color: COLORS.success,
                      marginBottom: "4px",
                    }}
                  >
                    +{newsStats.thisMonth}
                  </div>
                  <div
                    style={{
                      fontSize: "12px",
                      color: COLORS.secondary,
                      fontWeight: 600,
                    }}
                  >
                    이번 달
                  </div>
                </div>
              </div>

              <Button
                style={{
                  width: "100%",
                  background: COLORS.accent,
                  color: "white",
                  fontWeight: 600,
                  padding: "12px",
                  borderRadius: "10px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "8px",
                  border: "none",
                  cursor: "pointer",
                }}
              >
                전체 뉴스 보기
                <ArrowRight style={{ width: "18px", height: "18px" }} />
              </Button>
            </CardContent>
          </Card>

          {/* 온실가스 배출량 카드 */}
          <Card
            onClick={() => navigate("/emissions")}
            style={{
              borderRadius: "16px",
              border: `2px solid #10B981`,
              background: `linear-gradient(135deg, #10B98115 0%, #10B98105 100%)`,
              cursor: "pointer",
              transition: "all 0.3s",
              position: "relative",
              overflow: "hidden",
              opacity: isEmissionsTransitioning ? 0.7 : 1,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "translateY(-4px)";
              e.currentTarget.style.boxShadow =
                "0 8px 30px rgba(16, 185, 129, 0.25)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "translateY(0)";
              e.currentTarget.style.boxShadow = "none";
            }}
          >
            <div
              style={{
                position: "absolute",
                top: 0,
                right: 0,
                width: "150px",
                height: "150px",
                background: `radial-gradient(circle, #10B98120 0%, transparent 70%)`,
                pointerEvents: "none",
              }}
            />

            <CardContent
              style={{ padding: "24px", position: "relative", zIndex: 1 }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  marginBottom: "16px",
                }}
              >
                <div
                  style={{
                    padding: "10px",
                    background: "#10B981",
                    borderRadius: "10px",
                  }}
                >
                  <Leaf
                    style={{ width: "20px", height: "20px", color: "white" }}
                  />
                </div>
                <ChevronRight
                  style={{
                    marginLeft: "auto",
                    width: "20px",
                    height: "20px",
                    color: COLORS.secondary,
                  }}
                />
              </div>

              <h3
                style={{
                  fontSize: "18px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "8px",
                }}
              >
                온실가스 배출량
              </h3>

              <p
                style={{
                  fontSize: "14px",
                  color: COLORS.secondary,
                  marginBottom: "16px",
                  lineHeight: "1.5",
                }}
              >
                기업의 탄소배출 현황 추적
              </p>

              {emissionsOrgData && (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "8px",
                    marginBottom: "16px",
                    padding: "10px 12px",
                    background: "#10B98110",
                    borderRadius: "8px",
                    border: "1px solid #10B98130",
                  }}
                >
                  <Building2
                    style={{ width: "14px", height: "14px", color: "#10B981" }}
                  />
                  <span
                    style={{
                      fontSize: "13px",
                      fontWeight: 600,
                      color: COLORS.primary,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {emissionsOrgData.name}
                  </span>
                </div>
              )}

              <div
                style={{
                  marginBottom: "20px",
                  paddingTop: "16px",
                  borderTop: `1px solid ${COLORS.border}`,
                }}
              >
                <div
                  style={{
                    fontSize: "32px",
                    fontWeight: 800,
                    color: "#10B981",
                    marginBottom: "4px",
                  }}
                >
                  {emissionsOrgData
                    ? fmt.format(emissionsOrgData.emissions)
                    : "0"}
                </div>
                <div
                  style={{
                    fontSize: "13px",
                    color: COLORS.secondary,
                    fontWeight: 600,
                  }}
                >
                  tCO₂e{" "}
                  {emissionsOrgData?.emissionsYear
                    ? `(${emissionsOrgData.emissionsYear}년)`
                    : ""}
                </div>
              </div>

              <Button
                onClick={(e) => {
                  e.stopPropagation();
                  navigate("/emissions");
                }}
                style={{
                  width: "100%",
                  background: "#10B981",
                  color: "white",
                  fontWeight: 600,
                  padding: "10px",
                  borderRadius: "8px",
                  fontSize: "13px",
                  border: "none",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "6px",
                }}
              >
                상세보기
                <BarChart3 style={{ width: "16px", height: "16px" }} />
              </Button>
            </CardContent>
          </Card>

          {/* 기부금 카드 */}
          <Card
            onClick={() => navigate("/donations")}
            style={{
              borderRadius: "16px",
              border: `2px solid #6366F1`,
              background: `linear-gradient(135deg, #6366F115 0%, #6366F105 100%)`,
              cursor: "pointer",
              transition: "all 0.3s",
              position: "relative",
              overflow: "hidden",
              opacity: isDonationsTransitioning ? 0.7 : 1,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "translateY(-4px)";
              e.currentTarget.style.boxShadow =
                "0 8px 30px rgba(99, 102, 241, 0.25)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "translateY(0)";
              e.currentTarget.style.boxShadow = "none";
            }}
          >
            <div
              style={{
                position: "absolute",
                top: 0,
                right: 0,
                width: "150px",
                height: "150px",
                background: `radial-gradient(circle, #6366F120 0%, transparent 70%)`,
                pointerEvents: "none",
              }}
            />

            <CardContent
              style={{ padding: "24px", position: "relative", zIndex: 1 }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  marginBottom: "16px",
                }}
              >
                <div
                  style={{
                    padding: "10px",
                    background: "#6366F1",
                    borderRadius: "10px",
                  }}
                >
                  <DollarSign
                    style={{ width: "20px", height: "20px", color: "white" }}
                  />
                </div>
                <ChevronRight
                  style={{
                    marginLeft: "auto",
                    width: "20px",
                    height: "20px",
                    color: COLORS.secondary,
                  }}
                />
              </div>

              <h3
                style={{
                  fontSize: "18px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "8px",
                }}
              >
                기부금
              </h3>

              <p
                style={{
                  fontSize: "14px",
                  color: COLORS.secondary,
                  marginBottom: "16px",
                  lineHeight: "1.5",
                }}
              >
                기업의 사회공헌 기부금 내역
              </p>

              {donationsOrgData && (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "8px",
                    marginBottom: "16px",
                    padding: "10px 12px",
                    background: "#6366F110",
                    borderRadius: "8px",
                    border: "1px solid #6366F130",
                  }}
                >
                  <Building2
                    style={{ width: "14px", height: "14px", color: "#6366F1" }}
                  />
                  <span
                    style={{
                      fontSize: "13px",
                      fontWeight: 600,
                      color: COLORS.primary,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {donationsOrgData.name}
                  </span>
                </div>
              )}

              <div
                style={{
                  marginBottom: "20px",
                  paddingTop: "16px",
                  borderTop: `1px solid ${COLORS.border}`,
                }}
              >
                <div
                  style={{
                    fontSize: "32px",
                    fontWeight: 800,
                    color: "#6366F1",
                    marginBottom: "4px",
                  }}
                >
                  {donationsOrgData
                    ? fmt.format(Math.floor(donationsOrgData.donations / 1000))
                    : "0"}
                </div>
                <div
                  style={{
                    fontSize: "13px",
                    color: COLORS.secondary,
                    fontWeight: 600,
                  }}
                >
                  천원{" "}
                  {donationsOrgData?.donationsYear
                    ? `(${donationsOrgData.donationsYear}년)`
                    : ""}
                </div>
              </div>

              <Button
                onClick={(e) => {
                  e.stopPropagation();
                  navigate("/donations");
                }}
                style={{
                  width: "100%",
                  background: "#6366F1",
                  color: "white",
                  fontWeight: 600,
                  padding: "10px",
                  borderRadius: "8px",
                  fontSize: "13px",
                  border: "none",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "6px",
                }}
              >
                상세보기
                <BarChart3 style={{ width: "16px", height: "16px" }} />
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* ✨ AI 채팅 섹션 - 새로 추가! */}
        <div
          style={{
            position: "relative",
            borderRadius: "20px",
            overflow: "hidden",
            marginBottom: "32px",
            background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
            boxShadow: "0 10px 40px rgba(139, 92, 246, 0.3)",
          }}
        >
          {/* 배경 장식 */}
          <div
            style={{
              position: "absolute",
              top: "-50%",
              right: "-20%",
              width: "500px",
              height: "500px",
              background: "rgba(255, 255, 255, 0.1)",
              borderRadius: "50%",
              animation: "float 6s ease-in-out infinite",
            }}
          />
          <div
            style={{
              position: "absolute",
              bottom: "-30%",
              left: "-10%",
              width: "400px",
              height: "400px",
              background: "rgba(255, 255, 255, 0.08)",
              borderRadius: "50%",
              animation: "float 8s ease-in-out infinite",
              animationDelay: "2s",
            }}
          />

          <div
            style={{
              position: "relative",
              zIndex: 1,
              padding: "48px",
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: "48px",
              alignItems: "center",
            }}
          >
            {/* 왼쪽 - 텍스트 콘텐츠 */}
            <div>
              <Badge
                style={{
                  background: "rgba(255, 255, 255, 0.25)",
                  color: "white",
                  border: "1px solid rgba(255, 255, 255, 0.3)",
                  padding: "6px 14px",
                  fontSize: "13px",
                  fontWeight: 600,
                  marginBottom: "20px",
                }}
              >
                <Sparkles
                  style={{ width: "14px", height: "14px", marginRight: "6px" }}
                />
                AI 기반 분석
              </Badge>

              <h2
                style={{
                  fontSize: "42px",
                  fontWeight: 800,
                  color: "white",
                  marginBottom: "16px",
                  lineHeight: "1.2",
                }}
              >
                AI와 채팅하기
              </h2>

              <p
                style={{
                  fontSize: "18px",
                  color: "rgba(255, 255, 255, 0.95)",
                  marginBottom: "32px",
                  lineHeight: "1.6",
                }}
              >
                최신 AI 기술로 임팩트 데이터를 분석하고, 전략적 인사이트를
                얻으세요.
              </p>

              {/* 기능 카드들 */}
              <div
                style={{
                  display: "grid",
                  gap: "12px",
                  marginBottom: "32px",
                }}
              >
                {[
                  {
                    icon: Brain,
                    title: "스마트 분석",
                    desc: "데이터 기반 인사이트 제공",
                  },
                  {
                    icon: BarChart3,
                    title: "맞춤형 리포트",
                    desc: "실시간 질의응답 및 리포트 생성",
                  },
                  {
                    icon: Zap,
                    title: "전략 수립",
                    desc: "AI 기반 임팩트 전략 제안",
                  },
                ].map((feature, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "16px",
                      padding: "16px 20px",
                      background: "rgba(255, 255, 255, 0.15)",
                      backdropFilter: "blur(10px)",
                      borderRadius: "12px",
                      border: "1px solid rgba(255, 255, 255, 0.2)",
                      transition: "all 0.3s",
                      cursor: "pointer",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.background =
                        "rgba(255, 255, 255, 0.2)";
                      e.currentTarget.style.transform = "translateX(4px)";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.background =
                        "rgba(255, 255, 255, 0.15)";
                      e.currentTarget.style.transform = "translateX(0)";
                    }}
                  >
                    <div
                      style={{
                        width: "48px",
                        height: "48px",
                        background: "white",
                        borderRadius: "12px",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        flexShrink: 0,
                      }}
                    >
                      <feature.icon
                        style={{
                          width: "24px",
                          height: "24px",
                          color: COLORS.aiPurple,
                        }}
                      />
                    </div>
                    <div style={{ flex: 1 }}>
                      <h4
                        style={{
                          fontSize: "16px",
                          fontWeight: 700,
                          color: "white",
                          marginBottom: "4px",
                        }}
                      >
                        {feature.title}
                      </h4>
                      <p
                        style={{
                          fontSize: "14px",
                          color: "rgba(255, 255, 255, 0.85)",
                          margin: 0,
                        }}
                      >
                        {feature.desc}
                      </p>
                    </div>
                  </div>
                ))}
              </div>

              {/* CTA 버튼 */}
              <Button
                onClick={() => navigate("/ai-chat")}
                style={{
                  background: "white",
                  color: COLORS.aiPurple,
                  fontWeight: 700,
                  fontSize: "18px",
                  padding: "16px 32px",
                  borderRadius: "14px",
                  border: "none",
                  cursor: "pointer",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "10px",
                  boxShadow: "0 8px 24px rgba(0, 0, 0, 0.2)",
                  transition: "all 0.3s",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform =
                    "translateY(-3px) scale(1.05)";
                  e.currentTarget.style.boxShadow =
                    "0 12px 32px rgba(0, 0, 0, 0.3)";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = "translateY(0) scale(1)";
                  e.currentTarget.style.boxShadow =
                    "0 8px 24px rgba(0, 0, 0, 0.2)";
                }}
              >
                <MessageCircle style={{ width: "22px", height: "22px" }} />
                지금 대화 시작하기
                <ArrowRight style={{ width: "20px", height: "20px" }} />
              </Button>
            </div>

            {/* 오른쪽 - 채팅 프리뷰 */}
            <div
              style={{
                background: "white",
                borderRadius: "20px",
                padding: "28px",
                boxShadow: "0 20px 60px rgba(0, 0, 0, 0.3)",
                maxWidth: "450px",
              }}
            >
              {/* 채팅 헤더 */}
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "14px",
                  marginBottom: "24px",
                  paddingBottom: "18px",
                  borderBottom: `2px solid ${COLORS.border}`,
                }}
              >
                <div
                  style={{
                    width: "50px",
                    height: "50px",
                    background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                    borderRadius: "50%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Bot
                    style={{ width: "28px", height: "28px", color: "white" }}
                  />
                </div>
                <div>
                  <h4
                    style={{
                      fontSize: "17px",
                      fontWeight: 700,
                      color: COLORS.primary,
                      marginBottom: "2px",
                    }}
                  >
                    Impact AI Assistant
                  </h4>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "6px",
                    }}
                  >
                    <div
                      style={{
                        width: "8px",
                        height: "8px",
                        background: COLORS.success,
                        borderRadius: "50%",
                        animation: "pulse 2s infinite",
                      }}
                    />
                    <span style={{ fontSize: "13px", color: COLORS.secondary }}>
                      온라인
                    </span>
                  </div>
                </div>
              </div>

              {/* 채팅 메시지들 */}
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: "12px",
                }}
              >
                <div
                  style={{
                    padding: "14px 16px",
                    background: "#F5F3FF",
                    borderRadius: "16px",
                    fontSize: "15px",
                    color: COLORS.primary,
                    lineHeight: "1.5",
                    maxWidth: "85%",
                  }}
                >
                  안녕하세요! 임팩트 데이터 분석을 도와드릴게요. 📊
                </div>

                <div
                  style={{
                    padding: "14px 16px",
                    background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                    borderRadius: "16px",
                    fontSize: "15px",
                    color: "white",
                    lineHeight: "1.5",
                    maxWidth: "85%",
                    alignSelf: "flex-end",
                  }}
                >
                  최근 프로젝트 성과를 분석해줄 수 있나요?
                </div>

                <div
                  style={{
                    padding: "14px 16px",
                    background: "#F5F3FF",
                    borderRadius: "16px",
                    fontSize: "15px",
                    color: COLORS.primary,
                    lineHeight: "1.5",
                    maxWidth: "85%",
                  }}
                >
                  물론이죠! 지난 분기 데이터를 분석해드리겠습니다. 💡
                </div>

                {/* 타이핑 인디케이터 */}
                <div
                  style={{
                    padding: "14px 16px",
                    background: "#F5F3FF",
                    borderRadius: "16px",
                    maxWidth: "fit-content",
                    display: "flex",
                    gap: "6px",
                  }}
                >
                  {[0, 1, 2].map((i) => (
                    <div
                      key={i}
                      style={{
                        width: "8px",
                        height: "8px",
                        background: COLORS.aiPurple,
                        borderRadius: "50%",
                        animation: "typing 1.4s infinite",
                        animationDelay: `${i * 0.2}s`,
                      }}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* 최근 뉴스 섹션 */}
        <div
          style={{
            background: COLORS.cardBg,
            borderRadius: "16px",
            border: `1px solid ${COLORS.border}`,
            padding: "32px",
            boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: "24px",
            }}
          >
            <div>
              <h3
                style={{
                  fontSize: "20px",
                  fontWeight: 700,
                  color: COLORS.primary,
                  marginBottom: "4px",
                }}
              >
                최근 긍정 뉴스
              </h3>
              <p
                style={{
                  fontSize: "14px",
                  color: COLORS.secondary,
                }}
              >
                기업의 최신 ESG 활동 소식
              </p>
            </div>
            <Button
              onClick={() => navigate("/news")}
              variant="outline"
              style={{
                display: "flex",
                alignItems: "center",
                gap: "6px",
                padding: "10px 20px",
                borderRadius: "10px",
                fontWeight: 600,
                fontSize: "14px",
                border: `1px solid ${COLORS.border}`,
                background: "white",
                color: COLORS.primary,
                cursor: "pointer",
              }}
            >
              전체보기
              <ExternalLink style={{ width: "16px", height: "16px" }} />
            </Button>
          </div>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(3, 1fr)",
              gap: "20px",
            }}
          >
            {recentNews.length > 0 ? (
              recentNews.slice(0, 3).map((news, idx) => (
                <Card
                  key={idx}
                  style={{
                    borderRadius: "12px",
                    border: `1px solid ${COLORS.border}`,
                    cursor: "pointer",
                    transition: "all 0.3s",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = "translateY(-2px)";
                    e.currentTarget.style.boxShadow =
                      "0 4px 12px rgba(0,0,0,0.1)";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = "translateY(0)";
                    e.currentTarget.style.boxShadow = "none";
                  }}
                  onClick={() => window.open(news.url, "_blank")}
                >
                  <CardContent style={{ padding: "20px" }}>
                    <Badge
                      style={{
                        background: "#10B98115",
                        color: COLORS.success,
                        fontSize: "11px",
                        fontWeight: 600,
                        padding: "4px 10px",
                        marginBottom: "12px",
                      }}
                    >
                      {news.category}
                    </Badge>

                    <h4
                      style={{
                        fontSize: "15px",
                        fontWeight: 700,
                        color: COLORS.primary,
                        marginBottom: "8px",
                        lineHeight: "1.4",
                        display: "-webkit-box",
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: "vertical",
                        overflow: "hidden",
                      }}
                    >
                      {news.title}
                    </h4>

                    <p
                      style={{
                        fontSize: "13px",
                        color: COLORS.secondary,
                        lineHeight: "1.5",
                        display: "-webkit-box",
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: "vertical",
                        overflow: "hidden",
                        marginBottom: "12px",
                      }}
                    >
                      {news.description}
                    </p>

                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "8px",
                        paddingTop: "12px",
                        borderTop: `1px solid ${COLORS.border}`,
                      }}
                    >
                      <Calendar
                        style={{
                          width: "13px",
                          height: "13px",
                          color: COLORS.secondary,
                        }}
                      />
                      <span
                        style={{ fontSize: "12px", color: COLORS.secondary }}
                      >
                        {news.publishedDate}
                      </span>
                    </div>
                  </CardContent>
                </Card>
              ))
            ) : (
              // Mock 데이터
              <>
                <Card
                  style={{
                    borderRadius: "12px",
                    border: `1px solid ${COLORS.border}`,
                  }}
                >
                  <CardContent style={{ padding: "20px" }}>
                    <Badge
                      style={{
                        background: "#10B98115",
                        color: COLORS.success,
                        fontSize: "11px",
                        fontWeight: 600,
                        padding: "4px 10px",
                        marginBottom: "12px",
                      }}
                    >
                      환경
                    </Badge>
                    <h4
                      style={{
                        fontSize: "15px",
                        fontWeight: 700,
                        color: COLORS.primary,
                        marginBottom: "8px",
                      }}
                    >
                      탄소중립 달성을 위한 신기술 도입
                    </h4>
                    <p
                      style={{
                        fontSize: "13px",
                        color: COLORS.secondary,
                        lineHeight: "1.5",
                      }}
                    >
                      친환경 에너지 전환 프로젝트 시작
                    </p>
                  </CardContent>
                </Card>
                <Card
                  style={{
                    borderRadius: "12px",
                    border: `1px solid ${COLORS.border}`,
                  }}
                >
                  <CardContent style={{ padding: "20px" }}>
                    <Badge
                      style={{
                        background: "#8B5CF615",
                        color: "#8B5CF6",
                        fontSize: "11px",
                        fontWeight: 600,
                        padding: "4px 10px",
                        marginBottom: "12px",
                      }}
                    >
                      사회공헌
                    </Badge>
                    <h4
                      style={{
                        fontSize: "15px",
                        fontWeight: 700,
                        color: COLORS.primary,
                        marginBottom: "8px",
                      }}
                    >
                      지역사회 교육 지원 프로그램 확대
                    </h4>
                    <p
                      style={{
                        fontSize: "13px",
                        color: COLORS.secondary,
                        lineHeight: "1.5",
                      }}
                    >
                      소외계층 학생 500명에게 장학금 전달
                    </p>
                  </CardContent>
                </Card>
                <Card
                  style={{
                    borderRadius: "12px",
                    border: `1px solid ${COLORS.border}`,
                  }}
                >
                  <CardContent style={{ padding: "20px" }}>
                    <Badge
                      style={{
                        background: "#F59E0B15",
                        color: COLORS.warning,
                        fontSize: "11px",
                        fontWeight: 600,
                        padding: "4px 10px",
                        marginBottom: "12px",
                      }}
                    >
                      혁신
                    </Badge>
                    <h4
                      style={{
                        fontSize: "15px",
                        fontWeight: 700,
                        color: COLORS.primary,
                        marginBottom: "8px",
                      }}
                    >
                      AI 기반 친환경 물류 시스템 구축
                    </h4>
                    <p
                      style={{
                        fontSize: "13px",
                        color: COLORS.secondary,
                        lineHeight: "1.5",
                      }}
                    >
                      배송 효율성 30% 향상 및 탄소 절감
                    </p>
                  </CardContent>
                </Card>
              </>
            )}
          </div>
        </div>
      </main>

      {/* 애니메이션 CSS */}
      <style>{`
        @keyframes float {
          0%, 100% { transform: translateY(0px); }
          50% { transform: translateY(-30px); }
        }
        
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }
        
        @keyframes typing {
          0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
          30% { transform: translateY(-10px); opacity: 1; }
        }
      `}</style>
    </div>
  );
}
