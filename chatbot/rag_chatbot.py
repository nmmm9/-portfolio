# RAG 챗봇 핵심 로직
# 원본: SKN12-4th-2TEAM/app/services/rag_chatbot.py

from openai import OpenAI
from dotenv import load_dotenv
import os
import chromadb
from chromadb.utils import embedding_functions
import json
from pathlib import Path
from typing import Optional, Dict, List
from sentence_transformers import CrossEncoder
import numpy as np
import torch
from torch.nn.functional import sigmoid


# 현재 파일 기준 경로 설정
BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"

FEW_SHOT_PATH = DATA_DIR / "few_shot_examples.json"
CHROMA_DIR = DATA_DIR / "chromadb"

# .env 파일 로드
load_dotenv()

# OpenAI API 키 설정
api_key = os.getenv('OPENAI_API_KEY')
if not api_key:
    raise ValueError("OPENAI_API_KEY가 설정되지 않았습니다. .env 파일을 생성하고 OPENAI_API_KEY를 설정해주세요.")

client = OpenAI(api_key=api_key)

# Cross-encoder 모델 초기화
cross_encoder = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2')

# 질문 유형별 프롬프트 템플릿
QUESTION_TYPE_PROMPTS = {
    "definition": """
    이 질문은 ESG 개념이나 용어에 대한 **정의 및 설명**을 요청하는 질문입니다.

    다음 구조로 답변하세요:

    ### 1. 개요 및 정의
    - 해당 개념의 명확한 정의
    - 핵심 구성 요소 설명

    ### 2. 배경 및 중요성
    - 왜 중요한지
    - ESG 맥락에서의 의미
    - 관련 글로벌 기준 (GRI, SASB, TCFD 등)

    ### 3. 실무 적용
    - 기업에서 어떻게 활용되는지
    - 측정 및 관리 방법

    **모든 설명에는 제공된 문서의 구체적인 내용을 인용하세요.**
    예: "Scope 3는 공급망 전반의 간접 배출을 의미합니다 (출처: SAMPLE ESG보고서, p.10-15)"
    """,

    "how_to": """
    이 질문은 ESG 실행 방법이나 **전략 수립**에 대한 질문입니다.

    다음 구조로 답변하세요:

    ### 1. 개요
    - 무엇을 달성하려는 것인지 명확히 설명

    ### 2. 핵심 지표 (KPI)
    - 측정해야 할 주요 지표
    - 목표 수치 및 기준
    - 예: "재생에너지 사용률: 2023년 15% → 2030년 50% (출처: SAMPLE, p.15-18)"

    ### 3. 단계별 실행 로드맵
    **단계별로 구체적인 액션 플랜을 제시하세요:**

    #### 1단계 (0~6개월): 준비 및 진단
    - 구체적 실행 항목
    - 필요 리소스

    #### 2단계 (7~12개월): 초기 실행
    - 구체적 실행 항목
    - 예상 성과

    #### 3단계 (1~2년): 확대 및 정착
    - 구체적 실행 항목
    - 목표 달성 기준

    ### 4. 참고 사례
    - 문서에서 언급된 구체적 사례
    - 성과 데이터 포함

    **모든 내용에 출처를 명시하세요.**
    """,

    "case_study": """
    이 질문은 **특정 기업의 ESG 사례**를 요청하는 질문입니다.

    다음 구조로 답변하세요:

    ### 1. 기업 개요
    - 해당 기업의 ESG 전략 방향

    ### 2. 주요 활동 및 성과
    **표 형식으로 정리하세요:**

    | 분야 | 주요 활동 | 구체적 성과 | 출처 |
    |------|-----------|-------------|------|
    | 환경 | ... | ... | (출처: ..., p.X) |
    | 사회 | ... | ... | (출처: ..., p.X) |

    ### 3. 특징 및 시사점
    - 해당 기업 활동의 특징
    - 다른 기업이 참고할 만한 점

    ### 4. 성과 데이터
    - 구체적인 수치와 지표
    - 전년 대비 개선도

    **반드시 문서 기반으로만 작성하고, 모든 주장에 출처를 명시하세요.**
    """,

    "comparison": """
    이 질문은 **비교 분석**을 요청하는 질문입니다.

    다음 구조로 답변하세요:

    ### 1. 비교 개요
    - 무엇을 비교하는지 명확히 설명

    ### 2. 비교 분석
    **표 형식으로 구조화하세요:**

    | 항목 | A | B | 차이점 |
    |------|---|---|--------|
    | 지표1 | ... | ... | ... |
    | 지표2 | ... | ... | ... |

    ### 3. 종합 분석
    - 주요 차이점
    - 각각의 장단점
    - 상황별 선택 기준

    **모든 데이터에 출처를 명시하세요.**
    """,

    "trend": """
    이 질문은 **ESG 트렌드나 변화**에 대한 질문입니다.

    다음 구조로 답변하세요:

    ### 1. 현황 분석
    - 현재 상황 설명
    - 관련 데이터 제시

    ### 2. 변화 추이
    - 시간에 따른 변화
    - 주요 전환점 및 원인

    ### 3. 향후 전망
    - 예상되는 변화
    - 준비해야 할 사항

    ### 4. 대응 방안
    - 기업이 취해야 할 액션
    - 우선순위 제시

    **문서 기반으로 작성하되, 추세 분석은 논리적으로 도출하세요.**
    """
}

