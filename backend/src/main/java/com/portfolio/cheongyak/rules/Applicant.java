package com.portfolio.cheongyak.rules;

import lombok.Builder;

import java.time.LocalDate;

/**
 * 판정 요청의 신청자 조건. 전부 사용자가 넣은 값이고 서버는 저장하지 않는다(로그에도 안 남긴다).
 * householdHomeless 는 제53조 예외(60세 이상 직계존속 소유 등)를 본인이 적용한 뒤의 판단이다.
 */
@Builder(toBuilder = true)
public record Applicant(
        String residenceSido,        // 서울|부산|인천|대구|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주
        LocalDate birthDate,
        LocalDate marriageDate,      // 없으면 null
        boolean householdHomeless,   // 세대 전원 무주택
        LocalDate homelessSince,     // 유주택 이력이 있으면 최근 무주택이 된 날
        int householdHouseCount,     // 0 / 1 / 2(2 이상)
        boolean householder,
        int dependents,              // 별표1 1-나 기준. 배우자 포함, 본인 제외
        int accountMonths,           // 청약통장 최초 가입부터 개월
        int paymentCount,            // 납입 인정 횟수
        long depositManwon,          // 현재 예치금(만원)
        long savingsTotalManwon,     // 저축총액(만원). 입력값 그대로 쓴다
        int spouseAccountMonths,     // 배우자 통장 가입 개월, 없으면 0
        boolean wonWithin5y,         // 세대 내 5년 내 당첨자
        boolean wonByPointsWithin2y, // 2년 내 가점제 당첨 세대
        int residenceMonthsInSido,   // 현 시도 연속 거주 개월
        String residenceCity,        // 경기 거주자의 시군(「시」「군」 뗀 이름). 그 밖이거나 미입력이면 null
        int residenceMonthsInCity    // 현 시군 연속 거주 개월
) {
}
