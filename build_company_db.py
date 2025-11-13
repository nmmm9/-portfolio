"""
실제 기업 ESG 보고서 PDF로 ChromaDB 구축
company 폴더의 52개 PDF 파일 처리
"""

import fitz  # PyMuPDF
import chromadb
from chromadb.utils import embedding_functions
from pathlib import Path
import re
from typing import List, Dict
import shutil
from tqdm import tqdm

# 경로 설정
BASE_DIR = Path(__file__).resolve().parent
COMPANY_DIR = BASE_DIR / "company"
DATA_DIR = BASE_DIR / "data"
CHROMA_DIR = DATA_DIR / "chromadb"
BACKUP_DIR = DATA_DIR / "chromadb_backup"

# ESG 섹션 키워드 매핑
SECTION_KEYWORDS = {
    "Environment": [
        "환경", "기후", "탄소", "배출", "에너지", "재생에너지", "온실가스",
        "폐기물", "재활용", "수자원", "물", "생물다양성", "환경경영",
        "탄소중립", "넷제로", "RE100", "친환경"
    ],
    "Social": [
        "사회", "직원", "근로자", "임직원", "인권", "안전", "보건",
        "다양성", "포용", "교육", "훈련", "복지", "채용", "고용",
        "사회공헌", "지역사회", "상생", "협력사", "공급망"
    ],
    "Governance": [
        "지배구조", "이사회", "감사", "윤리", "준법", "컴플라이언스",
        "리스크", "투명성", "공시", "주주", "배당", "경영진",
        "내부통제", "감독", "독립성"
    ]
}

def classify_section(text: str) -> str:
    """텍스트 내용으로 ESG 섹션 자동 분류"""
    text_lower = text.lower()
    scores = {section: 0 for section in SECTION_KEYWORDS.keys()}

    for section, keywords in SECTION_KEYWORDS.items():
        for keyword in keywords:
            scores[section] += text_lower.count(keyword.lower())

    # 가장 높은 점수의 섹션 반환
    max_section = max(scores, key=scores.get)

    # 점수가 너무 낮으면 General로 분류
    if scores[max_section] < 2:
        return "General"

    return max_section

def extract_company_and_year(pdf_path: Path) -> tuple:
    """파일 경로에서 회사명과 연도 추출"""
    # 예: company/CJ/2024 CJ ESG.pdf
    company = pdf_path.parent.name
    filename = pdf_path.stem

    # 연도 추출 (4자리 숫자)
    year_match = re.search(r'(20\d{2})', filename)
    year = year_match.group(1) if year_match else "Unknown"

    return company, year

def extract_text_from_pdf(pdf_path: Path) -> List[Dict]:
    """PDF에서 텍스트를 페이지별로 추출"""
    chunks = []

    try:
        doc = fitz.open(pdf_path)
        company, year = extract_company_and_year(pdf_path)

        for page_num in range(len(doc)):
            page = doc[page_num]
            text = page.get_text()

            # 빈 페이지 스킵
            if not text or len(text.strip()) < 50:
                continue

            # 페이지 텍스트를 청크로 분할 (1500자 단위)
            page_chunks = split_into_chunks(text, chunk_size=1500, overlap=200)

            for chunk_idx, chunk_text in enumerate(page_chunks):
                # 섹션 자동 분류
                section = classify_section(chunk_text)

                chunks.append({
                    "text": chunk_text,
                    "metadata": {
                        "source": company,
                        "year": year,
                        "section": section,
                        "page": page_num + 1,
                        "chunk_id": f"{company}_{year}_p{page_num+1}_c{chunk_idx}",
                        "file": pdf_path.name
                    }
                })

        doc.close()
        print(f"✓ {company} {year}: {len(chunks)} 청크 추출됨")

    except Exception as e:
        print(f"✗ {pdf_path.name} 처리 중 오류: {e}")

    return chunks

def split_into_chunks(text: str, chunk_size: int = 1500, overlap: int = 200) -> List[str]:
    """텍스트를 청크로 분할 (오버랩 포함)"""
    chunks = []
    start = 0

    while start < len(text):
        end = start + chunk_size
        chunk = text[start:end]

        # 문장 중간에서 끊기지 않도록 조정
        if end < len(text):
            # 마지막 마침표/줄바꿈 찾기
            last_period = max(
                chunk.rfind('.'),
                chunk.rfind('다.'),
                chunk.rfind('\n')
            )
            if last_period > chunk_size * 0.7:  # 70% 이상 지점에서 찾으면
                chunk = chunk[:last_period + 1]
                end = start + last_period + 1

        chunks.append(chunk.strip())
        start = end - overlap  # 오버랩

    return chunks

