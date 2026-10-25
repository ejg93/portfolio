package com.portfolio.cheongyak;

import lombok.Builder;

/**
 * 공고문 PDF 에서 뽑은 값. cheongyak_notice 의 doc_* 컬럼과 1:1(doc_fetched_at 은 저장소가 채운다).
 * status 가 OK 가 아니면 값은 전부 null 이다 — 판정은 그 항목을 「확인 필요」 로 띄운다.
 */
@Builder
public record NoticeDoc(
        String status,
        String remndrArea,
        Boolean remndrHomeless,
        Integer rewinYears,
        String regulation,
        String crspCity,
        Integer crspMonths,
        String resPriArea,
        Integer resPriMonths,
        Integer pointLe60,
        Integer pointLe85,
        Integer pointGt85
) {

    public static final String OK = "OK";
    /** 받아서 뽑았지만 컬럼에 안 들어가 저장 못 함 */
    public static final String SAVE_ERR = "SAVE_ERR";

    /** 첨부를 못 받았거나 못 읽은 경우. 값 없이 사유만 남긴다 */
    public static NoticeDoc failed(String status) {
        return NoticeDoc.builder().status(status).build();
    }
}
