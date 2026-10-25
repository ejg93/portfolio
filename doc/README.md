# doc/

코드를 봐도 안 나오는 것만 둔다. 라이브러리가 뭔지, 클래스가 무엇을 호출하는지는 안 쓴다 —
코드는 바뀌고 문서는 안 따라와서 문서 쪽이 거짓말이 된다.
2026-09 에 주가 기능을 지울 때 소개문 여덟 파일을 같이 고쳐야 했던 것이 그 사례다.

## 어디에 무엇이 있나

| 문서 | 무엇을 답하나 | 언제 여나 |
|---|---|---|
| [backend/external-apis.md](backend/external-apis.md) | 외부 API 7종의 엔드포인트·인증 방식·쿼터·정식 문서 링크 | 외부 API 요청·응답을 바꿀 때 |
| [backend/cheongyak-rule-audit.md](backend/cheongyak-rule-audit.md) | 청약 공고문 조건이 API·법령·공고문 중 어디서 판정되는지 실측(2026-10) | 청약 판정 규칙을 바꿀 때 |
| [frontend/kakao-maps-sdk.md](frontend/kakao-maps-sdk.md) | Kakao Maps JS SDK 로딩 방식과 JS 키·REST 키 구분 | `/public-data` 지도를 바꿀 때 |
| [deployment.md](deployment.md) | Vercel·Railway 설정, 환경변수 등록 위치, 배포 체크리스트 | 배포 설정·환경변수를 바꿀 때 |
| [design-standards/README.md](design-standards/README.md) | 행안부 공공 DB 표준화 지침 원본 색인 | toolbox 논리명 변환기 규칙을 바꿀 때 |

각 구역 CLAUDE.md 의 「doc 참조 트리거」가 여기로 보낸다. 트리거에 안 걸리면 안 연다.

## 문서에 안 쓰는 것

| 안 쓴다 | 어디서 답하나 |
|---|---|
| 라이브러리 소개·버전·핵심 개념 | 공식 문서, `package.json`·`pom.xml` |
| 클래스·폴더 구조, 무엇이 무엇을 호출하나 | 코드 검색 |
| 지나간 작업 이력 | git log, `/board` 개발 일지 |

쓰는 것은 외부 서비스의 스펙·키 종류·쿼터처럼 **코드에 안 적혀 있고 틀리면 배포가 깨지는 것**이다.

## 글 쓰는 규칙

루트 `CLAUDE.md` 「글 작성 규칙」을 따른다.
