package com.portfolio.cheongyak;

/** 이미 RUNNING 인 수집이 있을 때 새로 시작하지 않는다 */
public class CollectBusyException extends RuntimeException {

    public CollectBusyException() {
        super("수집이 이미 돌고 있다");
    }
}
