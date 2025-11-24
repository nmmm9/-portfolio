import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  ArrowLeft,
  Send,
  Paperclip,
  Settings,
  BarChart3,
  Bot,
  User,
  Plus,
  TrendingUp,
  FileText,
  Target,
} from "lucide-react";

const COLORS = {
  primary: "#0F172A",
  secondary: "#64748B",
  accent: "#0EA5E9",
  success: "#10B981",
  background: "#F8FAFC",
  cardBg: "#FFFFFF",
  border: "#E2E8F0",
  aiPurple: "#8B5CF6",
  aiPink: "#EC4899",
};

interface Message {
  id: string;
  content: string;
  sender: "user" | "ai";
  timestamp: Date;
}

interface Conversation {
  id: string;
  title: string;
  preview: string;
  timestamp: Date;
}

export default function AIChatPage() {
  const navigate = useNavigate();
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const [conversations] = useState<Conversation[]>([
    {
      id: "1",
      title: "임팩트 데이터 분석",
      preview: "최근 프로젝트 성과를 분석해주실 수 있나요?",
      timestamp: new Date(),
    },
    {
      id: "2",
      title: "리포트 생성",
      preview: "Q3 분기 리포트를 만들어주세요.",
      timestamp: new Date(Date.now() - 86400000),
    },
    {
      id: "3",
      title: "데이터 시각화",
      preview: "CO2 감소량을 그래프로 보여주세요.",
      timestamp: new Date(Date.now() - 172800000),
    },
    {
      id: "4",
      title: "전략 컨설팅",
      preview: "내년 목표 설정을 도와주세요.",
      timestamp: new Date(Date.now() - 259200000),
    },
  ]);

  const suggestions = [
    "최근 CO2 감소량은 어떻게 되나요?",
    "자원봉사 시간 추이를 보여주세요",
    "가장 효과적인 프로젝트는?",
  ];

  const welcomeSuggestions = [
    {
      icon: TrendingUp,
      title: "프로젝트 성과 분석",
      desc: "최근 프로젝트의 KPI를 분석합니다",
      query: "최근 프로젝트 성과 분석",
    },
    {
      icon: FileText,
      title: "리포트 생성",
      desc: "맞춤형 임팩트 리포트를 만듭니다",
      query: "월별 리포트 생성",
    },
    {
      icon: BarChart3,
      title: "데이터 시각화",
      desc: "데이터를 그래프로 표현합니다",
      query: "데이터 시각화 요청",
    },
    {
      icon: Target,
      title: "전략 컨설팅",
      desc: "임팩트 전략을 제안합니다",
      query: "전략 컨설팅 요청",
    },
  ];

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  const handleSendMessage = (text?: string) => {
    const messageText = text || inputValue.trim();
    if (!messageText) return;

    const newMessage: Message = {
      id: Date.now().toString(),
      content: messageText,
      sender: "user",
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, newMessage]);
    setInputValue("");
    setIsTyping(true);

    // AI 응답 시뮬레이션
    setTimeout(() => {
      const aiResponse = generateAIResponse(messageText);
      const aiMessage: Message = {
        id: (Date.now() + 1).toString(),
        content: aiResponse,
        sender: "ai",
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, aiMessage]);
      setIsTyping(false);
    }, 2000);
  };

  const generateAIResponse = (userMessage: string): string => {
    const responses: Record<string, string> = {
      성과: "최근 프로젝트 성과를 분석한 결과, CO2 감소량이 전월 대비 15% 증가했으며, 자원봉사 시간은 38,540시간을 기록했습니다. 특히 Clean Water Initiative 프로젝트가 가장 높은 임팩트를 달성했습니다.",
      리포트:
        "월별 리포트를 생성했습니다. 주요 지표로는 metric tons CO2 5,320, 자원봉사 23,150시간, 기부금 $1,532,800, 승인율 92%를 기록했습니다. 상세 리포트를 다운로드하시겠습니까?",
      시각화:
        "데이터 시각화를 준비했습니다. CO2 감소량은 지난 12개월간 꾸준한 상승세를 보이고 있으며, 프로젝트별 임팩트 비교 그래프를 확인하실 수 있습니다.",
      전략: "임팩트 전략 컨설팅을 시작하겠습니다. 현재 데이터를 분석한 결과, 환경 부문에 더 많은 리소스를 투입하고, 자원봉사자 참여를 확대하는 것을 추천드립니다.",
      CO2: "최근 CO2 감소량은 5,320 metric tons를 기록했습니다. 이는 전월 대비 12% 증가한 수치이며, 주로 Green Energy Program의 기여도가 높았습니다.",
      자원봉사:
        "자원봉사 시간 추이를 보면, 지난 분기 동안 23,150시간을 기록했으며, 월평균 7,716시간의 봉사활동이 이루어졌습니다. After-School Programs에서 가장 많은 시간이 투입되었습니다.",
      프로젝트:
        "가장 효과적인 프로젝트는 Clean Water Initiative로, 1,002 tons의 임팩트를 달성했습니다. 다음으로 Green Energy Program과 Early Literacy Campaign이 높은 성과를 보였습니다.",
    };

    for (const keyword in responses) {
      if (userMessage.includes(keyword)) {
        return responses[keyword];
      }
    }

    return "질문 감사합니다! 임팩트 데이터에 대한 분석, 리포트 생성, 전략 제안 등 다양한 도움을 드릴 수 있습니다. 구체적으로 어떤 부분이 궁금하신가요?";
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleNewChat = () => {
    setMessages([]);
  };

  return (
    <div
      style={{
        height: "100vh",
        width: "100vw",
        display: "flex",
        background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
        overflow: "hidden",
      }}
    >
      {/* 사이드바 */}
      <aside
        style={{
          width: "320px",
          background: "rgba(255, 255, 255, 0.98)",
          backdropFilter: "blur(20px)",
          borderRight: `1px solid ${COLORS.border}`,
          display: "flex",
          flexDirection: "column",
          boxShadow: "2px 0 20px rgba(0, 0, 0, 0.1)",
        }}
      >
        <div
          style={{
            padding: "25px",
            borderBottom: `1px solid ${COLORS.border}`,
          }}
        >
          <Button
            onClick={() => navigate("/")}
            variant="ghost"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "10px",
              color: COLORS.aiPurple,
              fontWeight: 600,
              fontSize: "15px",
              padding: "12px 15px",
              borderRadius: "12px",
              marginBottom: "15px",
              width: "100%",
              justifyContent: "flex-start",
              background: "transparent",
              border: "none",
              cursor: "pointer",
            }}
          >
            <ArrowLeft style={{ width: "18px", height: "18px" }} />
            메인으로 돌아가기
          </Button>

          <h2
            style={{
              fontSize: "24px",
              fontWeight: 800,
              color: COLORS.primary,
              marginBottom: "8px",
            }}
          >
            대화 기록
          </h2>
          <p style={{ fontSize: "14px", color: COLORS.secondary }}>
            모든 채팅 내역
          </p>
        </div>

        <div style={{ padding: "20px" }}>
          <Button
            onClick={handleNewChat}
            style={{
              width: "100%",
              padding: "16px",
              background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
              color: "white",
              border: "none",
              borderRadius: "14px",
              fontSize: "16px",
              fontWeight: 700,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
              boxShadow: "0 4px 15px rgba(139, 92, 246, 0.3)",
            }}
          >
            <Plus style={{ width: "20px", height: "20px" }} />새 대화
          </Button>
        </div>

        <div style={{ flex: 1, overflowY: "auto", padding: "0 20px 20px" }}>
          {conversations.map((conv, idx) => (
            <div
              key={conv.id}
              style={{
                padding: "15px",
                marginBottom: "8px",
                background:
                  idx === 0
                    ? `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`
                    : "#F5F5F7",
                borderRadius: "12px",
                cursor: "pointer",
                transition: "all 0.3s",
                color: idx === 0 ? "white" : COLORS.primary,
              }}
            >
              <div
                style={{
                  fontSize: "15px",
                  fontWeight: 600,
                  marginBottom: "5px",
                }}
              >
                {conv.title}
              </div>
              <div
                style={{
                  fontSize: "13px",
                  color: idx === 0 ? "rgba(255,255,255,0.8)" : COLORS.secondary,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {conv.preview}
              </div>
            </div>
          ))}
        </div>
      </aside>

      {/* 메인 채팅 영역 */}
      <main
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          background: "white",
          margin: "20px",
          borderRadius: "24px",
          boxShadow: "0 20px 60px rgba(0, 0, 0, 0.2)",
          overflow: "hidden",
        }}
      >
        {/* 채팅 헤더 */}
        <div
          style={{
            padding: "25px 30px",
            background: "white",
            borderBottom: "2px solid #F0F0F5",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "20px" }}>
            <div
              style={{
                width: "60px",
                height: "60px",
                background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                borderRadius: "50%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 8px 20px rgba(139, 92, 246, 0.3)",
              }}
            >
              <Bot style={{ width: "32px", height: "32px", color: "white" }} />
            </div>

            <div>
              <h3
                style={{
                  fontSize: "22px",
                  fontWeight: 800,
                  color: COLORS.primary,
                  marginBottom: "5px",
                }}
              >
                Impact AI Assistant
              </h3>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  fontSize: "14px",
                  color: COLORS.secondary,
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
                온라인
              </div>
            </div>
          </div>

          <div style={{ display: "flex", gap: "12px" }}>
            <Button
              variant="outline"
              style={{
                padding: "10px 16px",
                background: "#F5F5F7",
                border: "none",
                borderRadius: "10px",
                cursor: "pointer",
                fontSize: "14px",
                fontWeight: 600,
                color: COLORS.primary,
                display: "flex",
                alignItems: "center",
                gap: "6px",
              }}
            >
              <BarChart3 style={{ width: "16px", height: "16px" }} />
              데이터 보기
            </Button>
            <Button
              variant="outline"
              style={{
                padding: "10px 16px",
                background: "#F5F5F7",
                border: "none",
                borderRadius: "10px",
                cursor: "pointer",
                fontSize: "14px",
                fontWeight: 600,
                color: COLORS.primary,
                display: "flex",
                alignItems: "center",
                gap: "6px",
              }}
            >
              <Settings style={{ width: "16px", height: "16px" }} />
              설정
            </Button>
          </div>
        </div>

        {/* 메시지 영역 */}
        <div
          style={{
            flex: 1,
            overflowY: "auto",
            padding: "30px",
            background: "#F9F9FB",
          }}
        >
          {messages.length === 0 ? (
            <div
              style={{
                textAlign: "center",
                padding: "60px 20px",
                maxWidth: "700px",
                margin: "0 auto",
              }}
            >
              <div
                style={{
                  width: "100px",
                  height: "100px",
                  background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  margin: "0 auto 30px",
                  boxShadow: "0 20px 40px rgba(139, 92, 246, 0.3)",
                }}
              >
                <Bot
                  style={{ width: "52px", height: "52px", color: "white" }}
                />
              </div>

              <h2
                style={{
                  fontSize: "32px",
                  fontWeight: 800,
                  marginBottom: "15px",
                  color: COLORS.primary,
                }}
              >
                안녕하세요!
              </h2>

              <p
                style={{
                  fontSize: "16px",
                  color: COLORS.secondary,
                  lineHeight: "1.6",
                  marginBottom: "40px",
                }}
              >
                저는 Impact AI Assistant입니다. 임팩트 데이터 분석, 리포트 생성,
                전략 수립 등 다양한 업무를 도와드릴 수 있습니다.
              </p>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(2, 1fr)",
                  gap: "15px",
                }}
              >
                {welcomeSuggestions.map((suggestion, idx) => (
                  <Card
                    key={idx}
                    onClick={() => handleSendMessage(suggestion.query)}
                    style={{
                      padding: "20px",
                      background: "white",
                      border: `2px solid ${COLORS.border}`,
                      borderRadius: "16px",
                      cursor: "pointer",
                      transition: "all 0.3s",
                      textAlign: "left",
                    }}
                  >
                    <CardContent style={{ padding: 0 }}>
                      <suggestion.icon
                        style={{
                          width: "28px",
                          height: "28px",
                          color: COLORS.aiPurple,
                          marginBottom: "12px",
                        }}
                      />
                      <h4
                        style={{
                          fontWeight: 700,
                          fontSize: "15px",
                          color: COLORS.primary,
                          marginBottom: "5px",
                        }}
                      >
                        {suggestion.title}
                      </h4>
                      <p style={{ fontSize: "13px", color: COLORS.secondary }}>
                        {suggestion.desc}
                      </p>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          ) : (
            <>
              {messages.map((message) => (
                <div
                  key={message.id}
                  style={{
                    display: "flex",
                    marginBottom: "25px",
                    justifyContent:
                      message.sender === "user" ? "flex-end" : "flex-start",
                    animation: "slideIn 0.4s ease-out",
                  }}
                >
                  {message.sender === "ai" && (
                    <div
                      style={{
                        width: "40px",
                        height: "40px",
                        borderRadius: "50%",
                        background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        marginRight: "15px",
                        flexShrink: 0,
                      }}
                    >
                      <Bot
                        style={{
                          width: "22px",
                          height: "22px",
                          color: "white",
                        }}
                      />
                    </div>
                  )}

                  <div style={{ maxWidth: "65%" }}>
                    <div
                      style={{
                        padding: "18px 22px",
                        borderRadius: "18px",
                        fontSize: "15px",
                        lineHeight: "1.6",
                        background:
                          message.sender === "ai"
                            ? "white"
                            : `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                        color:
                          message.sender === "ai" ? COLORS.primary : "white",
                        boxShadow:
                          message.sender === "ai"
                            ? "0 2px 12px rgba(0, 0, 0, 0.08)"
                            : "0 4px 15px rgba(139, 92, 246, 0.3)",
                      }}
                    >
                      {message.content}
                    </div>
                    <div
                      style={{
                        fontSize: "12px",
                        color: COLORS.secondary,
                        marginTop: "8px",
                        paddingLeft: "5px",
                      }}
                    >
                      {message.timestamp.toLocaleTimeString("ko-KR", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </div>
                  </div>

                  {message.sender === "user" && (
                    <div
                      style={{
                        width: "40px",
                        height: "40px",
                        borderRadius: "50%",
                        background: "#E8E8ED",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        marginLeft: "15px",
                        flexShrink: 0,
                      }}
                    >
                      <User
                        style={{
                          width: "22px",
                          height: "22px",
                          color: COLORS.primary,
                        }}
                      />
                    </div>
                  )}
                </div>
              ))}

              {isTyping && (
                <div
                  style={{ display: "flex", alignItems: "center", gap: "15px" }}
                >
                  <div
                    style={{
                      width: "40px",
                      height: "40px",
                      borderRadius: "50%",
                      background: `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <Bot
                      style={{ width: "22px", height: "22px", color: "white" }}
                    />
                  </div>

                  <div
                    style={{
                      padding: "18px 22px",
                      background: "white",
                      borderRadius: "18px",
                      boxShadow: "0 2px 12px rgba(0, 0, 0, 0.08)",
                      display: "flex",
                      gap: "6px",
                    }}
                  >
                    {[0, 1, 2].map((i) => (
                      <div
                        key={i}
                        style={{
                          width: "10px",
                          height: "10px",
                          background: COLORS.aiPurple,
                          borderRadius: "50%",
                          animation: "typing 1.4s infinite",
                          animationDelay: `${i * 0.2}s`,
                        }}
                      />
                    ))}
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </>
          )}
        </div>

        {/* 입력 영역 */}
        <div
          style={{
            padding: "25px 30px",
            background: "white",
            borderTop: "2px solid #F0F0F5",
          }}
        >
          {messages.length > 0 && (
            <div
              style={{
                display: "flex",
                gap: "10px",
                flexWrap: "wrap",
                marginBottom: "15px",
              }}
            >
              {suggestions.map((suggestion, idx) => (
                <Badge
                  key={idx}
                  onClick={() => handleSendMessage(suggestion)}
                  style={{
                    padding: "10px 18px",
                    background: "white",
                    border: `2px solid ${COLORS.border}`,
                    borderRadius: "20px",
                    fontSize: "14px",
                    cursor: "pointer",
                    color: COLORS.aiPurple,
                    fontWeight: 600,
                  }}
                >
                  {suggestion}
                </Badge>
              ))}
            </div>
          )}

          <div
            style={{
              display: "flex",
              gap: "15px",
              alignItems: "flex-end",
              background: "#F5F5F7",
              borderRadius: "18px",
              padding: "15px 20px",
            }}
          >
            <textarea
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="메시지를 입력하세요..."
              style={{
                flex: 1,
                border: "none",
                background: "transparent",
                fontSize: "15px",
                resize: "none",
                outline: "none",
                fontFamily: "inherit",
                maxHeight: "120px",
                color: COLORS.primary,
                minHeight: "24px",
              }}
              rows={1}
            />

            <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
              <button
                style={{
                  width: "40px",
                  height: "40px",
                  border: "none",
                  background: "transparent",
                  borderRadius: "50%",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: COLORS.aiPurple,
                }}
              >
                <Paperclip style={{ width: "20px", height: "20px" }} />
              </button>

              <button
                onClick={() => handleSendMessage()}
                disabled={!inputValue.trim()}
                style={{
                  width: "44px",
                  height: "44px",
                  border: "none",
                  background: inputValue.trim()
                    ? `linear-gradient(135deg, ${COLORS.aiPurple} 0%, ${COLORS.aiPink} 100%)`
                    : "#D2D2D7",
                  color: "white",
                  borderRadius: "50%",
                  cursor: inputValue.trim() ? "pointer" : "not-allowed",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  boxShadow: inputValue.trim()
                    ? "0 4px 12px rgba(139, 92, 246, 0.3)"
                    : "none",
                }}
              >
                <Send style={{ width: "20px", height: "20px" }} />
              </button>
            </div>
          </div>
        </div>
      </main>

      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }
        
        @keyframes typing {
          0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
          30% { transform: translateY(-10px); opacity: 1; }
        }
        
        @keyframes slideIn {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}
