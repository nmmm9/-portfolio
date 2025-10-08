"""
ESG 챗봇 - 간단 실행 버전
원본 프로젝트의 RAG 로직을 그대로 사용하는 터미널 기반 챗봇
"""

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
        collection = chroma_client.get_collection(
            name="ppt_documents_collection",
            embedding_function=embedding_function
        )
    except Exception as e:
        print(f"❌ 컬렉션 로드 실패: {e}")
        print("\n디버깅: 사용 가능한 컬렉션 확인 중...")
        print(f"컬렉션 목록: {chroma_client.list_collections()}")
        raise

    # 저장된 문서 수 확인
    try:
        doc_count = collection.count()
        # ChromaDB count()가 부정확할 수 있으므로 실제 검색으로 확인
        test_results = collection.query(query_texts=["test"], n_results=1)
        print(f"✓ ChromaDB 연결 성공 (컬렉션 문서 수: {doc_count}개)")
    except Exception as e:
        print(f"❌ 문서 수 확인 실패: {e}")
        import traceback
        traceback.print_exc()
        raise
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
        print("    (예: 'CJ의 환경 전략은?', '신한의 탄소 감축 방법?')")
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
                import traceback
                traceback.print_exc()
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
