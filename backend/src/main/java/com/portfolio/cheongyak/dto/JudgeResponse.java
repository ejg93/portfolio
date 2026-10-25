package com.portfolio.cheongyak.dto;

import com.portfolio.cheongyak.rules.Judgement;

import java.time.LocalDateTime;
import java.util.List;

public record JudgeResponse(LocalDateTime judgedAt, List<Judgement> results) {
}
