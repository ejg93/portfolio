package com.portfolio.cheongyak;

import java.time.format.DateTimeFormatter;

/**
 * 청약홈 분양정보 API 의 공고 종류. 종류마다 상세·주택형 오퍼레이션이 따로 있고,
 * 공공지원 민간임대·임의공급만 공고일을 YYYYMMDD 로 받는다(나머지는 YYYY-MM-DD).
 */
public enum ApplyhomeKind {

    APT("getAPTLttotPblancDetail", "getAPTLttotPblancMdl", "yyyy-MM-dd"),
    URBTY("getUrbtyOfctlLttotPblancDetail", "getUrbtyOfctlLttotPblancMdl", "yyyy-MM-dd"),
    REMNDR("getRemndrLttotPblancDetail", "getRemndrLttotPblancMdl", "yyyy-MM-dd"),
    PBLPVT("getPblPvtRentLttotPblancDetail", "getPblPvtRentLttotPblancMdl", "yyyyMMdd"),
    OPT("getOPTLttotPblancDetail", "getOPTLttotPblancMdl", "yyyyMMdd");

    private final String detailOp;
    private final String modelOp;
    private final DateTimeFormatter dateFormat;

    ApplyhomeKind(String detailOp, String modelOp, String datePattern) {
        this.detailOp = detailOp;
        this.modelOp = modelOp;
        this.dateFormat = DateTimeFormatter.ofPattern(datePattern);
    }

    public String detailOp() {
        return detailOp;
    }

    public String modelOp() {
        return modelOp;
    }

    public DateTimeFormatter dateFormat() {
        return dateFormat;
    }
}
