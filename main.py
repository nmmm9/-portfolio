

import chromadb
from chromadb.utils import embedding_functions
from pathlib import Path
from rag_chatbot import run_rag_pipeline
from dotenv import load_dotenv

# .env 파일 로드
load_dotenv()

def initialize_chatbot():
    """챗봇 초기화"""
    print("=" * 60)
    print("ESG 챗봇 초기화 중...")
    print("=" * 60)

    # 경로 설정
    BASE_DIR = Path(__file__).resolve().parent
    DATA_DIR = BASE_DIR / "data"
    CHROMA_DIR = DATA_DIR / "chromadb"

    # ChromaDB 클라이언트 초기화
    print("✓ ChromaDB 연결 중...")
    chroma_client = chromadb.PersistentClient(path=str(CHROMA_DIR))

    # 임베딩 함수 설정
    print("✓ 임베딩 모델 로드 중... (jhgan/ko-sroberta-multitask)")
    embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="jhgan/ko-sroberta-multitask"
    )

    # 컬렉션 가져오기
    print("✓ 벡터 데이터베이스 로드 중...")
    try:
        # 실제 기업 데이터 컬렉션 사용
        collection = chroma_client.get_collection(
            name="esg_documents_collection",
            embedding_function=embedding_function
        )
    except:
        # 없으면 샘플 데이터 컬렉션 사용
        print("  ⚠️  실제 기업 데이터 없음 - 샘플 데이터 사용 중")
        print("  💡 build_company_db.py를 실행하여 실제 데이터 구축하세요")
        collection = chroma_client.get_collection(
            name="ppt_documents_collection",
            embedding_function=embedding_function
        )

    # 저장된 문서 수 확인
    doc_count = collection.count()
    print(f"✓ 총 {doc_count}개의 ESG 문서가 로드되었습니다.")
    print("=" * 60)
    print("초기화 완료! 질문을 입력하세요.\n")

    return collection


def print_answer(result: dict):
    """답변 출력"""
    print("\n" + "=" * 60)
    print("📝 답변:")
    print("=" * 60)
    print(result['answer'])
    print("\n" + "=" * 60)

    # 검증 결과 표시 (있는 경우)
    if result.get('verification'):
        verification = result['verification']
        confidence = verification.get('confidence', 'unknown')
        overall_score = verification.get('overall', 0)

        # 신뢰도에 따른 이모지
        confidence_emoji = {
            'high': '✅',
            'medium': '⚠️',
            'low': '❌'
        }

        emoji = confidence_emoji.get(confidence, '❓')

        print(f"\n{emoji} 답변 신뢰도: {confidence.upper()} (점수: {overall_score}/10)")

        if verification.get('issues'):
            print(f"⚠️  개선 필요: {', '.join(verification['issues'])}")


def print_sources(result: dict):
    """출처 정보 출력"""
    print("\n" + "=" * 60)
    print("📚 참고 문서:")
    print("=" * 60)
    print(result['relevance'])
    print("\n상세 문서:")
    print("-" * 60)
    print(result['context'])
    print("=" * 60)


def main():
    """메인 실행 함수"""
    try:
        # 챗봇 초기화
        collection = initialize_chatbot()

        print("💡 사용 팁:")
        print("  - 짧은 질문도 가능합니다 (예: '탄소배출량?')")
        print("  - 기업명을 포함하면 해당 기업 정보를 우선 검색합니다")
        print("    (예: 'CJ의 환경 전략은?', '삼성의 탄소 감축 방법?')")
        print("  - 연도 지정 가능 (예: '2024년 SK 재생에너지 사용률은?')")
        print("  - 종료하려면 'quit' 또는 'exit' 입력\n")

        # 대화 루프
        while True:
            # 사용자 입력
            query = input("\n❓ 질문을 입력하세요: ").strip()

            # 종료 명령어 체크
            if query.lower() in ['quit', 'exit', '종료', 'q']:
                print("\n챗봇을 종료합니다. 감사합니다!")
                break

            # 빈 입력 체크
            if not query:
                print("⚠️ 질문을 입력해주세요.")
                continue

            # RAG 파이프라인 실행
            print("\n🔍 검색 중...")
            try:
                result = run_rag_pipeline(query, collection)

                # 답변 출력
                print_answer(result)

                # 출처 확인 여부
                show_sources = input("\n📖 참고 문서를 보시겠습니까? (y/n): ").strip().lower()
                if show_sources in ['y', 'yes', 'ㅛ', '예']:
                    print_sources(result)

            except Exception as e:
                print(f"\n❌ 오류가 발생했습니다: {e}")
                print("다시 시도해주세요.")

    except FileNotFoundError as e:
        print("\n❌ 데이터 파일을 찾을 수 없습니다.")
        print("data/chromadb 디렉토리가 존재하는지 확인해주세요.")
        print(f"상세 오류: {e}")
    except ValueError as e:
        print(f"\n❌ 설정 오류: {e}")
        print(".env 파일을 확인해주세요.")
    except Exception as e:
        print(f"\n❌ 예상치 못한 오류가 발생했습니다: {e}")


if __name__ == "__main__":
    main()
