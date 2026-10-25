package com.portfolio.cheongyak;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 청약홈 응답 한 줄(JsonNode) → Notice·HouseType. 종류마다 접수일 필드 이름이 다르다.
 * 숫자처럼 생긴 관리번호도 문자열로 받는다. 빈 문자열은 null 로 바꾼다.
 */
public final class NoticeMapper {

    private static final Pattern LEADING_AREA = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)");

    private NoticeMapper() {
    }

    public static Notice toNotice(ApplyhomeKind kind, JsonNode d) {
        Notice.NoticeBuilder b = Notice.builder()
                .houseManageNo(s(d, "HOUSE_MANAGE_NO")).pblancNo(s(d, "PBLANC_NO")).kind(kind.name())
                .houseSecd(s(d, "HOUSE_SECD")).houseSecdNm(s(d, "HOUSE_SECD_NM"))
                .houseDtlSecd(s(d, "HOUSE_DTL_SECD")).houseDtlSecdNm(s(d, "HOUSE_DTL_SECD_NM"))
                .rentSecd(s(d, "RENT_SECD")).houseNm(nvl(s(d, "HOUSE_NM"), "(이름 없음)"))
                .areaCode(s(d, "SUBSCRPT_AREA_CODE")).areaNm(s(d, "SUBSCRPT_AREA_CODE_NM")).address(s(d, "HSSPLY_ADRES"))
                .totSuplyHshldco(i(d, "TOT_SUPLY_HSHLDCO")).rcritPblancDe(date(d, "RCRIT_PBLANC_DE"))
                .spsplyBgnde(date(d, "SPSPLY_RCEPT_BGNDE")).spsplyEndde(date(d, "SPSPLY_RCEPT_ENDDE"))
                .przwnerDe(date(d, "PRZWNER_PRESNATN_DE"))
                .cntrctBgnde(date(d, "CNTRCT_CNCLS_BGNDE")).cntrctEndde(date(d, "CNTRCT_CNCLS_ENDDE"))
                .mvnPrearngeYm(s(d, "MVN_PREARNGE_YM"))
                .specltRdnAt(s(d, "SPECLT_RDN_EARTH_AT")).mdatTrgetAt(s(d, "MDAT_TRGET_AREA_SECD"))
                .parcprcUlsAt(s(d, "PARCPRC_ULS_AT")).imprmnBsnsAt(s(d, "IMPRMN_BSNS_AT"))
                .publicHouseAt(s(d, "PUBLIC_HOUSE_EARTH_AT")).lrsclBldlndAt(s(d, "LRSCL_BLDLND_AT"))
                .nplnPubHouseAt(s(d, "NPLN_PRVOPR_PUBLIC_HOUSE_AT"))
                .bsnsMbyNm(s(d, "BSNS_MBY_NM")).cnstrctEntrpsNm(s(d, "CNSTRCT_ENTRPS_NM"))
                .hmpgAdres(s(d, "HMPG_ADRES")).pblancUrl(s(d, "PBLANC_URL"))
                .rawJson(d.toString());
        switch (kind) {
            case APT -> b.rceptBgnde(date(d, "RCEPT_BGNDE")).rceptEndde(date(d, "RCEPT_ENDDE"))
                    .rnk1CrspBgnde(date(d, "GNRL_RNK1_CRSPAREA_RCPTDE")).rnk1CrspEndde(date(d, "GNRL_RNK1_CRSPAREA_ENDDE"))
                    .rnk1GgBgnde(date(d, "GNRL_RNK1_ETC_GG_RCPTDE")).rnk1GgEndde(date(d, "GNRL_RNK1_ETC_GG_ENDDE"))
                    .rnk1EtcBgnde(date(d, "GNRL_RNK1_ETC_AREA_RCPTDE")).rnk1EtcEndde(date(d, "GNRL_RNK1_ETC_AREA_ENDDE"))
                    .rnk2CrspBgnde(date(d, "GNRL_RNK2_CRSPAREA_RCPTDE")).rnk2CrspEndde(date(d, "GNRL_RNK2_CRSPAREA_ENDDE"))
                    .rnk2GgBgnde(date(d, "GNRL_RNK2_ETC_GG_RCPTDE")).rnk2GgEndde(date(d, "GNRL_RNK2_ETC_GG_ENDDE"))
                    .rnk2EtcBgnde(date(d, "GNRL_RNK2_ETC_AREA_RCPTDE")).rnk2EtcEndde(date(d, "GNRL_RNK2_ETC_AREA_ENDDE"));
            case REMNDR, OPT -> b.rceptBgnde(date(d, "SUBSCRPT_RCEPT_BGNDE")).rceptEndde(date(d, "SUBSCRPT_RCEPT_ENDDE"))
                    .rnk1CrspBgnde(date(d, "GNRL_RCEPT_BGNDE")).rnk1CrspEndde(date(d, "GNRL_RCEPT_ENDDE"));
            case URBTY, PBLPVT -> b.rceptBgnde(date(d, "SUBSCRPT_RCEPT_BGNDE")).rceptEndde(date(d, "SUBSCRPT_RCEPT_ENDDE"));
        }
        return b.build();
    }

    /** 공고 하나의 주택형들. 모델번호가 비거나 겹치면 순번으로 메운다(키가 겹치면 저장이 깨진다) */
    public static List<HouseType> toHouseTypes(ApplyhomeKind kind, Notice n, List<JsonNode> rows) {
        List<HouseType> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int seq = 0;
        for (JsonNode d : rows) {
            seq++;
            String model = s(d, "MODEL_NO");
            if (model == null || !seen.add(model)) {
                model = "#" + seq;
                seen.add(model);
            }
            boolean byType = kind == ApplyhomeKind.APT || kind == ApplyhomeKind.REMNDR || kind == ApplyhomeKind.OPT;
            out.add(HouseType.builder()
                    .houseManageNo(n.houseManageNo()).pblancNo(n.pblancNo()).modelNo(model)
                    .houseTy(s(d, "HOUSE_TY"))
                    .exclusiveArea(byType ? leadingArea(s(d, "HOUSE_TY")) : decimal(s(d, "EXCLUSE_AR")))
                    .supplyArea(decimal(s(d, "SUPLY_AR")))
                    .suplyHshldco(i(d, "SUPLY_HSHLDCO")).spsplyHshldco(i(d, "SPSPLY_HSHLDCO"))
                    .mnychHshldco(i(d, "MNYCH_HSHLDCO")).nwwdsHshldco(i(d, "NWWDS_HSHLDCO"))
                    .lfeFrstHshldco(i(d, "LFE_FRST_HSHLDCO")).oldParntsHshldco(i(d, "OLD_PARNTS_SUPORT_HSHLDCO"))
                    .insttHshldco(i(d, "INSTT_RECOMEND_HSHLDCO")).etcHshldco(i(d, "ETC_HSHLDCO"))
                    .transrHshldco(i(d, "TRANSR_INSTT_ENFSN_HSHLDCO")).ygmnHshldco(i(d, "YGMN_HSHLDCO"))
                    .nwbbHshldco(i(d, "NWBB_HSHLDCO"))
                    .topAmount(amount(nvl(s(d, "LTTOT_TOP_AMOUNT"), s(d, "SUPLY_AMOUNT"))))
                    .rawJson(d.toString())
                    .build());
        }
        return out;
    }

    private static String s(JsonNode d, String field) {
        JsonNode v = d.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String t = v.isNumber() ? v.asString("") : v.asString("").trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer i(JsonNode d, String field) {
        JsonNode v = d.path(field);
        if (v.isNumber()) {
            return v.asInt(0);
        }
        String t = s(d, field);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode d, String field) {
        return ApplyhomeDates.parse(s(d, field));
    }

    private static BigDecimal leadingArea(String houseTy) {
        if (houseTy == null) {
            return null;
        }
        Matcher m = LEADING_AREA.matcher(houseTy);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private static BigDecimal decimal(String v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long amount(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(v.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nvl(String a, String b) {
        return a != null ? a : b;
    }
}
