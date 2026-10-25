package com.portfolio.cheongyak;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 입주자모집공고문 텍스트에서 정형 문구만 정규식으로 뽑는다. 해석(AI)은 하지 않는다.
 * 정규식은 2026-10-25 실측(doc/backend/cheongyak-rule-audit.md)에서 수도권 1년치 공고로 맞춘 것이고,
 * 공고문 첫머리 「단지 주요정보」 표와 무순위 자격 문장을 본다. 못 찾으면 그 값은 null 이다.
 */
public final class NoticeDocExtractor {

    // 표 머리의 규제 칸 값. 「투기과열지구/청약과열지역」 은 투기과열로 친다
    private static final String REG = "(비규제지역|투기과열지구\\s*/\\s*청약과열지역|투기과열지구|청약과열지역|조정대상지역)";
    private static final Pattern REGULATION = Pattern.compile("규제지역여부.{0,400}?" + REG);
    private static final Pattern TABLE = Pattern.compile("규제지역여부\\s*(?:민영|국민|공공\\S*)\\s*(.{0,300}?)" + REG);
    private static final Pattern REWIN_TABLE = Pattern.compile(
            "재당첨\\s*제한\\s*전매\\s*제한\\s*거주의무기간\\s*분양가상한제(?:\\s*택지유형)?\\s*(없음|\\d+\\s*년)");
    private static final Pattern REWIN_TEXT = Pattern.compile("재당첨\\s*제한[^\\d]{0,20}(\\d+)\\s*년");
    private static final Pattern REMNDR = Pattern.compile(
            "현재\\s*([^에]{1,40}?)에\\s*거주하는\\s*(?:성년자인\\s*)?(무주택\\s*세대구성원|성년자|무주택)");
    private static final Pattern RES_PRI = Pattern.compile(
            "(서울특별시|서울시|서울|인천광역시|인천|경기도|경기)\\s*(?:([가-힣]{1,5})시\\s*)?(\\d+)\\s*년\\s*이상\\s*(?:계속\\s*)?거주");
    private static final Pattern SIDO_WORDS = Pattern.compile("서울특별시|서울시|인천광역시|경기도");
    private static final Pattern CITY = Pattern.compile("([가-힣]{1,4})(시|군)(?![가-힣])");
    private static final Pattern YEARS_OR_MORE = Pattern.compile("(\\d+)\\s*년\\s*이상");
    private static final Pattern POINT_TABLE = Pattern.compile("가점제\\s*추첨제\\s*(전용면적.{0,200})");
    private static final Pattern POINT_LE60 = Pattern.compile("60\\s*㎡\\s*이하\\s*(\\d+|-)");
    private static final Pattern POINT_LE85 = Pattern.compile("60\\s*㎡\\s*초과\\s*85\\s*㎡\\s*이하\\s*(\\d+|-)");
    private static final Pattern POINT_GT85 = Pattern.compile("85\\s*㎡\\s*초과\\s*(\\d+|-)");
    // 첫머리 표는 앞쪽에 있다. 본문 전체에서 찾으면 다른 단지·예시 문구에 걸린다
    private static final int HEAD = 6000;
    // 수도권 밖 시도 표기 → 짧은 이름. 「광주」 만 쓰면 경기 광주시와 섞이니 광주는 광역시 표기만 본다
    private static final String[][] OTHER_SIDOS = {
        {"부산", "부산"}, {"대구", "대구"}, {"광주광역시", "광주"}, {"대전", "대전"}, {"울산", "울산"},
        {"세종", "세종"}, {"강원", "강원"}, {"충청북도", "충북"}, {"충북", "충북"}, {"충청남도", "충남"},
        {"충남", "충남"}, {"전라북도", "전북"}, {"전북", "전북"}, {"전라남도", "전남"}, {"전남", "전남"},
        {"경상북도", "경북"}, {"경북", "경북"}, {"경상남도", "경남"}, {"경남", "경남"}, {"제주", "제주"},
    };

    private NoticeDocExtractor() {
    }

