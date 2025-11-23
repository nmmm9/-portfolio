# 전체 PDF Vision 처리 및 저장 스크립트
# 모든 회사의 모든 PDF를 처리하여 JSON + ChromaDB에 저장

from openai import OpenAI
from dotenv import load_dotenv
import os
import base64
import json
import re
from io import BytesIO
from pdf2image import convert_from_path
from pathlib import Path
import chromadb
from chromadb.utils import embedding_functions

# .env 파일 로드
load_dotenv()

# OpenAI 클라이언트 초기화
api_key = os.getenv('OPENAI_API_KEY')
if not api_key:
    raise ValueError("OPENAI_API_KEY가 설정되지 않았습니다.")

client = OpenAI(api_key=api_key)

# 경로 설정
BASE_DIR = Path(__file__).resolve().parent
COMPANY_DIR = BASE_DIR / "company"
DATA_DIR = BASE_DIR / "data"
EXTRACTED_DIR = DATA_DIR / "extracted"
CHROMA_DIR = DATA_DIR / "chromadb_vision"

# 디렉토리 생성
EXTRACTED_DIR.mkdir(parents=True, exist_ok=True)
CHROMA_DIR.mkdir(parents=True, exist_ok=True)


def extract_year_from_filename(filename):
    """파일명에서 년도 추출"""
    # 2019-2020 형식 (LG ELECTRONICS) - 뒤의 년도 사용
    match_range = re.search(r'20\d{2}-(20\d{2})', filename)
    if match_range:
        return int(match_range.group(1))

    # 일반 형식 (2019, 2020, ...)
    match = re.search(r'(20\d{2})', filename)
    if match:
        return int(match.group(1))
    return None


def extract_version_from_filename(filename):
    """파일명에서 버전 정보 추출 (KB 등)"""
    if "이해관계자" in filename:
        return "이해관계자"
    elif "투자자" in filename:
        return "투자자"
    return None


def pdf_page_to_base64(pdf_path, page_num=0):
    """PDF의 특정 페이지를 base64 이미지로 변환"""
    images = convert_from_path(
        pdf_path,
        first_page=page_num + 1,
        last_page=page_num + 1,
        dpi=150
    )

    if not images:
        raise ValueError(f"페이지 {page_num}을 변환할 수 없습니다.")

    image = images[0]
    buffered = BytesIO()
    image.save(buffered, format="PNG")
    img_base64 = base64.b64encode(buffered.getvalue()).decode()

    return img_base64


def analyze_page_with_vision(img_base64, page_num):
    """GPT-4o-mini Vision으로 페이지 전체 내용 추출"""

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": """이 ESG 보고서 페이지의 모든 내용을 정확하게 추출해주세요.

## 추출 규칙

### 1. 텍스트 (TEXT)
- 페이지에 있는 모든 텍스트를 그대로 추출
- 제목, 본문, 캡션, 주석 모두 포함
- 원본 순서와 구조 유지

### 2. 표 (TABLE)
- 마크다운 표 형식으로 정확히 변환
- 모든 행과 열의 데이터를 빠짐없이 추출
- 숫자는 단위(%, 원, 톤 등)와 함께 정확히 기재
- 표 제목이 있으면 표 위에 **[표: 제목]** 형식으로 표시

### 3. 그래프/차트 (CHART)
- **[차트: 제목]** 형식으로 시작
- 차트 유형 명시 (막대, 선, 원형 등)
- 모든 데이터 포인트의 수치를 추출
- X축, Y축 레이블과 범례 포함
- 예시:
  - 2021년: 1,234톤
  - 2022년: 1,456톤
  - 2023년: 1,678톤

### 4. 이미지/아이콘
- **[이미지: 설명]** 형식으로 간단히 설명

## 출력 형식

```
## 페이지 제목: [제목]

### 텍스트 내용
[추출된 모든 텍스트]

### 표
[표: 제목]
| 열1 | 열2 | 열3 |
|-----|-----|-----|
| 데이터 | 데이터 | 데이터 |

### 차트/그래프
[차트: 제목]
- 차트 유형: 막대 그래프
- 데이터:
  - 항목1: 수치
  - 항목2: 수치

### 기타
[이미지나 특이사항]

---

### 📌 페이지 요약
- **핵심 주제**: [이 페이지의 핵심 주제 1문장]
- **주요 내용**: [3-5문장으로 핵심 내용 요약]
- **핵심 수치**: [가장 중요한 수치 3-5개 나열]
- **키워드**: [핵심 키워드 5개]
```

