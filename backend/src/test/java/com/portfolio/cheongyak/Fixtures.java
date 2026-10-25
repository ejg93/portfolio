package com.portfolio.cheongyak;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 테스트용 공고·주택형. 기본은 서울 APT 민영 비규제, 공고일 2026-10-01 */
final class Fixtures {

    private Fixtures() {
    }

    static Notice.NoticeBuilder apt(String hmn) {
        return Notice.builder()
                .houseManageNo(hmn).pblancNo(hmn).kind("APT")
                .houseSecd("01").houseDtlSecd("01").rentSecd("0")
                .houseNm("테스트 " + hmn).areaNm("서울")
                .rcritPblancDe(LocalDate.of(2026, 10, 1))
                .rnk1CrspBgnde(LocalDate.of(2026, 10, 10)).rnk1CrspEndde(LocalDate.of(2026, 10, 10))
                .rnk1EtcBgnde(LocalDate.of(2026, 10, 11)).rnk1EtcEndde(LocalDate.of(2026, 10, 11))
                .specltRdnAt("N").mdatTrgetAt("N").publicHouseAt("N").nplnPubHouseAt("N")
                .pblancUrl("https://example.invalid/" + hmn)
                .rawJson("{\"id\":\"" + hmn + "\"}");
    }

    static Notice.NoticeBuilder remndr(String hmn) {
        return apt(hmn).kind("REMNDR").houseSecd("04").houseDtlSecd(null).rentSecd(null)
                .specltRdnAt(null).mdatTrgetAt(null).publicHouseAt(null).nplnPubHouseAt(null)
                .rnk1CrspBgnde(null).rnk1CrspEndde(null).rnk1EtcBgnde(null).rnk1EtcEndde(null)
                .rceptBgnde(LocalDate.of(2026, 10, 12)).rceptEndde(LocalDate.of(2026, 10, 12));
    }

    static HouseType.HouseTypeBuilder type(Notice n, String modelNo, double area) {
        return HouseType.builder()
                .houseManageNo(n.houseManageNo()).pblancNo(n.pblancNo()).modelNo(modelNo)
                .houseTy(String.format("%08.4fA", area)).exclusiveArea(BigDecimal.valueOf(area))
                .suplyHshldco(100).spsplyHshldco(0).topAmount(50000L)
                .rawJson("{\"m\":\"" + modelNo + "\"}");
    }
}