def classify_question_type(query: str) -> str:
    """LLM을 사용하여 질문 유형을 자동 분류"""

    classification_prompt = """
    다음 ESG 관련 질문의 유형을 분류하세요.

    가능한 유형:
    - "definition": 개념, 용어 설명 요청 (예: "Scope 3가 뭐야?", "ESG란?", "RE100 설명해줘")
    - "how_to": 실행 방법, 전략 수립 (예: "탄소배출 어떻게 줄여?", "재생에너지 목표 설정법", "ESG 경영 도입 방법")
    - "case_study": 특정 기업 사례 (예: "CJ는 어떻게 해?", "신한의 ESG 활동", "삼표 사례")
    - "comparison": 비교 분석 (예: "A와 B 비교", "차이점은?", "어떤 게 나아?")
    - "trend": 트렌드, 변화 (예: "최근 트렌드", "ESG 변화", "앞으로 어떻게 될까?")
    - "data_inquiry": 데이터 조회 (예: "탄소배출량은?", "목표는?", "실적 알려줘")

    질문: {query}

    단순히 유형만 반환하세요. 예: definition
    """

    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",  # 분류는 mini 모델로 충분
            messages=[
                {"role": "user", "content": classification_prompt.format(query=query)}
            ],
            temperature=0.1,
            max_tokens=20
        )
        question_type = response.choices[0].message.content.strip().lower()

        # 유효한 타입인지 확인
        valid_types = ["definition", "how_to", "case_study", "comparison", "trend", "data_inquiry"]
        if question_type not in valid_types:
            question_type = "data_inquiry"  # 기본값

        print(f"질문 유형 분류: {question_type}")
        return question_type

    except Exception as e:
        print(f"질문 유형 분류 중 오류: {e}")
        return "data_inquiry"  # 기본값

def expand_query(query: str, min_length: int = 10) -> str:
    """짧은 쿼리를 LLM을 사용하여 확장"""
    if len(query) > min_length: # 쿼리가 최소 길이보다 크면 쿼리 반환
        return query

    system_prompt = """너는 사용자의 짧은 질문을 ESG 컨텍스트에 맞게 더 구체적이고 풍부하게 바꿔주는 전문가야.
    다음 규칙을 따라야 해:
    1. 원래 질문의 의도를 유지하면서 확장
    2. ESG 관련 구체적인 용어나 개념 포함
    3. 검색에 도움될 만한 관련 키워드 추가
    4. 한국어로 자연스럽게 작성
    5. 확장된 질문은 1-2문장으로 제한

    예시:
    입력: "탄소배출량?"
    출력: "기업의 탄소배출량 관리와 감축 목표는 어떻게 설정되어 있으며, 어떤 감축 전략을 사용하고 있나요?"

    입력: "지배구조"
    출력: "기업의 이사회 구성과 운영체계는 어떻게 되어있으며, 지배구조의 투명성을 어떻게 확보하고 있나요?"
    """

    try:
        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": f"다음 질문을 확장해주세요: {query}"}
            ],
            temperature=0.3,
            max_tokens=200
        )
        expanded_query = response.choices[0].message.content.strip()
        print(f"\n원래 질문: {query}")
        print(f"확장된 질문: {expanded_query}\n")
        return expanded_query
    except Exception as e:
        print(f"질문 확장 중 오류 발생: {e}")
        return query

