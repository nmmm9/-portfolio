"""
샘플 ESG 데이터로 새로운 ChromaDB 생성
"""
import json
import chromadb
from chromadb.utils import embedding_functions
from pathlib import Path
import shutil

# 기존 ChromaDB 백업 및 삭제
CHROMA_DIR = Path('data/chromadb')
BACKUP_DIR = Path('data/chromadb_backup')

print("=" * 60)
print("샘플 ESG 데이터로 ChromaDB 생성")
print("=" * 60)

# 1. 기존 DB 백업
if CHROMA_DIR.exists():
    print(f"\n1. 기존 ChromaDB 백업 중...")
    if BACKUP_DIR.exists():
        shutil.rmtree(BACKUP_DIR)
    shutil.copytree(CHROMA_DIR, BACKUP_DIR)
    print(f"   ✓ 백업 완료: {BACKUP_DIR}")

    # 기존 DB 삭제
    shutil.rmtree(CHROMA_DIR)
    print(f"   ✓ 기존 DB 삭제 완료")

# 2. 샘플 데이터 로드
print(f"\n2. 샘플 데이터 로드 중...")
with open('sample_esg_data.json', 'r', encoding='utf-8') as f:
    sample_data = json.load(f)
print(f"   ✓ {len(sample_data)}개 문서 로드 완료")

# 3. ChromaDB 초기화
print(f"\n3. ChromaDB 초기화 중...")
client = chromadb.PersistentClient(path=str(CHROMA_DIR))

# 4. 임베딩 함수 설정 (원본과 동일)
print(f"\n4. 임베딩 모델 로드 중... (jhgan/ko-sroberta-multitask)")
embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
    model_name="jhgan/ko-sroberta-multitask"
)
print(f"   ✓ 임베딩 모델 로드 완료")

# 5. 컬렉션 생성
print(f"\n5. 컬렉션 생성 중...")
collection = client.create_collection(
    name="ppt_documents_collection",
    embedding_function=embedding_function,
    metadata={"description": "SAMPLE 기업 ESG 보고서"}
)
print(f"   ✓ 컬렉션 생성 완료")

# 6. 데이터 임베딩 및 저장
print(f"\n6. 데이터 임베딩 및 저장 중...")
ids = []
documents = []
metadatas = []

for idx, doc in enumerate(sample_data):
    ids.append(f"doc_{idx+1}")
    documents.append(doc['content'])
    metadatas.append({
        'source': doc['source'],
        'section': doc['section'],
        'sub_section': doc['sub_section'],
        'page_range': doc['page_range']
    })
    print(f"   - 문서 {idx+1}/{len(sample_data)}: {doc['sub_section']}")

# 배치로 추가
collection.add(
    ids=ids,
    documents=documents,
    metadatas=metadatas
)

print(f"\n   ✓ 총 {len(ids)}개 문서 임베딩 완료")

# 7. 검증
print(f"\n7. 데이터 검증 중...")
count = collection.count()
print(f"   - 저장된 문서 수: {count}")

# 테스트 검색
test_results = collection.query(
    query_texts=["탄소배출량"],
    n_results=3
)
print(f"   - 테스트 검색 결과: {len(test_results['ids'][0])}개")

if test_results['ids'][0]:
    print(f"   - 첫 번째 결과: {test_results['metadatas'][0][0]['sub_section']}")

print("\n" + "=" * 60)
print("✅ ChromaDB 생성 완료!")
print("=" * 60)
print(f"\n💡 이제 'python main.py'를 실행하여 챗봇을 테스트하세요.")
print(f"   예시 질문:")
print(f"   - SAMPLE의 탄소 감축 목표는?")
print(f"   - 재생에너지 전환 계획은?")
print(f"   - 여성 임원 비율은 얼마인가요?")
print(f"   - 사회공헌 활동 내용은?")
