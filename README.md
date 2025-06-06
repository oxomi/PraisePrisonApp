## 🌿 브랜치 전략

| 브랜치 이름         | 설명                                                                 | 비고 |
|--------------------|----------------------------------------------------------------------|------|
| `main`             | 배포를 위한 메인 브랜치                                                |      |
| `feature/기능명`   | 새 기능 개발 브랜치. 기능명은 소문자로 작성. 구분 필요 시(`-`) 사용 | 예: `feature/login`, `feature/userprofile`, `feature/login-oauth` |

---

## ✨ 커밋 컨벤션

| 타입        | 설명                             | 예시 메시지                |
|-------------|----------------------------------|----------------------------|
| `feat`      | 새로운 기능 추가                   | `feat: 로그인 기능 추가`    |
| `fix`       | 버그 수정                         | `fix: 결제 오류 수정`      |
| `refactor`  | 기능 변화 없이 코드 개선/리팩터링   | `refactor: 인증 로직 개선` |
| `chore`     | 문서 작성, 포맷팅, 환경 설정 등 기타 작업 | `chore: 라이브러리 버전 업데이트` |

---

## 🗂️ 디렉토리 구조
```
app/
 └── src/
     └── main/
         ├── java/com/yourappname/
         │    ├── data/                // 데이터 계층 (Repository, Model, API)
         │    │    ├── model/          // Data class들 (Diary, Group, User, Reaction 등)
         │    │    ├── repository/     // Firebase 연동 (DiaryRepository, GroupRepository 등)
         │    │    ├── network/        // 외부 API 연동 (KoBERT API, kadvice API, 감정 단어 API)
         │    ├── ui/                  // 화면 계층
         │    │    ├── home/           // 홈 탭 (그룹 리스트/생성)
         │    │    ├── group/          // 그룹방 (일기 보기, 리액션, 그룹 설정)
         │    │    ├── writediary/     // 일기 작성
         │    │    ├── mydiary/        // 내 일기 탭 (리스트/필터링)
         │    │    ├── profile/        // 프로필 탭 (통계, 알림 설정)
         │    │    ├── common/         // 공용 UI 컴포넌트 (ProgressBar, Dialog 등)
         │    ├── util/                // 유틸 클래스, 확장 함수
         │    ├── worker/              // WorkManager 사용 (푸시 알림용)
         │    ├── App.kt               // Application 클래스 (필요 시 - Firebase 초기화 등)
         ├── res/
         │    ├── layout/              // 화면 레이아웃 XML 파일
         │    ├── drawable/            // 이미지 리소스, 아이콘 리소스 저장
         │    ├── values/              // colors.xml, strings.xml, themes.xml 등
         │    ├── mipmap/              // 앱 로고
         ├── AndroidManifest.xml
         ├── google-services.json      // Firebase 설정 파일
```