def extract_metadata_filters(query: str) -> Dict[str, str]:
    """사용자 질문에서 메타데이터 필터 추출"""
    filters = {}

    # 섹션 필터 추출
    sections = {
        "Environment": ["환경", "environment", "기후", "탄소"],
        "Social": ["사회", "social", "직원", "인권", "안전"],
        "Governance": ["지배구조", "governance", "윤리", "준법"]
    }

    query_lower = query.lower()
    for section, keywords in sections.items():
        if any(keyword in query_lower for keyword in keywords):
            filters["section"] = section
            break

    # 회사명으로 필터링
    companies = {
        "CJ": ["cj", "씨제이"],
        "HYUNDAI": ["hyundai", "현대", "hdai"],
        "KB": ["kb", "케이비", "kb금융"],
        "LG CHEM": ["lg chem", "lg화학", "엘지화학", "엘지켁"],
        "LG ELECTRONICS": ["lg electronics", "lg전자", "엘지전자", "엘지"],
        "POSCO": ["posco", "포스코"],
        "SAMSUNG": ["samsung", "삼성"],
        "SK": ["sk", "에스케이"],
        # 레거시 (샘플 데이터)
        "KTNG": ["ktng", "케이티앤지", "kt&g"],
        "SHINHAN": ["신한", "shinhan"],
        "SAMPYO": ["삼표", "sampyo"],
        "SAMPLE": ["sample", "샘플"]
    }

    for company, keywords in companies.items():
        if any(keyword in query_lower for keyword in keywords):
            filters["source"] = company
            break

    return filters

def get_relevance_label(score: float) -> str:
    """관련도 점수에 따른 레이블 반환"""
    if score > 0.9:
        return "매우 높은 관련성 🌟"
    elif score > 0.7:
        return "높은 관련성 ⭐"
    elif score > 0.5:
        return "중간 관련성 ✨"
    else:
        return "낮은 관련성 ⚪"

def rerank_documents(query: str, documents: List[str], metadata_list: List[Dict], top_k: int = 5) -> tuple:
    """Cross-encoder를 사용하여 문서 재순위화 (정규화 포함)"""
    # 각 문서와 쿼리의 쌍을 생성
    pairs = [[query, doc] for doc in documents]

    # Cross-encoder로 유사도 점수 계산 (Tensor로 반환)
    raw_scores = cross_encoder.predict(pairs, convert_to_numpy=False)

    # sigmoid로 점수 정규화 (0~1 범위)
    if not isinstance(raw_scores, torch.Tensor):
        raw_scores = torch.tensor(raw_scores)
    norm_scores = sigmoid(raw_scores).tolist()

    # 점수에 따라 문서 정렬
    doc_score_pairs = list(zip(documents, metadata_list, norm_scores))
    doc_score_pairs.sort(key=lambda x: x[2], reverse=True)

    # top_k 개의 문서 선택
    reranked_docs = []
    reranked_metadata = []
    reranked_scores = []

    for doc, metadata, score in doc_score_pairs[:top_k]:
        reranked_docs.append(doc)
        reranked_metadata.append(metadata)
        reranked_scores.append(score)

    return reranked_docs, reranked_metadata, reranked_scores

