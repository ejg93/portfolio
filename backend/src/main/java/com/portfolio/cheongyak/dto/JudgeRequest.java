package com.portfolio.cheongyak.dto;

import com.portfolio.cheongyak.rules.Applicant;

import java.util.List;

/** 판정 요청. notices 가 비면 기본 범위(수도권·최근 90일) 공고 전부를 판정한다 */
public record JudgeRequest(Applicant applicant, List<Key> notices) {

    public record Key(String houseManageNo, String pblancNo) {
    }
}
