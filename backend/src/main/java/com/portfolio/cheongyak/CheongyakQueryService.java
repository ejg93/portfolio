package com.portfolio.cheongyak;

import com.portfolio.cheongyak.dto.JudgeRequest;
import com.portfolio.cheongyak.dto.JudgeResponse;
import com.portfolio.cheongyak.dto.NoticeView;
import com.portfolio.cheongyak.rules.Judge;
import com.portfolio.cheongyak.rules.Judgement;
import com.portfolio.cheongyak.rules.RuleTables;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 공고 조회·판정·수집 이력·규칙 표. 판정 요청의 신청자 값은 계산에만 쓰고 어디에도 남기지 않는다 */
@Service
@RequiredArgsConstructor
public class CheongyakQueryService {

    public static final int DEFAULT_DAYS = 90;
    private static final int MAX_NOTICES = 500;

    private final NoticeRepository repo;

    /** area: capital(수도권) · all(전국) · 시도 이름 */
    public List<NoticeView> notices(LocalDate from, LocalDate to, String area, Set<String> kinds) {
        List<Notice> found = find(from, to, area, kinds);
        Map<String, List<HouseType>> types = repo.findHouseTypes(found);
        return found.stream().map(n -> NoticeView.of(n, types.getOrDefault(n.key(), List.of()))).toList();
    }

    public JudgeResponse judge(JudgeRequest req) {
        List<Notice> targets = new ArrayList<>();
        if (req.notices() == null || req.notices().isEmpty()) {
            targets.addAll(find(null, null, "capital", Set.of()));
        } else {
            for (JudgeRequest.Key k : req.notices().subList(0, Math.min(MAX_NOTICES, req.notices().size()))) {
                repo.findNotice(k.houseManageNo(), k.pblancNo()).ifPresent(targets::add);
            }
        }
        Map<String, List<HouseType>> types = repo.findHouseTypes(targets);
        List<Judgement> results = new ArrayList<>();
        for (Notice n : targets) {
            results.addAll(Judge.judge(n, types.getOrDefault(n.key(), List.of()), req.applicant()));
        }
        return new JudgeResponse(NoticeRepository.now(), results);
    }

    public List<CollectRun> runs(int limit) {
        return repo.recentRuns(Math.max(1, Math.min(limit, 100)));
    }

    public Optional<CollectRun> run(long id) {
        return repo.findRun(id);
    }

    public Map<String, Object> rules() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", RuleTables.VERSION);
        List<Map<String, Object>> homeless = new ArrayList<>();
        for (int y = 0; y <= 15; y++) {
            homeless.add(Map.of("label", y == 0 ? "1년 미만" : y == 15 ? "15년 이상" : y + "년 이상 " + (y + 1) + "년 미만",
                    "points", RuleTables.homelessPoints(y)));
        }
        out.put("homeless", homeless);
        List<Map<String, Object>> dependents = new ArrayList<>();
        for (int n = 0; n <= 6; n++) {
            dependents.add(Map.of("label", n == 6 ? "6명 이상" : n + "명", "points", RuleTables.dependentPoints(n)));
        }
        out.put("dependents", dependents);
        List<Map<String, Object>> account = new ArrayList<>();
        account.add(Map.of("label", "6개월 미만", "points", RuleTables.accountPoints(0)));
        account.add(Map.of("label", "6개월 이상 1년 미만", "points", RuleTables.accountPoints(6)));
        for (int y = 1; y <= 15; y++) {
            account.add(Map.of("label", y == 15 ? "15년 이상" : y + "년 이상 " + (y + 1) + "년 미만", "points", RuleTables.accountPoints(y * 12)));
        }
        out.put("account", account);
        out.put("deposit", Map.of("areaLimits", RuleTables.DEPOSIT_AREA_LIMITS, "columns", RuleTables.DEPOSIT_COLUMNS,
                "table", RuleTables.DEPOSIT_TABLE));
        out.put("rank1", RuleTables.RANK1);
        out.put("pointShare", RuleTables.POINT_SHARE);
        out.put("sidos", RuleTables.SIDOS);
        out.put("ggCities", RuleTables.GG_CITIES);
        out.put("rules", RuleTables.RULES);
        return out;
    }

    private List<Notice> find(LocalDate from, LocalDate to, String area, Set<String> kinds) {
        LocalDate today = NoticeRepository.now().toLocalDate();
        LocalDate f = from != null ? from : today.minusDays(DEFAULT_DAYS);
        LocalDate t = to != null ? to : today;
        Set<String> areas = switch (area == null ? "capital" : area) {
            case "capital" -> Set.copyOf(RuleTables.CAPITAL);
            case "all" -> Set.of();
            default -> Set.of(area);
        };
        return repo.findNotices(f, t, areas, kinds, MAX_NOTICES);
    }
}
