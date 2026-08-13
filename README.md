# 🍽️ 먹을지도 (Meogeuljido) — Amugeona

> "오늘 뭐 먹지?" 고민될 때, 내 위치 기준으로 **아무거나** 골라주는 위치 기반 맛집 추천 서비스

## 소개

먹을지도는 명확한 메뉴가 없을 때 태그(예: `#한식` `#국물` `#혼밥`)를 다중 선택하면 현재 위치 반경 내에서 어울리는 식당을 추천해주는 서비스입니다. 카카오 지도 기반의 직관적인 UI 위에, 사용자가 직접 제보한 식당 데이터와 리뷰를 축적해 추천의 정확도를 높여갑니다.

## 제작 배경

"오늘 뭐 먹지?"는 매일 반복되는데도 매번 새로 고민하게 되는 질문입니다. 특히 메뉴가 아니라 "그냥 이 근처에서 국물 있는 거, 혼자 먹기 편한 곳" 같은 애매한 기준으로 찾고 싶을 때는, 기존 지도 서비스의 카테고리 검색만으로는 원하는 곳을 바로 찾기 어렵습니다. 결국 늘 가던 곳으로 관성적으로 향하게 되는 경우가 많습니다.

먹을지도는 이 "메뉴는 없지만 조건은 있는" 애매한 상황을 태그 기반 교차 필터링(카테고리 + 국물/볶음 같은 속성 + 위치 반경)으로 풀어보려는 프로젝트입니다. 동시에 인증/권한 관리, PostGIS 공간 검색, Redis 캐싱, 하이브리드 데이터 소싱(카카오 지도 API 연동), 소프트 삭제·감사 로그 같은 실무형 백엔드 설계를 직접 구현해보는 풀스택 포트폴리오 프로젝트이기도 합니다.

## 주요 기능

- **지도/리스트 기반 검색** — 화면 영역(Bounds), 카테고리, 키워드로 식당을 검색하고 지도/리스트 뷰를 오갈 수 있습니다.
- **'아무거나' 추천** — 태그를 다중 선택하면 현재 위치 반경 내 후보를 추천받고, 마음에 안 들면 다시 뽑을 수 있습니다.
- **식당 제보** — 새로운 식당을 등록할 수 있으며, 반경 내 유사 상호명 검사로 중복 등록을 방지합니다.
- **리뷰 & 즐겨찾기** — 별점과 사진으로 리뷰를 남기고, 자주 가는 식당을 즐겨찾기에 모아볼 수 있습니다.
- **관리자 검수** — 제보된 식당의 승인/반려, 악성 리뷰 모니터링, 전체 활동 로그 조회 기능을 제공합니다.

## 어떻게 사용하나요

1. **회원가입 / 로그인** — 이메일 인증 후 계정을 만듭니다(둘러보기만 할 경우 비로그인으로도 지도 조회는 가능합니다).
2. **'아무거나' 추천 받기** — 메인 화면 상단의 태그(`#한식`, `#중식`, `#국물`, `#볶음`, `#혼밥` 등)를 원하는 만큼 선택하면, 현재 위치 반경 내에서 조건에 맞는 식당 후보를 추천해줍니다. 마음에 드는 곳이 없으면 다시 뽑기로 다른 후보를 볼 수 있습니다.
3. **직접 둘러보기** — 태그 없이도 지도를 움직이거나 리스트 뷰로 전환해 주변 식당을 카테고리/키워드로 검색할 수 있습니다.
4. **상세 정보 확인 & 리뷰** — 마커나 카드를 탭하면 바텀시트/상세 페이지로 상호명, 평점, 태그, 리뷰를 볼 수 있고, 직접 방문 후 별점과 사진으로 리뷰를 남길 수 있습니다.
5. **즐겨찾기** — 자주 가는 식당은 즐겨찾기에 등록해 마이페이지에서 모아볼 수 있습니다.
6. **없는 식당은 직접 제보** — 지도에 없는 식당을 발견하면 우측 하단 `+` 버튼으로 상호명·위치·영업시간·사진과 함께 제보할 수 있습니다. 관리자 승인 후 지도에 공개됩니다.

## 기술 스택

### Frontend
- Vue 3 (Composition API) + TypeScript
- Pinia, Vue Router
- Tailwind CSS
- Kakao Map API
- Vite, ESLint + oxlint, Prettier
- Vitest + `@vue/test-utils` (컴포넌트 테스트)
- `openapi-typescript` (백엔드 OpenAPI 스펙 → TS 타입 자동 생성)

### Backend
- Spring Boot 4.1.0 (Java 21)
- Spring Security + JWT(jjwt)
- Spring Data JPA + QueryDSL + Hibernate Spatial (PostGIS 매핑)
- springdoc-openapi (Swagger UI)
- Bucket4j (요청 레이트리밋)
- Flyway (DB 마이그레이션)

### Database & Infra
- PostgreSQL + PostGIS (공간 검색), Redis (캐시/세션)
- Docker Compose, Nginx (정적 서빙 + 리버스 프록시)
- GitHub Actions (CI/CD), Let's Encrypt (HTTPS), Tailscale (운영 접근 VPN)

## 프로젝트 구조

```
meogeuljido/
├── front/meogeuljido/     # Vue 3 프론트엔드
├── server/meogeuljido/    # Spring Boot 백엔드
├── mock/                  # Tailwind 기반 정적 목업 (UI 사전 검증용)
└── docker-compose.yml     # 로컬 개발용 PostgreSQL/Redis
```

## 시작하기

### 사전 준비물
- Docker Desktop
- Node.js 20 이상
- JDK 21

### 1. 환경 변수 설정

```bash
cp .env.example .env
# .env를 열어 값 채우기 (DB/Redis 비밀번호, JWT 시크릿, 카카오 지도 API 키 등)
```

### 2. DB/Redis 실행

```bash
docker compose up -d postgres redis
```

### 3. 백엔드 실행

```bash
cd server/meogeuljido
./gradlew bootRun
```

### 4. 프론트엔드 실행

```bash
cd front/meogeuljido
npm install
npm run dev
```

### API 문서

백엔드 실행 후 아래 주소에서 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI 스펙(JSON): `http://localhost:8080/v3/api-docs`
````