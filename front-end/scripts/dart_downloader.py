"""
DART 재무제표 TSV 자동 다운로드 스크립트 (수정버전)
"""
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
import time
import os
from datetime import datetime

# 로그 파일 설정
log_file = r'C:\기부금\download_log.txt'

def log(message):
    """로그를 화면과 파일에 동시 출력"""
    print(message)
    with open(log_file, 'a', encoding='utf-8') as f:
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        f.write(f"[{timestamp}] {message}\n")

def download_dart_tsv():
    log("=" * 60)
    log("🚀 DART TSV 자동 다운로드 시작")
    log("=" * 60)
    
    # Chrome 옵션 설정
    chrome_options = Options()
    chrome_options.add_experimental_option('prefs', {
        'download.default_directory': r'C:\기부금\TSV',
        'download.prompt_for_download': False,
        'download.directory_upgrade': True,
        'safebrowsing.enabled': True
    })
    # chrome_options.add_argument('--headless')  # 주석 해제하면 브라우저 안 보임
    
    # 다운로드 폴더 생성
    os.makedirs(r'C:\기부금\TSV', exist_ok=True)
    os.makedirs(r'C:\기부금', exist_ok=True)
    
    # WebDriver 시작
    log("🌐 Chrome 브라우저 시작 중...")
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    driver.maximize_window()
    
    try:
        # 1. DART 재무제표 다운로드 페이지 접속
        log("📂 DART 페이지 접속 중...")
        url = "https://opendart.fss.or.kr/disclosureinfo/fnltt/dwld/list.do"
        driver.get(url)
        log(f"   URL: {url}")
        
        # 페이지 로딩 대기
        time.sleep(5)
        
        # 페이지 소스 확인 (디버깅용)
        log("🔍 페이지 요소 검색 중...")
        
        # 2. 입력 필드 찾기 (여러 방법 시도)
        try:
            # 방법 1: ID로 찾기
            start_date = WebDriverWait(driver, 10).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, "input[name='start_date'], input[id*='start'], input[placeholder*='시작']"))
            )
            log("✅ 시작일 입력창 발견")
        except:
            log("⚠️ 자동 입력 실패 - 수동으로 진행합니다")
            log("   브라우저에서 직접 입력해주세요:")
            log("   1. 기간: 1999-01-01 ~ 2025-10-17")
            log("   2. 보고서 종류: 모두 체크")
            log("   3. 검색 버튼 클릭")
            log("   4. 다운로드 버튼 클릭")
            log("")
            log("⏳ 120초 대기 중... (이 시간 동안 수동으로 작업해주세요)")
            time.sleep(120)
            
            # 다운로드 확인
            download_path = r'C:\기부금\TSV'
            files = [f for f in os.listdir(download_path) if f.endswith('.tsv') or f.endswith('.zip')]
            
            if files:
                log("=" * 60)
                log("✅ 다운로드 완료!")
                log("=" * 60)
                for file in files:
                    file_path = os.path.join(download_path, file)
                    file_size = os.path.getsize(file_path) / (1024 * 1024)  # MB
                    log(f"   📁 {file} ({file_size:.2f} MB)")
            else:
                log("❌ 다운로드된 파일이 없습니다")
            
            return
        
        # 자동 입력 진행
        log("📅 기간 설정 중...")
        start_date.clear()
        start_date.send_keys("1999-01-01")
        
        # 종료일 찾기
        end_date = driver.find_element(By.CSS_SELECTOR, "input[name='end_date'], input[id*='end'], input[placeholder*='종료']")
        end_date.clear()
        end_date.send_keys("2025-10-17")
        log("   ✅ 기간 입력 완료")
        
        time.sleep(2)
        
        # 3. 보고서 종류 체크
        log("📋 보고서 종류 선택 중...")
        checkboxes = driver.find_elements(By.CSS_SELECTOR, "input[type='checkbox']")
        checked_count = 0
        for checkbox in checkboxes:
            try:
                if not checkbox.is_selected():
                    driver.execute_script("arguments[0].click();", checkbox)
                    checked_count += 1
            except:
                pass
        log(f"   ✅ {checked_count}개 체크박스 선택 완료")
        
        time.sleep(2)
        
        # 4. 검색 버튼 클릭
        log("🔍 검색 버튼 클릭 중...")
        search_btn = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), '검색') or contains(@class, 'search')]"))
        )
        search_btn.click()
        log("   ✅ 검색 완료")
        
        # 검색 결과 대기
        time.sleep(10)
        
        # 5. 다운로드 버튼 클릭
        log("⬇️ 다운로드 버튼 클릭 중...")
        download_btn = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), '다운로드') or contains(text(), 'TSV') or contains(@class, 'download')]"))
        )
        download_btn.click()
        log("   ✅ 다운로드 시작")
        
        # 다운로드 완료 대기
        log("⏳ 다운로드 완료 대기 중... (약 2~5분 소요)")
        log("   파일 크기가 클 수 있으니 기다려주세요...")
        
        for i in range(12):  # 2분 동안 10초마다 체크
            time.sleep(10)
            download_path = r'C:\기부금\TSV'
            files = [f for f in os.listdir(download_path) if not f.endswith('.crdownload') and not f.endswith('.tmp')]
            if files:
                log(f"   📊 진행 중... ({(i+1)*10}초 경과)")
        
        time.sleep(60)  # 추가 1분 대기
        
        # 6. 다운로드 확인
        log("=" * 60)
        download_path = r'C:\기부금\TSV'
        files = os.listdir(download_path)
        
        if files:
            log("✅ 다운로드 완료!")
            log("=" * 60)
            for file in files:
                file_path = os.path.join(download_path, file)
                file_size = os.path.getsize(file_path) / (1024 * 1024)  # MB
                log(f"   📁 {file} ({file_size:.2f} MB)")
        else:
            log("❌ 다운로드 실패 또는 진행 중...")
            log("   브라우저를 닫지 말고 수동으로 다운로드해주세요")
            
    except Exception as e:
        log("=" * 60)
        log(f"❌ 오류 발생: {str(e)}")
        log("=" * 60)
        log("💡 해결 방법:")
        log("   1. 브라우저에서 수동으로 다운로드를 진행하세요")
        log("   2. 또는 DART 홈페이지에서 직접 다운로드하세요")
        log("   3. 로그 파일 확인: C:\\기부금\\download_log.txt")
        
        # 수동 작업 시간 제공
        log("")
        log("⏳ 120초 동안 브라우저를 열어둡니다")
        log("   이 시간 동안 수동으로 다운로드해주세요!")
        time.sleep(120)
        
    finally:
        log("🔚 브라우저 종료")
        log("📄 전체 로그: C:\\기부금\\download_log.txt")
        driver.quit()

if __name__ == "__main__":
    download_dart_tsv()