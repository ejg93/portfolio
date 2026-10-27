# 외부 API 정리

백엔드 `service/`·`cheongyak/` 에서 호출하는 외부 API 7종. 전부 HttpClient 5로 호출하고 Jackson으로 파싱한다. API 키는 `application.properties`(git 제외)와 Railway Variables에 등록.

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

## 6. 한국부동산원 청약홈 청약접수 경쟁률 및 특별공급 신청현황 조회 서비스

- 사용처: 수집의 결과 단계 → `cheongyak/ApplyhomeClient.java` `fetchCmpet`·`fetchScores`. 당첨 발표가 지난 판정 대상 공고만, 공고마다 한 번
- 엔드포인트: Base `https://api.odcloud.kr/api/ApplyhomeInfoCmpetRtSvc/v1`, 전부 GET. 인증·봉투·`cond[…]` 문법은 5절과 같다(같은 `data.go.kr.key`, 활용신청은 따로)

| 오퍼레이션 | 내용 | 쓰나 |
|---|---|---|
| `getAPTLttotPblancCmpet` | APT 경쟁률(주택형 × 순위 × 거주 구분) | 쓴다 |
| `getAptLttotPblancScore` | APT 당첨가점(최저·최고·평균) | 쓴다 |
| `getRemndrLttotPblancCmpet` | 잔여세대(무순위) 경쟁률 | 쓴다 |
| `getAPTSpsplyReqstStus` | APT 특별공급 신청현황 | 안 쓴다(특공 판정 없음) |
| `getUrbtyOfctlLttotPblancCmpet`·`getPblPvtRentLttotPblancCmpet`·`getCancResplLttotPblancCmpet`·`getOPTLttotPblancCmpet` | 판정 대상 아닌 종류 | 안 쓴다 |

- 조건: `cond[HOUSE_MANAGE_NO::EQ]`·`cond[PBLANC_NO::EQ]`(APT 는 `cond[RESIDE_SECD::EQ]` 도 받는다)
- 경쟁률 필드: `HOUSE_TY`, `RESIDE_SECD`·`RESIDE_SENM`(01 해당지역·02 기타지역·03 기타경기), `SUBSCRPT_RANK_CODE`(숫자 1·2, 무순위에는 없다), `SUPLY_HSHLDCO`(숫자), `REQ_CNT`(문자열 숫자), `CMPET_RATE`(문자열). 무순위는 거주 구분 대신 `REMNDR_HSHLD_PBLANC_TYCD`
- `CMPET_RATE` 는 숫자·`-`(접수 0)·`(△3)`(미달 세대 수) 셋이다. 숫자가 아니면 `cmpet_text` 에 그대로 남기고, 화면이 `(△N)` 을 「미달 N」으로 그린다
- 당첨가점 필드: `LWET_SCORE`(최저)·`TOP_SCORE`(최고)·`AVRG_SCORE`(평균, 소수 둘째 자리) — 셋 다 문자열. 발표 전은 `-`. 최고가 `0` 이면 최저·평균도 늘 `0` 이고 가점제 당첨자가 없는 행이라 세 값을 다 비운다(해당지역 171·기타지역 968 / 7,000행)
- 2026-10-27 실측: APT 경쟁률 55,524행·무순위 4,329행·당첨가점 30,020행. 첫 페이지가 최신 공고다
- 발표 직후에는 비어 있을 수 있다 — 발표 14일 안은 다음 실행에 다시 보고, 지나면 `result_status = NONE`
- 정식 문서:
  - 공공데이터포털: https://www.data.go.kr/data/15098905/openapi.do
  - 스웨거: https://infuser.odcloud.kr/api/stages/36148/api-docs

## 7. 국토교통부 아파트 매매 실거래가 상세 자료

- 사용처: 수집의 실거래 단계 → `cheongyak/RtmsClient.java`. 지오코딩된 판정 대상 공고의 시군구만, 최근 12개월
- 엔드포인트: `GET https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev?serviceKey=&LAWD_CD=&DEAL_YMD=&numOfRows=1000&pageNo=`
- 인증: 5절과 같은 `data.go.kr.key`(Decoding, 한 번 인코딩). 활용신청은 따로
- 응답: **XML**. 정상 `response/header/resultCode` = `000`, 거래는 `response/body/items/item`, 전체 수는 `totalCount`(1000 을 넘으면 `pageNo` 를 올린다). 키·쿼터 오류는 다른 봉투 `OpenAPI_ServiceResponse/cmmMsgHeader/returnReasonCode`
- 항목: `aptNm`, `umdNm`(법정동), `jibun`, `excluUseAr`(전용㎡), `dealYear·dealMonth·dealDay`, `dealAmount`(만원, 쉼표), `floor`, `buildYear`, `cdealType`(해제면 `O`, 아니면 공백), `dealingGbn`(중개·직거래). 2026-10-27 실측: 강남구·분당구 3개월 중 한 달 최대 470건이라 1000 을 넘는 달은 없었다
- 거래 ID 가 없다 — (시군구, 계약월) 단위로 통째로 지우고 다시 넣는다. 이번 달·지난달은 신고가 계속 들어와 7일마다 다시 받는다
- 쿼터: 활용신청 화면의 값을 따른다(개발계정 기준 일 1만 건 안팎으로 알려져 있다)
- 정식 문서: https://www.data.go.kr/data/15126468/openapi.do

## 8. 행정안전부 도로명주소 검색 API

- 사용처: 수집의 지오코딩 단계 → `cheongyak/JusoGeoClient.java`. 공고 주소 → 법정동코드
- 엔드포인트: `GET https://business.juso.go.kr/addrlink/addrLinkApi.do?confmKey=&keyword=&resultType=json&currentPage=1&countPerPage=1`
- 인증: `confmKey` 쿼리 파라미터 (`juso.key`). 주소기반산업지원서비스에서 검색 API 를 신청한다(민간기관, 사업자번호 칸 없음). 샘플 키 `TESTJUSOGOKR` 는 검색어와 상관없이 고정 예시만 돌려준다
- 응답: `results.common.errorCode` 가 `0` 이면 정상, 결과는 `results.juso[]`. 쓰는 항목은 `admCd`(법정동코드 10자리, 앞 5자리가 실거래 API 의 `LAWD_CD`)·`emdNm`·`jibunAddr`. 좌표는 없다
- 오류 코드: `E0001`(승인키)·`E0014`(개발키 기간 만료)·`-999`(시스템)는 단계를 멈춘다. 나머지는 검색어 문제라 다음 후보로 넘어간다. 검색어에 `%` `=` `>` `<` 와 SQL 예약어가 섞이면 오류 코드가 온다
- 지금 있는 건물만 찾는다. 분양 단지는 번지가 비어 있는 일이 많아서 번지 → 읍면동 → 괄호 안 주소 → 그 읍면동 순서로 줄여 가며 부른다. 2026-10-26 실측: 수도권 공고 주소 155건 중 148건(95%) 일치, 호출 247회. 못 찾은 7건 중 6건은 주소에 읍면동 없이 지구 이름만 있는 공고
- 정식 문서: https://business.juso.go.kr/addrlink/openApi/searchApi.do

참고한 공개 저장소(설계만 보고 코드는 가져오지 않았다, 2026-10): leeyunbo/house-ping(MIT — 시세 배지 틀·경쟁률 API), choiyounggi/chungyak-alimi(LH API 호출 방식), BaekJaeIn/cheongYak-radar(LH 상세 접수일)
