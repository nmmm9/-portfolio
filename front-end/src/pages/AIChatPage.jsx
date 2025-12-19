import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Send, MessageCircle, User } from "lucide-react";
import "./AIChatPage.css";

export default function AIChatPage() {
  const navigate = useNavigate();
  const messagesEndRef = useRef(null);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState("");
  const [isTyping, setIsTyping] = useState(false);

  const quickQuestions = [
    "온실가스 배출량이 가장 많은 기업은?",
    "최근 기부금 현황을 알려줘",
    "긍정 뉴스 요약해줘",
    "ESG 성과가 좋은 기업 추천",
  ];

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = (text) => {
    const messageText = text || inputValue.trim();
    if (!messageText) return;

    setMessages((prev) => [
      ...prev,
      { id: Date.now(), sender: "user", text: messageText },
    ]);
    setInputValue("");
    setIsTyping(true);

    // 시뮬레이션 응답
    setTimeout(() => {
      setIsTyping(false);
      const responses = [
        `"${messageText}"에 대해 찾아봤어요.\n\n현재 데모 모드라 실제 데이터 분석은 제공되지 않지만, 정식 버전에서는 상세한 분석 결과를 확인하실 수 있습니다.`,
        `좋은 질문이에요! "${messageText}"에 대한 답변입니다.\n\n데이터베이스와 연동되면 더 정확한 정보를 제공해드릴게요.`,
        `"${messageText}" 관련 정보를 준비했어요.\n\n더 궁금한 점이 있으시면 언제든 물어보세요!`,
      ];
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          sender: "bot",
          text: responses[Math.floor(Math.random() * responses.length)],
        },
      ]);
    }, 1200);
  };

  const handleKeyPress = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="chatbot-page">
      {/* 헤더 */}
      <header className="chatbot-header">
        <button className="back-btn" onClick={() => navigate("/")}>
          <ArrowLeft size={24} />
        </button>
        <div className="header-info">
          <h1 className="header-title">임팩트 챗봇</h1>
          <p className="header-subtitle">ESG 데이터에 대해 질문해보세요</p>
        </div>
      </header>

      {/* 메시지 영역 */}
      <div className="chatbot-messages">
        {messages.length === 0 ? (
          <div className="welcome-container">
            <div className="welcome-icon">
              <MessageCircle />
            </div>
            <h2 className="welcome-title">안녕하세요! 👋</h2>
            <p className="welcome-desc">
              임팩트 데이터에 대해 궁금한 점을 물어보세요.
              <br />
              기업의 ESG 활동, 배출량, 기부금 등 다양한 정보를 알려드릴게요.
            </p>
            <div className="quick-actions">
              {quickQuestions.map((q, idx) => (
                <button
                  key={idx}
                  className="quick-btn"
                  onClick={() => handleSend(q)}
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <>
            {messages.map((msg) => (
              <div key={msg.id} className={`message ${msg.sender}`}>
                <div className={`message-avatar ${msg.sender}`}>
                  {msg.sender === "bot" ? (
                    <MessageCircle size={18} />
                  ) : (
                    <User size={18} />
                  )}
                </div>
                <div className="message-bubble">
                  {msg.text.split("\n").map((line, i) => (
                    <span key={i}>
                      {line}
                      <br />
                    </span>
                  ))}
                </div>
              </div>
            ))}
            {isTyping && (
              <div className="message bot">
                <div className="message-avatar bot">
                  <MessageCircle size={18} />
                </div>
                <div className="typing-indicator">
                  <span className="typing-dot" />
                  <span className="typing-dot" />
                  <span className="typing-dot" />
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </>
        )}
      </div>

      {/* 입력 */}
      <div className="chatbot-input">
        <div className="input-wrapper">
          <input
            className="chat-input"
            type="text"
            placeholder="메시지를 입력하세요..."
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyPress={handleKeyPress}
          />
          <button
            className="send-btn"
            onClick={() => handleSend()}
            disabled={!inputValue.trim()}
          >
            <Send size={20} />
          </button>
        </div>
      </div>
    </div>
  );
}