def get_relevant_context(query: str, collection, initial_k: int = 20, final_k: int = 5, metadata_filters: Optional[Dict[str, str]] = None) -> tuple:
    """사용자 질문과 관련된 문서 검색 (2단계 검색)"""
    try:
        # metadata_filters를 ChromaDB where 절 형식으로 변환
        where_clause = None
        if metadata_filters and len(metadata_filters) > 0:
            conditions = []
            for field, value in metadata_filters.items():
                conditions.append({
                    field: {"$eq": value}
                })

            if len(conditions) == 1:
                where_clause = conditions[0]
            else:
                where_clause = {
                    "$and": conditions
                }

            print(f"적용된 검색 필터: {where_clause}")

        # 1단계: 벡터 검색으로 initial_k개 문서 검색
        results = collection.query(
            query_texts=[query],
            n_results=initial_k,
            where=where_clause
        )

        # 결과가 없으면 필터 없이 다시 검색
        if not results['documents'][0]:
            print("지정된 필터로 검색된 결과가 없어 전체 검색을 수행합니다.")
            results = collection.query(
                query_texts=[query],
                n_results=initial_k
            )
    except Exception as e:
        print(f"검색 중 오류 발생: {e}")
        print("필터 없이 전체 검색을 수행합니다.")
        results = collection.query(
            query_texts=[query],
            n_results=initial_k
        )

    # 2단계: Cross-encoder로 재순위화
    print(f"디버깅: 검색된 문서 수 = {len(results['documents'][0]) if results['documents'] and results['documents'][0] else 0}")

    if results['documents'] and results['documents'][0]:
        reranked_docs, reranked_metadata, scores = rerank_documents(
            query,
            results['documents'][0],
            results['metadatas'][0],
            final_k
        )
    else:
        print("검색 결과가 없습니다.")
        return "", {
            "sections": set(),
            "subsections": set(),
            "sources": set(),
            "page_ranges": set()
        }

    # 메타데이터 요약 정보 수집
    metadata_summary = {
        "sections": set(),
        "subsections": set(),
        "sources": set(),
        "page_ranges": set()
    }

    contexts = []
    for i, (doc, metadata, score) in enumerate(zip(reranked_docs, reranked_metadata, scores)):
        metadata_summary["sections"].add(metadata['section'])
        metadata_summary["subsections"].add(metadata['sub_section'])
        metadata_summary["sources"].add(metadata['source'])
        metadata_summary["page_ranges"].add(metadata.get('page_range', '알 수 없음'))

        relevance_label = get_relevance_label(score)

        context = f"""
                    출처: {metadata['source']}
                    섹션: {metadata['section']}
                    서브섹션: {metadata['sub_section']}
                    페이지: {metadata.get('page_range', '알 수 없음')}
                    관련도: {score:.4f} ({relevance_label})
                    내용: {doc}
                    ---"""
        contexts.append(context)

    return "\n".join(contexts), metadata_summary

def generate_response(query: str, context: str, metadata_summary: Dict, question_type: str = "data_inquiry"):
    """개선된 프롬프트를 사용하여 응답 생성"""
    # few-shot 예시 로드
    examples_path = FEW_SHOT_PATH
    with open(examples_path, "r", encoding="utf-8") as f:
        few_shot_examples = json.load(f)

    # 메타데이터 요약 문자열 생성
    metadata_info = f"""
    참고한 문서 정보:
    - 섹션: {', '.join(sorted(metadata_summary['sections']))}
    - 서브섹션: {', '.join(sorted(metadata_summary['subsections']))}
    - 출처: {', '.join(sorted(metadata_summary['sources']))}
    - 페이지: {', '.join(sorted(str(p) for p in metadata_summary['page_ranges']))}
    """

    # 질문 유형에 맞는 프롬프트 템플릿 선택
    type_specific_prompt = QUESTION_TYPE_PROMPTS.get(question_type, "")

    # 개선된 시스템 프롬프트
    system_prompt = f"""당신은 기업의 ESG(환경·사회·지배구조) 경영을 지원하는 전문 AI 챗봇입니다.

**핵심 원칙:**
1. **문서 기반 답변**: 반드시 제공된 문서의 내용만을 사용하여 답변하세요. 문서에 없는 정보는 추측하지 마세요.
2. **출처 명시**: 모든 주요 정보에는 출처를 명시하세요. 형식: (출처: [회사명] ESG보고서, p.[페이지])
3. **정확성**: 수치, 날짜, 고유명사는 문서 그대로 정확히 인용하세요.
4. **구조화**: Markdown 형식으로 체계적으로 작성하세요.

---

{type_specific_prompt}

---

**답변 시 주의사항:**
- 확실하지 않은 정보는 "문서에서 해당 정보를 찾을 수 없습니다"라고 명시
- 여러 출처의 정보를 종합할 때는 각각 출처 표시
- 표나 리스트를 활용하여 가독성 향상
- 마지막에 핵심 내용을 한 문장으로 요약

---

**제공된 문서 정보:**
{metadata_info}

**검색된 문서 내용:**
{context}

---

위 문서를 바탕으로 질문에 답변하세요."""

    try:
        result = client.chat.completions.create(
            model="gpt-4o",  # GPT-4o 모델 사용
            messages = [{"role": "system", "content": system_prompt}] +
                        few_shot_examples +
                        [{"role": "user", "content": query}],
            temperature=0.7,
            max_tokens=1500  # 더 긴 답변 허용
        )
        return result.choices[0].message.content, metadata_info
    except Exception as e:
        print(f"응답 생성 중 오류 발생: {e}")
        return "죄송합니다. 응답을 생성하는 중에 오류가 발생했습니다.", metadata_info

