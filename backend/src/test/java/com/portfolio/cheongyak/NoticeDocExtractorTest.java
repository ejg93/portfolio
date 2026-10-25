package com.portfolio.cheongyak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 문구는 2026-10-25 실측에서 본 실제 공고문 「단지 주요정보」 표와 자격 문장이다(공백 정규화 뒤)
class NoticeDocExtractorTest {

    private static final String BUCHEON = "※ 단지 주요정보 (분양문의) 010-0000-0000 주택유형 해당지역 기타지역 규제지역여부 민영 "
            + "경기도 부천시 1년 이상 계속 거주자(2024.10.28. 이전부터 계속하여 거주) 경기도 부천시 1년 미만 거주자 및 경기도, "
            + "서울특별시, 인천광역시 거주자 비규제지역 재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형 없음 1년 없음 미적용 민간택지";
    private static final String SEOUL_REG = "주택유형 해당지역 기타지역 규제지역여부 민영 서울특별시 2년 이상 계속 거주자 "
            + "(2023.10.31. 이전부터 계속 거주) 서울특별시 2년 미만 거주자 인천광역시 및 경기도 거주자 투기과열지구/청약과열지역 "
            + "재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형 10년 3년 3년 적용 민간택지";
    private static final String NAMYANGJU = "주택유형 해당지역 기타지역 규제지역여부 국민 경기도 남양주시 "
            + "수도권(서울특별시, 경기도, 인천광역시) 비규제지역 재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형 "
            + "3년 소유권이전등기일까지 없음 미적용 공공택지";
    private static final String REMNDR_NATION = "주택유형 규제지역여부 무순위(사후) 자격요건 민영 비규제지역 무순위(사후) "
            + "입주자모집공고일 현재 전국에 거주하는 무주택세대구성원 재당첨제한 전매제한 거주의무기간 분양가상한제 없음 없음 적용";
    private static final String POINT = "■ ②가점 - 전용면적별 1순위 가점제/추첨제 구분 가점제 추첨제 전용면적 60㎡ 이하 40% 60% "
            + "전용면적 60㎡ 초과 85㎡ 이하 70% 30% 전용면적 85㎡ 초과 - 100% - 가점 산정기준";

    @Test
    @DisplayName("부천 표 — 해당지역이 시 단위(부천시 1년), 재당첨 없음, 비규제")
    void bucheonCityLevel() {
        NoticeDoc d = NoticeDocExtractor.extract("APT", BUCHEON);
        assertThat(d.status()).isEqualTo("OK");
        assertThat(d.crspCity()).isEqualTo("부천");
        assertThat(d.crspMonths()).isEqualTo(12);
        assertThat(d.rewinYears()).isZero();
        assertThat(d.regulation()).isEqualTo("비규제");
        assertThat(d.resPriArea()).isEqualTo("부천");
    }

    @Test
    @DisplayName("서울 규제 표 — 시 단위 아님, 재당첨 10년, 투기과열, 서울 2년 거주 우선")
    void seoulRegulated() {
        NoticeDoc d = NoticeDocExtractor.extract("APT", SEOUL_REG);
        assertThat(d.crspCity()).isNull();
        assertThat(d.rewinYears()).isEqualTo(10);
        assertThat(d.regulation()).isEqualTo("투기과열");
        assertThat(d.resPriArea()).isEqualTo("서울");
        assertThat(d.resPriMonths()).isEqualTo(24);
    }

    @Test
    @DisplayName("남양주 표 — 「거주자」 꼬리가 없어도 수도권 앞에서 끊어 남양주, 기간 요건 없음 0")
    void namyangjuWithoutResidentWord() {
        NoticeDoc d = NoticeDocExtractor.extract("APT", NAMYANGJU);
        assertThat(d.crspCity()).isEqualTo("남양주");
        assertThat(d.crspMonths()).isZero();
        assertThat(d.rewinYears()).isEqualTo(3);
    }

    @Test
    @DisplayName("무순위 전국 표 — 전국 무주택, 재당첨 없음, 비규제")
    void remndrNationwide() {
        NoticeDoc d = NoticeDocExtractor.extract("REMNDR", REMNDR_NATION);
        assertThat(d.remndrArea()).isEqualTo("전국");
        assertThat(d.remndrHomeless()).isTrue();
        assertThat(d.rewinYears()).isZero();
        assertThat(d.regulation()).isEqualTo("비규제");
        assertThat(d.crspCity()).isNull();
    }

    @Test
    @DisplayName("무순위 자격 문장 변형 — 띄어 쓴 「무주택 세대구성원」, 붙여 쓴 「현재수도권(…)」")
    void remndrVariants() {
        assertThat(NoticeDocExtractor.extract("REMNDR", "입주자모집공고 현재 경기도에 거주하는 무주택 세대구성원인 성년자").remndrArea())
                .isEqualTo("경기");
        assertThat(NoticeDocExtractor.extract("REMNDR", "입주자모집공고일 현재수도권(서울특별시, 경기도, 인천광역시)에 거주하는 무주택세대구성원").remndrArea())
                .isEqualTo("수도권");
    }

    @Test
    @DisplayName("가점제 비율 표 — 60㎡ 이하 40, 85㎡ 이하 70, 85㎡ 초과 「-」 는 0")
    void pointShareTable() {
        NoticeDoc d = NoticeDocExtractor.extract("APT", POINT);
        assertThat(d.pointLe60()).isEqualTo(40);
        assertThat(d.pointLe85()).isEqualTo(70);
        assertThat(d.pointGt85()).isZero();
    }

    @Test
    @DisplayName("표가 없는 공고문(LH 형식) — 값은 전부 null, 받은 것 자체는 OK")
    void noTable() {
        NoticeDoc d = NoticeDocExtractor.extract("APT", "공급위치 서울특별시 강서구 마곡동 일원 공급규모 100세대");
        assertThat(d.status()).isEqualTo("OK");
        assertThat(d).extracting(NoticeDoc::crspCity, NoticeDoc::rewinYears, NoticeDoc::regulation, NoticeDoc::pointLe60)
                .containsOnlyNulls();
    }
}
