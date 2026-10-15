# backend — Spring Boot 4 API

Java 17 + Spring Boot 4.1.1. 프론트가 호출하는 REST API를 제공하고, Railway가 이 폴더를 루트로 배포한다. DB는 없다.

## 구조

```
com/portfolio/
├── controller/   REST 엔드포인트
├── service/      외부 API 호출·비즈니스 로직
└── dto/          요청/응답 객체
```

외부 API 호출은 전부 `service/`에서 한다. 컨트롤러는 요청 검증과 응답 변환만 맡는다. CORS 설정은 `WebConfig.java`에 있다.

## 엔드포인트

| API | 담당 클래스 | 외부 의존 |
|---|---|---|
| `POST /api/battle/chat` | `BattleController` / `BattleService` | Anthropic Claude API |
| `GET /api/youtube/{comments,replies}` | `YoutubeController` / `YoutubeService` | YouTube Data API v3 |
| `GET /api/jobs` | `JobController` / `JobService` | 사람인 오픈 API + Kakao Local(주소→좌표) |

**생사 판정용 엔드포인트는 없다.** 2026-09-19 에 `GET /api/battle/health` 를 지웠다 — 꺼져 있는 기능 밑에 앱 전체 판정이 들어가 있었다. 떠 있는지 봐야 하는 자리(`verify.sh`·`dev-up.sh`·`e2e/backend.spec.ts`·배포 워크플로)는 `GET /api/jobs` 를 두들긴다. 사람인 키가 없으면 외부를 안 부르고 바로 `200` 에 `error` 필드를 실어 주므로 값이 싸다. **키를 넣으면 이 호출이 사람인·카카오까지 가서 느려진다** — 그때는 판정 대상을 다시 고른다.

영속화하는 데이터가 없다. 전부 매 요청 외부 호출이다.

## 환경변수

`src/main/resources/application.properties` + Railway Variables. 로컬은 `application.properties.template`를 복사해서 채운다. 실제 키가 든 `application.properties`는 커밋 대상이 아니다.

| 변수 | 용도 |
|---|---|
| `anthropic.api.key` | AI 배틀 |
| `youtube.api.key` | 유튜브 댓글·답글 |
| `kakao.rest.key` | Kakao Local REST 서버사이드 호출 |
| `saramin.access.key` | 채용정보 |

과거에 쓰던 `polygon.api.key`·`spring.datasource.*`(주가 캐시용 Postgres)는 제거됐으니 다시 넣지 않는다.

## 로컬 실행

```bash
cp src/main/resources/application.properties.template src/main/resources/application.properties
# 위 파일에 실제 키 채운 뒤
mvn spring-boot:run       # localhost:8080
```

## 검증

테스트는 `src/test/java/com/portfolio/` 에 있다. 컨트롤러는 `@WebMvcTest` 로 서비스를 가짜로 바꿔 띄우므로 API 키 없이 돈다. `PortfolioApplicationTests` 는 컨텍스트만 세워 빈 주입·설정 오류를 잡고, `WebConfigCorsTest` 는 프리플라이트로 허용 오리진을 확인한다.

서비스 계층은 가짜 HTTP 서버로 돌린다(`src/test/java/com/portfolio/service/StubServer.java`, JDK 의 `HttpServer` 라 의존성이 안 는다). 진짜 유튜브·사람인·모델 API 를 부르면 키가 있어야 하고 할당량과 상대 쪽 사정에 결과가 흔들린다. 그래서 각 서비스의 주소를 속성으로 뺐다 — `youtube.api.base-url`·`saramin.api.url`·`kakao.keyword.url`·`anthropic.api.url` 이고, 안 주면 진짜 주소가 기본값이다.

여기서만 잡히는 것: 페이지 토큰을 따라가는 수집 루프, 실패 이유별 상태코드 분류, 급여 문자열 파싱, 지역명 정리, 반복공고 세기, 거리 계산, 모델 요청 본문 모양.

200 인데 본문이 JSON 이 아닌 응답(점검 안내 HTML)도 세 서비스 모두 여기서 502 `UPSTREAM_ERROR` 로 바꾼다. 댓글 수집은 같은 페이지 토큰이 다시 오거나 50장을 넘으면 멈춘다 — 빈 페이지가 이어지면 댓글 수 상한만으로는 안 끝난다.

`cors.allowed-origins` 는 쉼표로 여러 개를 적는다. 로컬은 `http://localhost:3000`(dev)과 `http://localhost:3100`(프론트 e2e) 둘 다 필요하다.


Maven Wrapper로 빌드한다. `mvn`은 이 PC에 설치돼 있지 않지만 `./mvnw`가 Maven 3.9.9를 `~/.m2/wrapper/`에 자동으로 받아 쓴다.

**JAVA_HOME을 매번 지정해야 한다.** 시스템 `JAVA_HOME`은 JDK 11을 가리키고 PATH의 `java`는 JDK 25인데, 이 프로젝트는 Java 17 타깃이다. 둘 다 그대로 쓰면 안 된다.

```bash
# Bash
JAVA_HOME="C:/Program Files/Java/jdk-17.0.19" ./mvnw -B test
```
```powershell
# PowerShell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.19'; .\mvnw.cmd -B test
```

저장소 루트의 `bash scripts/verify.sh` 가 위 JAVA_HOME 을 붙여 `test` 를 돌린다. 아래 표는 손으로 돌릴 때다.

| 명령 | 무엇을 확인하나 |
|---|---|
| `./mvnw -B compile` | 컴파일 통과 여부. 가장 싸다 |
| `./mvnw -B test` | 컴파일 + 컨텍스트 기동 + API 계약·서비스 로직 58건. **자바 코드 수정 후 필수** |
| `./mvnw -B package` | jar 생성까지. 배포 형태 확인 |
| `./mvnw -B spring-boot:run` | 실제 기동. `application.properties`에 키가 채워져 있어야 한다 |

테스트가 안 보는 것은 진짜 외부 API 와의 계약이다. 스펙이 바뀌어 응답 모양이 달라지면 가짜 서버는 옛 모양 그대로라 초록으로 남는다. 그건 기동해서 직접 부르거나, 백엔드를 띄운 채 프론트 `e2e/backend.spec.ts` 로 확인한다.

기동은 안 했으면, 확인한 범위와 못 한 범위를 나눠서 보고한다.

## doc 참조 트리거

아래 작업을 할 때만 해당 문서를 연다. 그 외에는 열지 않는다.

| 작업 | 문서 |
|---|---|
| 외부 API 요청 파라미터·응답 스펙·쿼터 확인 | [doc/backend/external-apis.md](../doc/backend/external-apis.md) |
| 배포 환경 변수·Railway 설정 변경 | [doc/deployment.md](../doc/deployment.md) |
| 라이브러리 버전 확인이 필요할 때 | `pom.xml` 직접 확인 |
