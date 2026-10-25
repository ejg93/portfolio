package com.portfolio.cheongyak.rules;

import java.util.List;
import java.util.Map;

/**
 * 「주택공급에 관한 규칙」(2026-06-15 시행) 의 점수표·예치금표·1순위 요건·가점제 비율.
 * 엔진과 화면 규칙 표가 같은 상수를 본다 — GET /api/cheongyak/rules 가 이걸 그대로 내보낸다.
 */
public final class RuleTables {

    public static final String VERSION = "주택공급에 관한 규칙 2026-06-15 시행 · 별표1 2024-12-18 · 별표2 2023-05-10";

    public static final List<String> SIDOS = List.of("서울", "부산", "인천", "대구", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
    public static final List<String> CAPITAL = List.of("서울", "경기", "인천");
    /** 경기 31개 시군. 「시」「군」 을 뗀 이름 — 공고문 해당지역 시군과 같은 꼴 */
    public static final List<String> GG_CITIES = List.of("수원", "성남", "의정부", "안양", "부천", "광명", "평택", "동두천",
            "안산", "고양", "과천", "구리", "남양주", "오산", "시흥", "군포", "의왕", "하남", "용인", "파주", "이천", "안성",
            "김포", "화성", "광주", "양주", "포천", "여주", "연천", "가평", "양평");

    /** 별표2 예치금(만원). 행 = 전용 85·102·135㎡ 이하, 모든 면적 / 열 = 특별시·부산, 그 밖 광역시, 그 외 */
    public static final int[] DEPOSIT_AREA_LIMITS = {85, 102, 135};
    public static final int[][] DEPOSIT_TABLE = {{300, 250, 200}, {600, 400, 300}, {1000, 700, 400}, {1500, 1000, 500}};
    public static final List<String> DEPOSIT_COLUMNS = List.of("특별시·부산", "그 밖의 광역시", "특별시·광역시 외");

    public static final List<Map<String, String>> RANK1 = List.of(
            Map.of("type", "국민", "area", "수도권", "rule", "가입 12개월 + 12회 납입", "basis", "제27조①1가"),
            Map.of("type", "국민", "area", "수도권 외", "rule", "가입 6개월 + 6회 납입", "basis", "제27조①1나"),
            Map.of("type", "국민", "area", "투기과열·청약과열", "rule", "가입 24개월 + 24회 + 세대주 + 세대 5년 내 당첨 없음", "basis", "제27조①1다"),
            Map.of("type", "민영", "area", "수도권", "rule", "가입 12개월 + 별표2 예치금", "basis", "제28조①1가"),
            Map.of("type", "민영", "area", "수도권 외", "rule", "가입 6개월 + 별표2 예치금", "basis", "제28조①1나"),
            Map.of("type", "민영", "area", "투기과열·청약과열", "rule", "가입 24개월 + 예치금 + 세대주 + 세대 5년 내 당첨 없음 + 2주택 이상 세대 아님", "basis", "제28조①1다"));

    public static final List<Map<String, String>> POINT_SHARE = List.of(
            Map.of("area", "60㎡ 이하", "regulated", "40%", "other", "40% 이하 지자체 공고", "basis", "제28조②"),
            Map.of("area", "60㎡ 초과 85㎡ 이하", "regulated", "70%", "other", "40% 이하 지자체 공고", "basis", "제28조②"),
            Map.of("area", "85㎡ 초과", "regulated", "투기과열 80% · 청약과열 50%", "other", "추첨", "basis", "제28조④"));

    public record Rule(String code, String text, String basis) {
    }

    public static final List<Rule> RULES = List.of(
            new Rule("HOMELESS", "무주택기간은 만 30세(그 전 혼인이면 혼인신고일)부터, 집을 판 적 있으면 최근 무주택이 된 날부터 센다", "별표1 1-가"),
            new Rule("DEPENDENTS", "부양가족은 공고일 현재 같은 주민등록표 세대원. 직계존속은 3년 이상 등재", "별표1 1-나"),
            new Rule("SPOUSE_ACCOUNT", "배우자 통장 가입기간의 50% 를 점수표에 넣어 최대 3점을 더한다. 합계 17점 상한", "별표1 비고2"),
            new Rule("REMNDR", "무순위는 성년 무주택세대구성원. 거주지역은 공고문이 정한다", "제26조⑤"),
            new Rule("RES_PRIORITY", "수도권 투기과열지구는 해당지역 2년 이상 거주자 우선", "제4조⑤"),
            new Rule("POINT_EXCLUDED", "1주택 세대·2년 내 가점제 당첨 세대는 가점제에서 빠지고 추첨만", "제28조⑥"),
            new Rule("LOTTERY_HOMELESS", "추첨 물량의 75% 는 무주택세대구성원 우선", "제28조⑧"));

    private RuleTables() {
    }

    /** 무주택기간 점수(상한 32). 1년 미만 2, 이후 1년마다 2점 */
    public static int homelessPoints(int fullYears) {
        return fullYears >= 15 ? 32 : 2 + 2 * Math.max(0, fullYears);
    }

    /** 부양가족 점수(상한 35). 0명 5, 1명마다 5점 */
    public static int dependentPoints(int n) {
        return 5 + 5 * Math.min(Math.max(0, n), 6);
    }

    /** 통장 가입기간 점수(상한 17). 6개월 미만 1, 1년 미만 2, 이후 1년마다 1점 */
    public static int accountPoints(int months) {
        if (months < 6) {
            return 1;
        }
        if (months < 12) {
            return 2;
        }
        return Math.min(17, 2 + months / 12);
    }

    public static int depositColumn(String sido) {
        if ("서울".equals(sido) || "부산".equals(sido)) {
            return 0;
        }
        return List.of("인천", "대구", "광주", "대전", "울산").contains(sido) ? 1 : 2;
    }

    public static int requiredDeposit(String sido, double exclusiveArea) {
        int row = DEPOSIT_AREA_LIMITS.length;
        for (int i = 0; i < DEPOSIT_AREA_LIMITS.length; i++) {
            if (exclusiveArea <= DEPOSIT_AREA_LIMITS[i]) {
                row = i;
                break;
            }
        }
        return DEPOSIT_TABLE[row][depositColumn(sido)];
    }

    /**
     * 법정 가점제 비율. 규제(투기과열·청약과열, 또는 수도권 공공주택지구)일 때만 정해지고 그 밖은 null(사업주체·지자체 공고).
     * 85㎡ 초과는 투기과열 80%·청약과열 50%, 공공주택지구만이면 「80% 이하 지자체」 라 null
     */
    public static Double pointShare(double exclusiveArea, boolean regulatedOrPublic, boolean speculative, boolean overheated) {
        if (exclusiveArea <= 60) {
            return regulatedOrPublic ? 0.40 : null;
        }
        if (exclusiveArea <= 85) {
            return regulatedOrPublic ? 0.70 : null;
        }
        if (speculative) {
            return 0.80;
        }
        return overheated ? 0.50 : null;
    }
}
