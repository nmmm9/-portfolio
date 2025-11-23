# FastAPI 챗봇 서버
# 프론트엔드에서 호출하는 RAG 챗봇 API

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Optional

from openai import OpenAI
from dotenv import load_dotenv
import os
import chromadb
from chromadb.utils import embedding_functions
from pathlib import Path
from sentence_transformers import CrossEncoder
import torch
from torch.nn.functional import sigmoid

# .env 파일 로드
load_dotenv()

# OpenAI 클라이언트 초기화
api_key = os.getenv('OPENAI_API_KEY')
if not api_key:
    raise ValueError("OPENAI_API_KEY가 설정되지 않았습니다.")

client = OpenAI(api_key=api_key)

# 경로 설정
BASE_DIR = Path(__file__).resolve().parent
CHROMA_DIR = BASE_DIR / "data" / "chromadb_vision"

# Cross-encoder 모델 초기화
cross_encoder = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2')

# 시스템 프롬프트
SYSTEM_PROMPT = """당신은 **ESG(환경·사회·지배구조) 전문 AI 컨설턴트**입니다.
기업의 ESG 보고서를 분석하여 정확하고 통찰력 있는 답변을 제공합니다.

## 🎯 핵심 원칙

### 1. 정확성 (Accuracy)
- **반드시** 제공된 문서 내용만 사용하세요
- 수치, 날짜, 고유명사는 문서 그대로 정확히 인용하세요
- 추측이나 일반적인 지식으로 답변하지 마세요

### 2. 출처 명시 (Citation)
- 모든 주요 정보에 출처를 명시하세요
- 형식: **(출처: [회사명] [년도] ESG보고서, p.[페이지])**
- 여러 출처를 종합할 때는 각각 표시하세요

### 3. 구조화된 답변 (Structure)
- Markdown 형식으로 체계적으로 작성하세요
- 복잡한 정보는 표, 목록, 소제목을 활용하세요
- 핵심 내용을 먼저 제시하고 세부사항을 설명하세요

### 4. 대화 문맥 (Context)
- 이전 대화 내용을 참고하여 일관성 있게 답변하세요
- "그것", "이전에 말한" 등 대명사가 가리키는 내용을 정확히 파악하세요
- 같은 정보를 반복하지 말고 새로운 관점을 추가하세요

## 📋 답변 형식

### 일반 질문:
**[핵심 답변]**

[상세 설명]

📊 **관련 수치:**
- 항목1: 수치 (출처)
- 항목2: 수치 (출처)

💡 **시사점:** [분석 또는 의미]

### 정보 부족 시:
제공된 문서에서 해당 정보를 찾을 수 없습니다.

**관련 정보:**
- [찾을 수 있는 유사 정보]

## 🔍 ESG 분야별 전문성

### 환경 (Environment)
- 탄소배출량 (Scope 1, 2, 3), 온실가스, 에너지 사용
- 재생에너지, 폐기물, 수자원, 생물다양성

### 사회 (Social)
- 임직원 (다양성, 안전, 복지, 교육)
- 공급망 관리, 인권, 지역사회 공헌

### 지배구조 (Governance)
- 이사회 구성, 윤리경영, 준법경영
- 리스크 관리, 주주권리, 정보공개

## ⚠️ 주의사항
- 확실하지 않은 정보는 "문서에서 확인 필요"라고 명시
- 한국어로 자연스럽고 전문적으로 답변하세요
"""

# FastAPI 앱 초기화
app = FastAPI(title="ESG RAG Chatbot API", version="1.0.0")

# CORS 설정 (프론트엔드 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 모든 origin 허용 (개발용)
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 전역 변수
collection = None


# 요청/응답 모델
class Message(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    message: str
    history: Optional[List[Message]] = []


class ChatResponse(BaseModel):
    answer: str
    sources: Optional[List[str]] = []


def initialize_collection():
    """ChromaDB 컬렉션 초기화"""
    global collection

    print("🤖 ChromaDB 초기화 중...")
    chroma_client = chromadb.PersistentClient(path=str(CHROMA_DIR))

    embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="jhgan/ko-sroberta-multitask"
    )

    collection = chroma_client.get_collection(
        name="esg_vision_collection",
        embedding_function=embedding_function
    )

    doc_count = collection.count()
    print(f"✅ {doc_count}개 문서 로드 완료")

    return collection