    /** kind 는 APT 또는 REMNDR. 그 밖은 값 없이 OK */
    public static NoticeDoc extract(String kind, String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ");
        String head = flat.length() > HEAD ? flat.substring(0, HEAD) : flat;
        NoticeDoc.NoticeDocBuilder b = NoticeDoc.builder().status(NoticeDoc.OK)
                .rewinYears(rewinYears(flat, head)).regulation(regulation(head));
        if ("REMNDR".equals(kind)) {
            Matcher m = REMNDR.matcher(flat);
            if (m.find()) {
                b.remndrArea(area(m.group(1))).remndrHomeless(m.group(2).contains("무주택"));
            }
            return b.build();
        }
        if ("APT".equals(kind)) {
            String crsp = crspSegment(head);
            if (crsp != null) {
                Matcher city = CITY.matcher(SIDO_WORDS.matcher(crsp).replaceAll(" "));
                if (city.find()) {
                    Matcher y = YEARS_OR_MORE.matcher(crsp);
                    b.crspCity(city.group(1)).crspMonths(y.find() ? Integer.parseInt(y.group(1)) * 12 : 0);
                }
            }
            Matcher r = RES_PRI.matcher(flat);
            if (r.find()) {
                b.resPriArea(r.group(2) != null ? r.group(2) : area(r.group(1))).resPriMonths(Integer.parseInt(r.group(3)) * 12);
            }
            Matcher pt = POINT_TABLE.matcher(flat);
            if (pt.find()) {
                String t = pt.group(1);
                b.pointLe60(point(POINT_LE60, t)).pointLe85(point(POINT_LE85, t)).pointGt85(point(POINT_GT85, t));
            }
        }
        return b.build();
    }

    /**
     * 지역 문구 → 수도권 · 전국 · 서울/인천/경기(여럿이면 ·) · 수도권 밖 시도 하나(짧은 이름).
     * 어느 것도 못 고르면 null(「해당 주택건설지역인 수원특례시」 처럼 시 단위만 적힌 경우) — 판정은 공고 확인으로 간다
     */
    static String area(String s) {
        if (s.contains("수도권")) {
            return "수도권";
        }
        if (s.contains("전국") || s.contains("국내")) {
            return "전국";
        }
        List<String> hits = new ArrayList<>();
        for (String sido : List.of("서울", "인천", "경기")) {
            if (s.contains(sido)) {
                hits.add(sido);
            }
        }
        if (!hits.isEmpty()) {
            return String.join("·", hits);
        }
        String other = null;
        for (String[] sido : OTHER_SIDOS) {
            if (s.contains(sido[0])) {
                if (other != null && !other.equals(sido[1])) {
                    return null;
                }
                other = sido[1];
            }
        }
        return other;
    }

    private static Integer rewinYears(String flat, String head) {
        Matcher m = REWIN_TABLE.matcher(head);
        if (m.find()) {
            return "없음".equals(m.group(1)) ? 0 : Integer.parseInt(m.group(1).replaceAll("\\D", ""));
        }
        m = REWIN_TEXT.matcher(flat);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static String regulation(String head) {
        Matcher m = REGULATION.matcher(head);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1).replaceAll("\\s", "");
        if ("비규제지역".equals(v)) {
            return "비규제";
        }
        return v.startsWith("투기과열") ? "투기과열" : "청약과열";
    }

    /** 표의 해당지역 칸. 첫 「거주자」 까지, 없으면 첫 「수도권」 앞까지(「경기도 남양주시 수도권(…)」 꼴) */
    private static String crspSegment(String head) {
        Matcher m = TABLE.matcher(head);
        if (!m.find()) {
            return null;
        }
        String seg = m.group(1);
        int resident = seg.indexOf("거주자");
        if (resident >= 0) {
            return seg.substring(0, resident + "거주자".length());
        }
        int capital = seg.indexOf("수도권");
        return capital >= 0 ? seg.substring(0, capital) : null;
    }

    private static Integer point(Pattern p, String t) {
        Matcher m = p.matcher(t);
        if (!m.find()) {
            return null;
        }
        if ("-".equals(m.group(1))) {
            return 0;
        }
        // 비율은 5% 단위다. 84·93 처럼 전용면적이 잘못 걸리는 공고가 있다(실측 계양 카이브 유보라)
        int v = Integer.parseInt(m.group(1));
        return v <= 100 && v % 5 == 0 ? v : null;
    }
}
