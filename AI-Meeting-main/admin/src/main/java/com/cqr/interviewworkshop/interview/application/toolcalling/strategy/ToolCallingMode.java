package com.cqr.interviewworkshop.interview.application.toolcalling.strategy;

public enum ToolCallingMode {
    NATIVE("native"),
    FALLBACK("fallback"),
    DISABLED(null);

    private final String strategyName;

    ToolCallingMode(String strategyName){
        this.strategyName = strategyName;
    }

    public String getStrategyName(){
        return strategyName;
    }
}