def verify_answer(query: str, answer: str, context: str) -> dict:
    """답변 품질을 검증하고 신뢰도 점수 반환"""

    verification_prompt = f"""
    다음 ESG 질문과 답변을 평가하세요.

    **질문:** {query}

    **답변:** {answer}

    **원본 문서:** {context}

    다음 기준으로 평가하세요:
    1. **관련성** (0-10): 답변이 질문과 얼마나 관련있는가?
    2. **정확성** (0-10): 답변이 원본 문서와 일치하는가? (수치, 날짜 포함)
    3. **완전성** (0-10): 질문에 충분히 답변했는가?
    4. **출처 표시** (0-10): 출처가 명확히 표시되었는가?

    JSON 형식으로 반환:
    {{
        "relevance": 점수,
        "accuracy": 점수,
        "completeness": 점수,
        "citation": 점수,
        "overall": 평균점수,
        "issues": ["문제점1", "문제점2"] (없으면 빈 배열),
        "confidence": "high/medium/low"
    }}
    """

    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",  # 검증은 mini로 충분
            messages=[
                {"role": "user", "content": verification_prompt}
            ],
            temperature=0.1,
            max_tokens=300
        )

        # JSON 파싱
        import json
        result_text = response.choices[0].message.content.strip()

        # JSON 부분만 추출 (코드 블록 제거)
        if "```json" in result_text:
            result_text = result_text.split("```json")[1].split("```")[0].strip()
        elif "```" in result_text:
            result_text = result_text.split("```")[1].split("```")[0].strip()

        verification_result = json.loads(result_text)

        print(f"\n답변 검증 결과:")
        print(f"  신뢰도: {verification_result.get('confidence', 'unknown')}")
        print(f"  종합 점수: {verification_result.get('overall', 0)}/10")

        if verification_result.get('issues'):
            print(f"  문제점: {', '.join(verification_result['issues'])}")

        return verification_result

    except Exception as e:
        print(f"답변 검증 중 오류: {e}")
        return {
            "overall": 7.0,
            "confidence": "medium",
            "issues": []
        }

def run_rag_pipeline(user_query: str, collection, enable_verification: bool = True) -> dict:
    """개선된 RAG 파이프라인 실행"""

    # 1단계: 질문 유형 분류
    question_type = classify_question_type(user_query)

    # 2단계: 쿼리 확장
    expanded = expand_query(user_query)

    # 3단계: 메타데이터 필터 추출
    filters = extract_metadata_filters(user_query)

    # 4-5단계: 문서 검색 + 재순위화
    context, metadata = get_relevant_context(expanded, collection, metadata_filters=filters)

    # 6단계: 답변 생성 (질문 유형 반영)
    answer, relevance = generate_response(user_query, context, metadata, question_type)

    # 7단계: 답변 검증 (선택적)
    verification = None
    if enable_verification and context:
        verification = verify_answer(user_query, answer, context)

    return {
        "answer": answer,
        "relevance": relevance,
        "context": context,
        "question_type": question_type,
        "verification": verification
    }
