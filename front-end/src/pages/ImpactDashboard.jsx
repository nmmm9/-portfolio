import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import {
  Leaf,
  DollarSign,
  Newspaper,
  ChevronRight,
  ChevronDown,
  MessageCircle,
} from "lucide-react";
import "./ImpactDashboard.css";

const fmt = new Intl.NumberFormat("ko-KR");

export default function ImpactDashboard() {
  const navigate = useNavigate();
  const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

  const [loading, setLoading] = useState(true);
  const [currentSection, setCurrentSection] = useState(0);
  const [isScrolling, setIsScrolling] = useState(false);
  const containerRef = useRef(null);
  const totalSections = 3;

  const [stats, setStats] = useState({
    newsCount: 0,
    orgsCount: 0,
    emissionsTotal: 0,
    donationsTotal: 0,
  });
  const [recentNews, setRecentNews] = useState([]);

  useEffect(() => {
    loadDashboardData();
  }, []);

  // 휠 이벤트 핸들러 - 강제 섹션 스크롤
  const isScrollingRef = useRef(false);
  
  useEffect(() => {
    const handleWheel = (e) => {
      e.preventDefault();
      e.stopPropagation();
      
      // 스크롤 중이면 무시 (useRef로 즉시 체크)
      if (isScrollingRef.current) return;
      
      // 작은 스크롤 무시 (터치패드 민감도 대응)
      if (Math.abs(e.deltaY) < 30) return;

      const direction = e.deltaY > 0 ? 1 : -1;
      const nextSection = currentSection + direction;

      if (nextSection >= 0 && nextSection < totalSections) {
        isScrollingRef.current = true;
        setIsScrolling(true);
        setCurrentSection(nextSection);
        
        // 1.2초 쿨다운 (애니메이션 0.8초 + 버퍼)
        setTimeout(() => {
          isScrollingRef.current = false;
          setIsScrolling(false);
        }, 1200);
      }
    };

    document.addEventListener("wheel", handleWheel, { passive: false });

    return () => {
      document.removeEventListener("wheel", handleWheel);
    };
  }, [currentSection]);

  // 키보드 이벤트
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (isScrolling) return;

      if (e.key === "ArrowDown" || e.key === "PageDown") {
        e.preventDefault();
        goToSection(currentSection + 1);
      } else if (e.key === "ArrowUp" || e.key === "PageUp") {
        e.preventDefault();
        goToSection(currentSection - 1);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [currentSection, isScrolling]);

  const goToSection = (index) => {
    if (index >= 0 && index < totalSections && !isScrolling) {
      setIsScrolling(true);
      setCurrentSection(index);
      setTimeout(() => setIsScrolling(false), 1000);
    }
  };

  const loadDashboardData = async () => {
    setLoading(true);
    try {
      // 병렬 API 호출로 성능 최적화
      const [summaryRes, newsRes] = await Promise.all([
        fetch(`${API_BASE}/api/dashboard/summary`),
        fetch(`${API_BASE}/api/positive-news/search?keyword=&page=0&size=4`)
      ]);

      const summaryData = summaryRes.ok ? await summaryRes.json() : null;
      const newsData = newsRes.ok ? await newsRes.json() : { content: [], totalElements: 0 };

      if (summaryData) {
        setStats({
          newsCount: summaryData.newsCount || newsData.totalElements || 0,
          orgsCount: summaryData.organizationsCount || 0,
          emissionsTotal: Number(summaryData.totalCo2Reduced) || 0,
          donationsTotal: Number(summaryData.totalDonation) || 0,
        });
      }

      setRecentNews(newsData.content || []);
    } catch (error) {
      console.error("Dashboard load error:", error);
    } finally {
      setLoading(false);
    }
  };

  const categories = [
    {
      title: "긍정 뉴스",
      desc: "ESG 활동 및 사회공헌 관련 뉴스",
      path: "/positive-news",
      stat: `${fmt.format(stats.newsCount)}건`,
    },
    {
      title: "온실가스 배출량",
      desc: "기업별 탄소 배출량 데이터",
      path: "/emissions",
      stat: `${fmt.format(Math.round(stats.emissionsTotal))} tCO₂e`,
    },
    {
      title: "기부금 현황",
      desc: "사회공헌 기부금 통계",
      path: "/donations",
      stat: `${fmt.format(Math.round(stats.donationsTotal / 100000000))}억원`,
    },
    {
      title: "챗봇",
      desc: "임팩트 데이터에 대해 질문하기",
      path: "/ai-chat",
      stat: "→",
    },
  ];

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-content">
          <div className="loading-spinner" />
          <p>데이터를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard" ref={containerRef}>
      {/* 페이지 인디케이터 */}
      <div className="page-indicators">
        {[0, 1, 2].map((idx) => (
          <button
            key={idx}
            className={`page-dot ${currentSection === idx ? "active" : ""} ${currentSection === 0 ? "light" : ""}`}
            onClick={() => goToSection(idx)}
          />
        ))}
      </div>

      {/* 섹션 컨테이너 */}
      <div
        className="sections-container"
        style={{ transform: `translateY(-${currentSection * 100}vh)` }}
      >
        {/* 히어로 섹션 */}
        <section className="section section-hero">
          <div className="hero-content">
            <span className="hero-badge">SOCIAL IMPACT TRACKER</span>
            <h1 className="hero-title">
              기업의 사회적 가치를<br />측정하고 분석합니다
            </h1>
            <p className="hero-subtitle">
              ESG 경영, 탄소 배출, 사회공헌 활동 등<br />
              기업의 지속가능성 데이터를 한눈에 확인하세요
            </p>

            <div className="hero-stats">
              <div className="hero-stat">
                <span className="hero-stat-value">{fmt.format(stats.orgsCount)}</span>
                <span className="hero-stat-label">등록 기업</span>
              </div>
              <div className="hero-stat">
                <span className="hero-stat-value">{fmt.format(stats.newsCount)}</span>
                <span className="hero-stat-label">긍정 뉴스</span>
              </div>
              <div className="hero-stat">
                <span className="hero-stat-value">{fmt.format(Math.round(stats.donationsTotal / 100000000))}</span>
                <span className="hero-stat-label">총 기부금 (억원)</span>
              </div>
            </div>
          </div>

          <div className="scroll-indicator" onClick={() => goToSection(1)}>
            <span>SCROLL</span>
            <ChevronDown className="scroll-arrow" />
          </div>
        </section>

        {/* 카테고리 섹션 */}
        <section className="section section-categories">
          <div className="section-inner">
            <div className="section-header">
              <p className="section-label">Explore Data</p>
              <h2 className="section-title">데이터 카테고리</h2>
            </div>

            <div className="categories-grid">
              {categories.map((cat, idx) => (
                <div
                  key={idx}
                  className="category-card"
                  onClick={() => navigate(cat.path)}
                >
                  <div className="category-content">
                    <h3 className="category-title">{cat.title}</h3>
                    <p className="category-desc">{cat.desc}</p>
                  </div>
                  <span className="category-stat">{cat.stat}</span>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* 뉴스 섹션 */}
        <section className="section section-news">
          <div className="section-inner">
            <div className="section-header">
              <p className="section-label">Latest Updates</p>
              <h2 className="section-title">최근 뉴스</h2>
            </div>

            {recentNews.length === 0 ? (
              <div className="news-empty">
                <p>뉴스 데이터가 없습니다</p>
              </div>
            ) : (
              <div className="news-list">
                {recentNews.map((news, idx) => (
                  <div
                    key={news.id || idx}
                    className="news-item"
                    onClick={() => news.url && window.open(news.url, "_blank")}
                  >
                    <div>
                      <span className="news-category">{news.category || "ESG"}</span>
                      <h4 className="news-title">{news.title}</h4>
                      <span className="news-meta">
                        {news.organizationName || news.organization?.name || "-"}
                      </span>
                    </div>
                    <span className="news-date">
                      {news.publishedDate
                        ? new Date(news.publishedDate).toLocaleDateString("ko-KR")
                        : "-"}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <button className="view-all-btn" onClick={() => navigate("/positive-news")}>
              전체 뉴스 보기
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
