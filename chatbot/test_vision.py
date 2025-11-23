# PDF Vision 테스트 스크립트
# GPT-4o-mini로 PDF 페이지를 이미지로 인식

from openai import OpenAI
from dotenv import load_dotenv
import os
import base64
from io import BytesIO
from pdf2image import convert_from_path
from pathlib import Path

# .env 파일 로드
load_dotenv()

# OpenAI 클라이언트 초기화
api_key = os.getenv('OPENAI_API_KEY')
if not api_key:
    raise ValueError("OPENAI_API_KEY가 설정되지 않았습니다.")

client = OpenAI(api_key=api_key)

def pdf_page_to_base64(pdf_path, page_num=0):
    """PDF의 특정 페이지를 base64 이미지로 변환"""
    print(f"PDF 변환 중: {pdf_path}")

    # PDF를 이미지로 변환 (특정 페이지만)
    images = convert_from_path(
        pdf_path,
        first_page=page_num + 1,
        last_page=page_num + 1,
        dpi=150  # 해상도 (높을수록 정확하지만 비용 증가)
    )

    if not images:
        raise ValueError(f"페이지 {page_num}을 변환할 수 없습니다.")

    # 이미지를 base64로 인코딩
    image = images[0]
    buffered = BytesIO()
    image.save(buffered, format="PNG")
    img_base64 = base64.b64encode(buffered.getvalue()).decode()

    print(f"페이지 {page_num + 1} 변환 완료 (크기: {image.size})")
    return img_base64

def analyze_page_with_vision(img_base64, page_num):
    """GPT-4o-mini Vision으로 페이지 분석"""
    print(f"\n페이지 {page_num + 1} 분석 중...")

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{
            "role": "user",
            "content": [
                {
                    "type": "text",
                    "text": """이 ESG 보고서 페이지를 분석해주세요.

다음 정보를 추출해주세요:
1. 페이지 제목/섹션명
2. 주요 내용 요약 (3-5문장)
3. 표가 있다면: 표 제목과 주요 수치
4. 그래프가 있다면: 그래프 제목과 주요 수치
5. 핵심 키워드 (5개)

한국어로 답변해주세요."""
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:image/png;base64,{img_base64}"
                    }
                }
            ]
        }],
        max_tokens=1500
    )

    return response.choices[0].message.content

def test_pdf_vision(pdf_path, test_pages=[0, 1, 2]):
    """PDF의 여러 페이지를 테스트"""
    print("=" * 60)
    print(f"PDF Vision 테스트")
    print(f"파일: {pdf_path}")
    print(f"테스트 페이지: {[p+1 for p in test_pages]}")
    print("=" * 60)

    results = []

    for page_num in test_pages:
        try:
            # PDF 페이지를 이미지로 변환
            img_base64 = pdf_page_to_base64(pdf_path, page_num)

            # Vision으로 분석
            analysis = analyze_page_with_vision(img_base64, page_num)

            results.append({
                "page": page_num + 1,
                "analysis": analysis
            })

            # 결과 출력
            print("\n" + "=" * 60)
            print(f"📄 페이지 {page_num + 1} 분석 결과:")
            print("=" * 60)
            print(analysis)
            print("\n")

        except Exception as e:
            print(f"❌ 페이지 {page_num + 1} 처리 중 오류: {e}")
            results.append({
                "page": page_num + 1,
                "error": str(e)
            })

    return results

if __name__ == "__main__":
    # 테스트할 PDF 경로
    pdf_path = Path(__file__).parent / "company" / "CJ" / "2024 CJ ESG.pdf"

    # 테스트 실행 (1, 2, 3페이지)
    # 표나 그래프가 있는 페이지를 테스트하려면 페이지 번호 변경
    results = test_pdf_vision(str(pdf_path), test_pages=[0, 1, 2])

    print("\n" + "=" * 60)
    print("✅ 테스트 완료!")
    print("=" * 60)