def rerank_documents(query: str, documents: List[str], metadata_list: List[Dict], top_k: int = 5):
    """Cross-encoder를 사용하여 문서 재순위화"""
    pairs = [[query, doc] for doc in documents]
    raw_scores = cross_encoder.predict(pairs, convert_to_numpy=False)

    if not isinstance(raw_scores, torch.Tensor):
        raw_scores = torch.tensor(raw_scores)
    norm_scores = sigmoid(raw_scores).tolist()

    doc_score_pairs = list(zip(documents, metadata_list, norm_scores))
    doc_score_pairs.sort(key=lambda x: x[2], reverse=True)

    return doc_score_pairs[:top_k]


def search_documents(query: str, initial_k: int = 15, final_k: int = 5):
    """관련 문서 검색 (2단계: 벡터 검색 + Cross-encoder 리랭킹)"""
    global collection

    # 1단계: 벡터 검색
    results = collection.query(
        query_texts=[query],
        n_results=initial_k
    )

    if not results['documents'][0]:
        return "", []

    # 2단계: Cross-encoder 리랭킹
    reranked = rerank_documents(
        query,
        results['documents'][0],
        results['metadatas'][0],
        final_k
    )

    # 결과 포맷팅
    contexts = []
    sources = []

    for doc, metadata, score in reranked:
        company = metadata.get('company', '')
        year = metadata.get('year', '')
        page = metadata.get('page', '')
        version = metadata.get('version', '')

        version_str = f" ({version})" if version else ""
        source = f"{company} {year}년{version_str} ESG 보고서, {page}페이지"
        sources.append(source)

        # 관련도 표시
        if score > 0.8:
            relevance = "🌟 매우 높음"
        elif score > 0.6:
            relevance = "⭐ 높음"
        elif score > 0.4:
            relevance = "✨ 중간"
        else:
            relevance = "○ 낮음"

        context = f"""
[출처: {source}] [{relevance}]
{doc[:2000]}...
"""
        contexts.append(context)

    return "\n---\n".join(contexts), sources


def generate_response(query: str, context: str, history: List[Message]):
    """GPT-4o로 답변 생성"""

    # 시스템 프롬프트에 검색된 문서 추가
    system_with_context = f"""{SYSTEM_PROMPT}

## 현재 질문에 대한 검색된 문서:
{context}
"""

    # 메시지 구성
    messages = [{"role": "system", "content": system_with_context}]

    # 이전 대화 기록 추가 (최근 10개)
    recent_history = history[-10:] if len(history) > 10 else history
    for msg in recent_history:
        messages.append({"role": msg.role, "content": msg.content})

    # 현재 질문 추가
    messages.append({"role": "user", "content": query})

    response = client.chat.completions.create(
        model="gpt-4o",
        messages=messages,
        temperature=0.3,
        max_tokens=2000
    )

    return response.choices[0].message.content


# API 엔드포인트
@app.on_event("startup")
async def startup_event():
    """서버 시작 시 초기화"""
    initialize_collection()


@app.get("/")
async def root():
    """헬스 체크"""
    return {"status": "ok", "message": "ESG RAG Chatbot API"}


@app.post("/api/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """챗봇 대화 API"""
    global collection

    if collection is None:
        raise HTTPException(status_code=500, detail="ChromaDB가 초기화되지 않았습니다.")

    try:
        # 1. 관련 문서 검색
        context, sources = search_documents(request.message)

        # 2. 답변 생성
        answer = generate_response(request.message, context, request.history)

        return ChatResponse(answer=answer, sources=sources)

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/health")
async def health_check():
    """상세 헬스 체크"""
    global collection

    return {
        "status": "ok",
        "chromadb": "connected" if collection else "disconnected",
        "documents": collection.count() if collection else 0
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)
