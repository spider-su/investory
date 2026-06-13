package com.example.demo.data;

public enum CashOperationType {
    SEC_FEE,
    SUBACCOUNT_TRANSFER,
    STOCK_PURCHASE,
    STOCK_SALE,
    CLOSE_TRADE,
    DIVIDEND,
    FREE_FUNDS_INTEREST,
    FREE_FUNDS_INTEREST_TAX,
    COMMISSION,
    TRANSFER,
    WITHDRAWAL,
    DEPOSIT,
    UNKNOWN;

    public static CashOperationType fromString(String value) {
        switch (value.toLowerCase()) {
            case "sec fee": return SEC_FEE;
            case "subaccount transfer": return SUBACCOUNT_TRANSFER;
            case "stock purchase": return STOCK_PURCHASE;
            case "stock sale": return STOCK_SALE;
            case "close trade": return CLOSE_TRADE;
            case "divident": return DIVIDEND;
            case "free-funds interest": return FREE_FUNDS_INTEREST;
            case "free-funds interest tax": return FREE_FUNDS_INTEREST_TAX;
            case "commission": return COMMISSION;
            case "transfer": return TRANSFER;
            case "withdrawal": return WITHDRAWAL;
            case "deposit": return DEPOSIT;
            default: return UNKNOWN;
        }
    }
}