def find_all_pdfs(company_dir: Path) -> List[Path]:
    """company 폴더의 모든 PDF 파일 찾기"""
    pdf_files = list(company_dir.glob("**/*.pdf"))
    print(f"\n총 {len(pdf_files)}개의 PDF 파일 발견")
    return sorted(pdf_files)

def build_chromadb(chunks: List[Dict]):
    """ChromaDB 구축"""
    print("\n" + "=" * 60)
    print("ChromaDB 구축 시작")
    print("=" * 60)

    # 기존 DB 백업
    if CHROMA_DIR.exists():
        print(f"\n기존 DB 백업 중: {BACKUP_DIR}")
        if BACKUP_DIR.exists():
            shutil.rmtree(BACKUP_DIR)
        shutil.copytree(CHROMA_DIR, BACKUP_DIR)
        shutil.rmtree(CHROMA_DIR)

    # ChromaDB 클라이언트 초기화
    print("\nChromaDB 클라이언트 생성...")
    chroma_client = chromadb.PersistentClient(path=str(CHROMA_DIR))

    # 임베딩 함수 설정
    print("한국어 임베딩 모델 로드 중... (jhgan/ko-sroberta-multitask)")
    embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="jhgan/ko-sroberta-multitask"
    )

    # 컬렉션 생성
    print("컬렉션 생성...")
    collection = chroma_client.create_collection(
        name="esg_documents_collection",
        embedding_function=embedding_function,
        metadata={"description": "실제 기업 ESG 보고서 데이터"}
    )

    # 문서 추가 (배치 처리)
    print(f"\n총 {len(chunks)}개 청크를 ChromaDB에 추가 중...")

    batch_size = 100
    for i in tqdm(range(0, len(chunks), batch_size), desc="DB 구축"):
        batch = chunks[i:i + batch_size]

        documents = [c["text"] for c in batch]
        metadatas = [c["metadata"] for c in batch]
        ids = [c["metadata"]["chunk_id"] for c in batch]

        collection.add(
            documents=documents,
            metadatas=metadatas,
            ids=ids
        )

    print(f"\n✓ ChromaDB 구축 완료!")
    print(f"  - 총 문서 수: {collection.count()}")
    print(f"  - 저장 위치: {CHROMA_DIR}")

    # 통계 출력
    print_statistics(chunks)

def print_statistics(chunks: List[Dict]):
    """데이터 통계 출력"""
    print("\n" + "=" * 60)
    print("데이터 통계")
    print("=" * 60)

    # 회사별 통계
    companies = {}
    for chunk in chunks:
        company = chunk["metadata"]["source"]
        companies[company] = companies.get(company, 0) + 1

    print("\n회사별 청크 수:")
    for company, count in sorted(companies.items()):
        print(f"  {company:20s}: {count:5d} 청크")

    # 섹션별 통계
    sections = {}
    for chunk in chunks:
        section = chunk["metadata"]["section"]
        sections[section] = sections.get(section, 0) + 1

    print("\nESG 섹션별 청크 수:")
    for section, count in sorted(sections.items(), key=lambda x: -x[1]):
        print(f"  {section:20s}: {count:5d} 청크")

    # 연도별 통계
    years = {}
    for chunk in chunks:
        year = chunk["metadata"]["year"]
        years[year] = years.get(year, 0) + 1

    print("\n연도별 청크 수:")
    for year, count in sorted(years.items()):
        print(f"  {year:10s}: {count:5d} 청크")

def main():
    """메인 실행 함수"""
    print("=" * 60)
    print("실제 기업 ESG 보고서 ChromaDB 구축")
    print("=" * 60)

    # PDF 파일 찾기
    pdf_files = find_all_pdfs(COMPANY_DIR)

    if not pdf_files:
        print("✗ PDF 파일을 찾을 수 없습니다!")
        return

    # 사용자 확인
    print("\n처리할 파일 목록:")
    for i, pdf in enumerate(pdf_files[:5], 1):
        print(f"  {i}. {pdf.parent.name}/{pdf.name}")
    if len(pdf_files) > 5:
        print(f"  ... 외 {len(pdf_files) - 5}개 파일")

    response = input(f"\n총 {len(pdf_files)}개 PDF를 처리하시겠습니까? (y/n): ")
    if response.lower() != 'y':
        print("취소되었습니다.")
        return

    # PDF에서 텍스트 추출
    print("\n" + "=" * 60)
    print("PDF 텍스트 추출 시작")
    print("=" * 60)

    all_chunks = []
    for pdf_path in tqdm(pdf_files, desc="PDF 처리"):
        chunks = extract_text_from_pdf(pdf_path)
        all_chunks.extend(chunks)

    print(f"\n총 {len(all_chunks)}개의 텍스트 청크 추출 완료!")

    # ChromaDB 구축
    if all_chunks:
        build_chromadb(all_chunks)
    else:
        print("✗ 추출된 텍스트가 없습니다!")

if __name__ == "__main__":
    main()
