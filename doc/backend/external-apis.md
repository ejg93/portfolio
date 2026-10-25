# 외부 API 정리

백엔드 `service/` 계층에서 호출하는 외부 API 5종. 전부 HttpClient 5로 호출하고 Jackson으로 파싱한다. API 키는 `application.properties`(git 제외)와 Railway Variables에 등록.

## 1. Anthropic Claude API

- 사용처: `/ai-battle` → `POST /api/battle/chat` → `BattleService.java`
- 엔드포인트: `POST https://api.anthropic.com/v1/messages`
- 인증: `x-api-key` 헤더 (`anthropic.api.key`) + `anthropic-version` 헤더 필수
- 현재 코드 상태: 프론트에서 "opus"/"sonnet"을 선택해 보내지만, 실제로는 둘 다 `claude-haiku-4-5-20251001`로 매핑되어 있다 (비용 절약용으로 보임 — `resolveModel()` 참고). 시스템 프롬프트만 모델 선택에 따라 달라짐.
- 정식 문서:
  - 개발자 문서 홈: https://docs.claude.com/
  - Messages API 레퍼런스: https://docs.claude.com/en/api/messages
  - 모델 목록: https://docs.claude.com/en/docs/about-claude/models
  - 콘솔(키 발급): https://console.anthropic.com/

## 2. YouTube Data API v3

- 사용처: `/youtube` → `GET /api/youtube/{comments,replies}` → `YoutubeService.java`
- 엔드포인트: `https://www.googleapis.com/youtube/v3` (commentThreads, comments 리소스)
- 인증: API 키 쿼리 파라미터 (`youtube.api.key`)
- 쿼터 주의: 기본 일일 10,000 유닛. 댓글 조회는 리소스당 1 유닛이라 여유 있지만 초과 시 403 반환.
- 정식 문서:
  - API 홈: https://developers.google.com/youtube/v3
  - commentThreads: https://developers.google.com/youtube/v3/docs/commentThreads/list
  - comments: https://developers.google.com/youtube/v3/docs/comments/list
  - 키 발급(Google Cloud Console): https://console.cloud.google.com/apis/library/youtube.googleapis.com

## 3. Kakao Local REST API

- 사용처: 채용공고 주소 → 좌표 변환 → `JobService.java`
- 엔드포인트: `GET https://dapi.kakao.com/v2/local/search/keyword.json`
- 인증: `Authorization: KakaoAK {REST_API_키}` 헤더 (`kakao.rest.key`) — 프론트의 JavaScript 키와 다른 키다 ([kakao-maps-sdk.md](../frontend/kakao-maps-sdk.md) 참고)
- 정식 문서:
  - Local API 가이드: https://developers.kakao.com/docs/latest/ko/local/dev-guide
  - 키워드 장소 검색: https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword

## 4. 사람인 오픈 API

- 사용처: `/public-data` → `GET /api/jobs` → `JobService.java`
- 엔드포인트: `GET https://oapi.saramin.co.kr/job-search`
- 인증: `access-key` 쿼리 파라미터 (`saramin.access.key`)
- 정식 문서:
  - 오픈 API 홈: https://oapi.saramin.co.kr/
  - 채용정보 API 가이드: https://oapi.saramin.co.kr/guide/job-search

## 5. 한국부동산원 청약홈 분양정보 조회 서비스

- 사용처: `/cheongyak` → `POST /api/cheongyak/collect`(GitHub Actions cron) → `cheongyak/ApplyhomeClient.java`·`NoticeDocClient.java`
- 엔드포인트: Base `https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1`, 전부 GET. 종류마다 상세·주택형 오퍼레이션이 따로 있다

| 종류 | 상세 | 주택형 | 공고일 형식 |
|---|---|---|---|
| APT(민영·국민·신혼희망타운·사전청약) | `getAPTLttotPblancDetail` | `getAPTLttotPblancMdl` | `YYYY-MM-DD` |
| 오피스텔·도시형·민간임대·생숙 | `getUrbtyOfctlLttotPblancDetail` | `getUrbtyOfctlLttotPblancMdl` | `YYYY-MM-DD` |
| APT 잔여세대(무순위·불법행위 재공급) | `getRemndrLttotPblancDetail` | `getRemndrLttotPblancMdl` | `YYYY-MM-DD` |
| 공공지원 민간임대 | `getPblPvtRentLttotPblancDetail` | `getPblPvtRentLttotPblancMdl` | `YYYYMMDD` |
| 임의공급 | `getOPTLttotPblancDetail` | `getOPTLttotPblancMdl` | `YYYYMMDD` |

- 인증: `serviceKey` 쿼리(`data.go.kr.key`). 공공데이터포털 **일반 인증키 Decoding** 을 넣고 코드가 한 번 URL 인코딩한다 — Encoding 키를 넣으면 이중 인코딩으로 인증이 깨진다
- 쿼터: 개발계정 하루 40,000건
- 필터: `cond[필드::연산]=값`, 연산은 EQ·LT·LTE·GT·GTE·LIKE. 예 `cond[RCRIT_PBLANC_DE::GTE]=2026-01-01`. 응답은 `{page, perPage, totalCount, currentCount, matchCount, data}`, `perPage` 100 까지 받는다
- 무순위 상세에는 `SPECLT_RDN_EARTH_AT`(투기과열)·`MDAT_TRGET_AREA_SECD`(조정대상)·`PUBLIC_HOUSE_EARTH_AT`·`HOUSE_DTL_SECD` 가 없다. 규제 여부는 공고문에서 뽑는다. `GNRL_RNK1_ETC_GG_*`(경기 거주자 접수일)는 경기 공고 일부에만 있다
- 공고문 첨부: API 에 없다. 상세의 `PBLANC_URL` 화면 HTML 에서 `https://static.applyhome.co.kr/ai/aia/getAtchmnfl.do?…` 링크를 찾아 받는다. `User-Agent` 헤더가 있어야 하고, 요청 사이를 1초 띄운다. 파일명(`Content-Disposition`)에 `모집공고` 가 든 PDF 가 공고문이다. 2026-10 실측에서 약 8% 는 첨부 링크가 없다
- 정식 문서:
  - 공공데이터포털: https://www.data.go.kr/data/15098547/openapi.do
  - 스웨거: https://infuser.odcloud.kr/api/stages/37000/api-docs
