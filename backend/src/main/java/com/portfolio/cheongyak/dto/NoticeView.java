package com.portfolio.cheongyak.dto;

import com.portfolio.cheongyak.HouseType;
import com.portfolio.cheongyak.Notice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 화면이 쓰는 공고 한 건. 원문(raw_json)은 빼고, 공고문 추출값은 doc 으로 묶는다 */
public record NoticeView(
        String houseManageNo,
        String pblancNo,
        String kind,
        String kindLabel,
        String houseSecd,
        String houseDtlSecd,
        String houseNm,
        String areaNm,
        String address,
        Integer totSuplyHshldco,
        LocalDate rcritPblancDe,
        LocalDate rceptBgnde,
        LocalDate rceptEndde,
        LocalDate spsplyBgnde,
        LocalDate spsplyEndde,
        LocalDate rnk1CrspBgnde,
        LocalDate rnk1CrspEndde,
        LocalDate rnk1GgBgnde,
        LocalDate rnk1GgEndde,
        LocalDate rnk1EtcBgnde,
        LocalDate rnk1EtcEndde,
        LocalDate przwnerDe,
        String specltRdnAt,
        String mdatTrgetAt,
        boolean regulated,
        boolean pubHousing,
        String pblancUrl,
        String hmpgAdres,
        Doc doc,
        List<Type> houseTypes
) {

    /** 공고문에서 뽑은 값. status 가 null 이면 아직 안 받았다 */
    public record Doc(String status, String remndrArea, Boolean remndrHomeless, Integer rewinYears, String regulation,
                      String crspCity, Integer crspMonths, String resPriArea, Integer resPriMonths,
                      Integer pointLe60, Integer pointLe85, Integer pointGt85) {
    }

    public record Type(String modelNo, String houseTy, BigDecimal exclusiveArea, BigDecimal supplyArea,
                       Integer suplyHshldco, Integer spsplyHshldco, Long topAmount) {
    }

    public static NoticeView of(Notice n, List<HouseType> types) {
        Doc doc = new Doc(n.docStatus(), n.docRemndrArea(),
                n.docRemndrHomeless() == null ? null : "Y".equals(n.docRemndrHomeless()),
                n.docRewinYears(), n.docRegulation(), n.docCrspCity(), n.docCrspMonths(), n.docResPriArea(),
                n.docResPriMonths(), n.docPointLe60(), n.docPointLe85(), n.docPointGt85());
        List<Type> ts = types.stream().map(t -> new Type(t.modelNo(), t.houseTy(), t.exclusiveArea(), t.supplyArea(),
                t.suplyHshldco(), t.spsplyHshldco(), t.topAmount())).toList();
        return new NoticeView(n.houseManageNo(), n.pblancNo(), n.kind(), kindLabel(n), n.houseSecd(), n.houseDtlSecd(),
                n.houseNm(), n.areaNm(), n.address(), n.totSuplyHshldco(), n.rcritPblancDe(), n.rceptBgnde(), n.rceptEndde(),
                n.spsplyBgnde(), n.spsplyEndde(), n.rnk1CrspBgnde(), n.rnk1CrspEndde(), n.rnk1GgBgnde(), n.rnk1GgEndde(),
                n.rnk1EtcBgnde(), n.rnk1EtcEndde(), n.przwnerDe(), n.specltRdnAt(), n.mdatTrgetAt(),
                "Y".equals(n.specltRdnAt()) || "Y".equals(n.mdatTrgetAt()),
                "Y".equals(n.publicHouseAt()) || "Y".equals(n.nplnPubHouseAt()),
                n.pblancUrl(), n.hmpgAdres(), doc, ts);
    }

    static String kindLabel(Notice n) {
        String secd = n.houseSecd() == null ? "" : n.houseSecd();
        return switch (n.kind()) {
            case "APT" -> switch (secd) {
                case "09" -> "민간사전청약";
                case "10" -> "신혼희망타운";
                default -> "1".equals(n.rentSecd()) ? "분양전환 임대" : "03".equals(n.houseDtlSecd()) ? "APT 국민" : "APT 민영";
            };
            case "REMNDR" -> "06".equals(secd) ? "불법행위 재공급" : "무순위";
            case "URBTY" -> n.houseDtlSecdNm() != null ? n.houseDtlSecdNm() : "오피스텔·도시형";
            case "PBLPVT" -> "공공지원 민간임대";
            case "OPT" -> "임의공급";
            default -> n.kind();
        };
    }
}
