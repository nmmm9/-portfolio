# Vision RAG 챗봇 (대화 문맥 유지)
# ESG 보고서 기반 AI 챗봇

from openai import OpenAI
from dotenv import load_dotenv
import os
import chromadb
from chromadb.utils import embedding_functions
from pathlib import Path
from typing import List, Dict
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
```
**[핵심 답변]**

[상세 설명]

📊 **관련 수치:**
- 항목1: 수치 (출처)
- 항목2: 수치 (출처)

💡 **시사점:** [분석 또는 의미]
```

### 비교/분석 질문:
```
**[비교 요약]**

| 항목 | A | B | 비고 |
|------|---|---|------|
| 지표1 | 값 | 값 | 분석 |

📈 **분석:** [차이점과 의미]
```

### 정보 부족 시:
```
제공된 문서에서 [질문 내용]에 대한 정보를 찾을 수 없습니다.

**관련 정보:**
- [찾을 수 있는 유사 정보]

💡 **대안:** [다른 질문 제안]
```

## 🔍 ESG 분야별 전문성

### 환경 (Environment)
- 탄소배출량 (Scope 1, 2, 3), 온실가스, 에너지 사용
- 재생에너지, 폐기물, 수자원, 생물다양성
- 기후변화 대응, 탄소중립 목표

### 사회 (Social)
- 임직원 (다양성, 안전, 복지, 교육)
- 공급망 관리, 인권, 지역사회 공헌
- 고객 만족, 개인정보 보호

### 지배구조 (Governance)
- 이사회 구성, 윤리경영, 준법경영
- 리스크 관리, 주주권리, 정보공개

## ⚠️ 주의사항
- 확실하지 않은 정보는 "문서에서 확인 필요"라고 명시
- 민감한 정보(미공개 전략, 논쟁적 주제)는 객관적으로만 서술
- 한국어로 자연스럽고 전문적으로 답변하세요
"""


def initialize_chatbot():
    """챗봇 초기화"""
    print("=" * 60)
    print("🤖 Vision RAG 챗봇 초기화 중...")
    print("=" * 60)

    # ChromaDB 클라이언트 초기화
    chroma_client = chromadb.PersistentClient(path=str(CHROMA_DIR))

    # 임베딩 함수 설정
    embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="jhgan/ko-sroberta-multitask"
    )

    # 컬렉션 가져오기
    collection = chroma_client.get_collection(
        name="esg_vision_collection",
        embedding_function=embedding_function
    )

    # 저장된 문서 수 확인
    doc_count = collection.count()
    print(f"✅ 총 {doc_count}개의 문서가 로드되었습니다.")
    print("=" * 60)

    return collection


def rerank_documents(query: str, documents: List[str], metadata_list: List[Dict], top_k: int = 5):
    """Cross-encoder를 사용하여 문서 재순위화"""
    # 각 문서와 쿼리의 쌍을 생성
    pairs = [[query, doc] for doc in documents]

    # Cross-encoder로 유사도 점수 계산
    raw_scores = cross_encoder.predict(pairs, convert_to_numpy=False)

    # sigmoid로 점수 정규화 (0~1 범위)
    if not isinstance(raw_scores, torch.Tensor):
        raw_scores = torch.tensor(raw_scores)
    norm_scores = sigmoid(raw_scores).tolist()

    # 점수에 따라 문서 정렬
    doc_score_pairs = list(zip(documents, metadata_list, norm_scores))
    doc_score_pairs.sort(key=lambda x: x[2], reverse=True)

    # top_k 개의 문서 선택
    return doc_score_pairs[:top_k]


def search_documents(query, collection, initial_k=15, final_k=5):
    """관련 문서 검색 (2단계: 벡터 검색 + Cross-encoder 리랭킹)"""

    # 1단계: 벡터 검색으로 후보 문서 검색
    results = collection.query(
        query_texts=[query],
        n_results=initial_k
    )

    if not results['documents'][0]:
        return ""

    # 2단계: Cross-encoder로 재순위화
    reranked = rerank_documents(
        query,
        results['documents'][0],
        results['metadatas'][0],
        final_k
    )

    # 결과 포맷팅
    contexts = []
    for doc, metadata, score in reranked:
        company = metadata.get('company', '')
        year = metadata.get('year', '')
        page = metadata.get('page', '')
        version = metadata.get('version', '')

        version_str = f" ({version})" if version else ""

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
[출처: {company} {year}년{version_str} ESG 보고서, {page}페이지] [{relevance}]
{doc[:2000]}...
"""
        contexts.append(context)

    return "\n---\n".join(contexts)


def generate_response(query: str, context: str, conversation_history: List[Dict[str, str]]):
    """GPT-4o로 답변 생성 (대화 기록 포함)"""

    # 시스템 프롬프트에 현재 검색된 문서 추가
    system_with_context = f"""{SYSTEM_PROMPT}

## 현재 질문에 대한 검색된 문서:
{context}
"""

    # 메시지 구성: 시스템 + 대화 기록 + 현재 질문
    messages = [{"role": "system", "content": system_with_context}]

    # 이전 대화 기록 추가 (최근 10개까지)
    recent_history = conversation_history[-10:] if len(conversation_history) > 10 else conversation_history
    messages.extend(recent_history)

    # 현재 질문 추가
    messages.append({"role": "user", "content": query})

    response = client.chat.completions.create(
        model="gpt-4o",
        messages=messages,
        temperature=0.3,
        max_tokens=2000
    )

    return response.choices[0].message.content


def chat(query: str, collection, conversation_history: List[Dict[str, str]]):
    """질문에 대한 답변 생성"""
    # 1. 관련 문서 검색
    context = search_documents(query, collection)

    # 2. 답변 생성 (대화 기록 포함)
    response = generate_response(query, context, conversation_history)

    return response


def main():
    """메인 실행 함수"""
    # 초기화
    collection = initialize_chatbot()

    # 대화 기록 초기화
    conversation_history: List[Dict[str, str]] = []

    print("\n💡 ESG 보고서 기반 AI 챗봇입니다.")
    print("   예시: 'CJ의 탄소배출량은?', 'CJ의 ESG 전략은?'")
    print("   대화 초기화: 'clear' 또는 'reset'")
    print("   종료: 'quit' 또는 'exit'\n")

    # 대화 루프
    while True:
        query = input("❓ 질문: ").strip()

        # 종료 명령어
        if query.lower() in ['quit', 'exit', '종료', 'q']:
            print("챗봇을 종료합니다.")
            break

        # 대화 초기화 명령어
        if query.lower() in ['clear', 'reset', '초기화']:
            conversation_history.clear()
            print("🔄 대화 기록이 초기화되었습니다.\n")
            continue

        # 빈 입력 체크
        if not query:
            print("질문을 입력해주세요.")
            continue

        print("\n🔍 검색 중...")
        response = chat(query, collection, conversation_history)

        # 대화 기록에 추가
        conversation_history.append({"role": "user", "content": query})
        conversation_history.append({"role": "assistant", "content": response})

        print("\n" + "=" * 60)
        print("📝 답변:")
        print("=" * 60)
        print(response)
        print("=" * 60)
        print(f"💬 대화 기록: {len(conversation_history)//2}개 대화\n")


if __name__ == "__main__":
    main()
