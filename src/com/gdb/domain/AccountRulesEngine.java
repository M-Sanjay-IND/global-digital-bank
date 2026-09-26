package com.gdb.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class AccountRulesEngine {

    public enum TenureTier {
        NEW,
        STANDARD,
        PREMIUM,
        PRIVILEGE
    }

    private static final Map<TenureTier, Double> SAVINGS_MIN_BALANCE_MAP = new HashMap<>();
    private static final Map<TenureTier, Double> SAVINGS_INTEREST_RATE_MAP = new HashMap<>();
    private static final TreeMap<Integer, Double> FD_INTEREST_RATE_MAP = new TreeMap<>();

    static {
        SAVINGS_MIN_BALANCE_MAP.put(TenureTier.NEW, 10000.0);
        SAVINGS_MIN_BALANCE_MAP.put(TenureTier.STANDARD, 7500.0);
        SAVINGS_MIN_BALANCE_MAP.put(TenureTier.PREMIUM, 5000.0);
        SAVINGS_MIN_BALANCE_MAP.put(TenureTier.PRIVILEGE, 2500.0);

        SAVINGS_INTEREST_RATE_MAP.put(TenureTier.NEW, 2.70);
        SAVINGS_INTEREST_RATE_MAP.put(TenureTier.STANDARD, 3.00);
        SAVINGS_INTEREST_RATE_MAP.put(TenureTier.PREMIUM, 3.50);
        SAVINGS_INTEREST_RATE_MAP.put(TenureTier.PRIVILEGE, 4.00);

        FD_INTEREST_RATE_MAP.put(0, 4.0);
        FD_INTEREST_RATE_MAP.put(3, 4.5);
        FD_INTEREST_RATE_MAP.put(6, 5.5);
        FD_INTEREST_RATE_MAP.put(12, 6.5);
    }

    public static TenureTier getTenureTier(int tenureYears) {
        if (tenureYears >= 5) {
            return TenureTier.PRIVILEGE;
        } else if (tenureYears >= 3) {
            return TenureTier.PREMIUM;
        } else if (tenureYears >= 1) {
            return TenureTier.STANDARD;
        } else {
            return TenureTier.NEW;
        }
    }

    public static double getSavingsMinBalance(int tenureYears) {
        return SAVINGS_MIN_BALANCE_MAP.get(getTenureTier(tenureYears));
    }

    public static double getSavingsMinBalance(TenureTier tier) {
        return SAVINGS_MIN_BALANCE_MAP.getOrDefault(tier, 10000.0);
    }

    public static double getSavingsMinBalance(String tierName) {
        try {
            return getSavingsMinBalance(TenureTier.valueOf(tierName.toUpperCase()));
        } catch (Exception e) {
            return 10000.0;
        }
    }

    public static double getSavingsInterestRate(int tenureYears) {
        return SAVINGS_INTEREST_RATE_MAP.get(getTenureTier(tenureYears));
    }

    public static double getSavingsInterestRate(TenureTier tier) {
        return SAVINGS_INTEREST_RATE_MAP.getOrDefault(tier, 2.70);
    }

    public static double getSavingsInterestRate(String tierName) {
        try {
            return getSavingsInterestRate(TenureTier.valueOf(tierName.toUpperCase()));
        } catch (Exception e) {
            return 2.70;
        }
    }

    public static double getCurrentOverdraftLimit(double monthlyTurnover) {
        return Math.max(25000.0, monthlyTurnover * 2.5);
    }

    public static double getFDInterestRate(int months) {
        if (months >= 12) {
            return 6.5;
        } else if (months >= 6) {
            return 5.5;
        } else if (months >= 3) {
            return 4.5;
        } else {
            return 4.0;
        }
    }

    public static Map<TenureTier, Double> getSavingsMinBalanceMap() {
        return Collections.unmodifiableMap(SAVINGS_MIN_BALANCE_MAP);
    }

    public static Map<TenureTier, Double> getSavingsInterestRateMap() {
        return Collections.unmodifiableMap(SAVINGS_INTEREST_RATE_MAP);
    }

    public static Map<Integer, Double> getFDInterestRateMap() {
        return Collections.unmodifiableMap(FD_INTEREST_RATE_MAP);
    }
}
