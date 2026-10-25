package com.portfolio.cheongyak;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 청약 공고 한 건. 필드는 V1 의 cheongyak_notice 컬럼과 1:1 이다(스네이크 → 카멜).
 * null 이 올 수 있는 컬럼은 전부 래퍼 타입이다 — 원시 타입에 null 이 오면 행 매핑이 터진다.
 * doc_* 는 공고문 PDF 에서 뽑은 값이고, 아직 못 받았으면 docStatus 까지 null 이다.
 */
@Builder(toBuilder = true)
public record Notice(
        String houseManageNo,
        String pblancNo,
        String kind,
        String houseSecd,
        String houseSecdNm,
        String houseDtlSecd,
        String houseDtlSecdNm,
        String rentSecd,
        String houseNm,
        String areaCode,
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
        LocalDate rnk2CrspBgnde,
        LocalDate rnk2CrspEndde,
        LocalDate rnk2GgBgnde,
        LocalDate rnk2GgEndde,
        LocalDate rnk2EtcBgnde,
        LocalDate rnk2EtcEndde,
        LocalDate przwnerDe,
        LocalDate cntrctBgnde,
        LocalDate cntrctEndde,
        String mvnPrearngeYm,
        String specltRdnAt,
        String mdatTrgetAt,
        String parcprcUlsAt,
        String imprmnBsnsAt,
        String publicHouseAt,
        String lrsclBldlndAt,
        String nplnPubHouseAt,
        String bsnsMbyNm,
        String cnstrctEntrpsNm,
        String hmpgAdres,
        String pblancUrl,
        String docStatus,
        String docRemndrArea,
        String docRemndrHomeless,
        Integer docRewinYears,
        String docRegulation,
        String docCrspCity,
        Integer docCrspMonths,
        String docResPriArea,
        Integer docResPriMonths,
        Integer docPointLe60,
        Integer docPointLe85,
        Integer docPointGt85,
        LocalDateTime docFetchedAt,
        String rawJson,
        LocalDateTime firstSeenAt,
        LocalDateTime updatedAt
) {

    /** 공고 키. 주택형 묶음의 map key 로도 쓴다 */
    public String key() {
        return houseManageNo + ":" + pblancNo;
    }
}