**중요: 숫자와 수치는 반드시 정확하게 추출하세요. 추측하지 마세요.**
**한국어로 답변해주세요.**"""
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:image/png;base64,{img_base64}"
                    }
                }
            ]
        }],
        max_tokens=6000
    )

    return response.choices[0].message.content


def get_total_pages(pdf_path):
    """PDF 총 페이지 수 확인"""
    from pdf2image.pdf2image import pdfinfo_from_path
    info = pdfinfo_from_path(pdf_path)
    return info['Pages']


def process_single_pdf(pdf_path, company_name):
    """단일 PDF 파일 처리"""
    filename = os.path.basename(pdf_path)
    year = extract_year_from_filename(filename)
    version = extract_version_from_filename(filename)

    print(f"\n{'='*60}")
    print(f"📄 처리 중: {filename}")
    version_str = f", 버전: {version}" if version else ""
    print(f"   회사: {company_name}, 년도: {year}{version_str}")
    print(f"{'='*60}")

    # 총 페이지 수 확인
    total_pages = get_total_pages(pdf_path)
    print(f"   총 페이지: {total_pages}")

    pages_data = []

    for page_num in range(total_pages):
        try:
            print(f"   페이지 {page_num + 1}/{total_pages} 처리 중...", end=" ")

            # 이미지 변환
            img_base64 = pdf_page_to_base64(pdf_path, page_num)

            # Vision 분석
            content = analyze_page_with_vision(img_base64, page_num)

            # 키워드 추출 (요약에서)
            keywords = extract_keywords_from_content(content)

            page_data = {
                "page": page_num + 1,
                "content": content,
                "keywords": keywords
            }
            pages_data.append(page_data)

            print("✅")

        except Exception as e:
            print(f"❌ 오류: {e}")
            pages_data.append({
                "page": page_num + 1,
                "content": f"오류 발생: {str(e)}",
                "keywords": []
            })

    # 결과 데이터 구성
    result = {
        "company": company_name,
        "year": year,
        "version": version,
        "filename": filename,
        "total_pages": total_pages,
        "pages": pages_data
    }

    return result


def extract_keywords_from_content(content):
    """컨텐츠에서 키워드 추출"""
    # 키워드 섹션 찾기
    keywords = []
    if "키워드" in content:
        # 키워드 라인 찾기
        lines = content.split('\n')
        for line in lines:
            if "키워드" in line and ":" in line:
                # 키워드 추출
                keyword_part = line.split(":")[-1]
                # 쉼표나 공백으로 분리
                keywords = [k.strip() for k in re.split(r'[,،、]', keyword_part) if k.strip()]
                break
    return keywords[:5]  # 최대 5개


def save_to_json(result, company_name):
    """결과를 JSON 파일로 저장"""
    company_dir = EXTRACTED_DIR / company_name
    company_dir.mkdir(parents=True, exist_ok=True)

    # 파일명 생성 (버전이 있으면 추가)
    year = result.get('year', 'unknown')
    version = result.get('version')
    if version:
        json_filename = f"{year}_{company_name}_ESG_{version}.json"
    else:
        json_filename = f"{year}_{company_name}_ESG.json"
    json_path = company_dir / json_filename

    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"   💾 JSON 저장: {json_path}")
    return json_path


def save_to_chromadb(result, collection):
    """결과를 ChromaDB에 저장"""
    company = result['company']
    year = result['year']
    version = result.get('version')

    documents = []
    metadatas = []
    ids = []

    for page_data in result['pages']:
        page_num = page_data['page']
        content = page_data['content']
        keywords = page_data.get('keywords', [])

        # 문서 ID 생성 (버전이 있으면 추가)
        if version:
            doc_id = f"{company}_{year}_{version}_page_{page_num}"
        else:
            doc_id = f"{company}_{year}_page_{page_num}"

        documents.append(content)
        metadatas.append({
            "company": company,
            "year": year,
            "version": version if version else "",
            "page": page_num,
            "filename": result['filename'],
            "keywords": ", ".join(keywords)
        })
        ids.append(doc_id)

    # ChromaDB에 추가
    collection.add(
        documents=documents,
        metadatas=metadatas,
        ids=ids
    )

    print(f"   🗄️  ChromaDB 저장: {len(documents)}개 문서")


def process_all_companies():
    """모든 회사의 모든 PDF 처리"""
    print("=" * 60)
    print("🚀 전체 PDF Vision 처리 시작")
    print("=" * 60)

    # ChromaDB 초기화
    print("\n📦 ChromaDB 초기화 중...")
    chroma_client = chromadb.PersistentClient(path=str(CHROMA_DIR))

    # 임베딩 함수 설정
    embedding_function = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="jhgan/ko-sroberta-multitask"
    )

    # 기존 컬렉션 삭제 후 새로 생성
    try:
        chroma_client.delete_collection(name="esg_vision_collection")
    except:
        pass

    collection = chroma_client.create_collection(
        name="esg_vision_collection",
        embedding_function=embedding_function
    )
    print("   ✅ ChromaDB 컬렉션 생성 완료")

    # 회사 폴더 목록
    company_folders = [f for f in COMPANY_DIR.iterdir() if f.is_dir()]

    total_pdfs = 0
    processed_pdfs = 0

    # 전체 PDF 수 계산
    for company_folder in company_folders:
        pdf_files = list(company_folder.glob("*.pdf"))
        total_pdfs += len(pdf_files)

    print(f"\n📊 총 {len(company_folders)}개 회사, {total_pdfs}개 PDF 파일")

    # 각 회사별 처리
    for company_folder in company_folders:
        company_name = company_folder.name
        pdf_files = sorted(company_folder.glob("*.pdf"))

        print(f"\n{'='*60}")
        print(f"🏢 회사: {company_name} ({len(pdf_files)}개 PDF)")
        print(f"{'='*60}")

        for pdf_file in pdf_files:
            processed_pdfs += 1
            print(f"\n[{processed_pdfs}/{total_pdfs}]", end="")

            try:
                # PDF 처리
                result = process_single_pdf(str(pdf_file), company_name)

                # JSON 저장
                save_to_json(result, company_name)

                # ChromaDB 저장
                save_to_chromadb(result, collection)

            except Exception as e:
                print(f"\n❌ PDF 처리 실패: {pdf_file}")
                print(f"   오류: {e}")

    # 완료
    print("\n" + "=" * 60)
    print("✅ 전체 처리 완료!")
    print(f"   처리된 PDF: {processed_pdfs}/{total_pdfs}")
    print(f"   JSON 저장 위치: {EXTRACTED_DIR}")
    print(f"   ChromaDB 위치: {CHROMA_DIR}")
    print("=" * 60)

    # 저장된 문서 수 확인
    doc_count = collection.count()
    print(f"\n📈 ChromaDB 총 문서 수: {doc_count}")


if __name__ == "__main__":
    process_all_companies()
