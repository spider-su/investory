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
    WITHHOLDING_TAX,
    SWAP,
    ROLLOVER,
    CORRECTION,
    STAMP_DUTY,
    TRANSACTION_TAX,
    UNKNOWN;

    public static CashOperationType fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
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
            case "withdrawal":
            case "withdraw": return WITHDRAWAL;
            case "deposit":
            case "ike deposit": return DEPOSIT;
            case "withholding tax": return WITHHOLDING_TAX;
            case "swap": return SWAP;
            case "rollover": return ROLLOVER;
            case "correction": return CORRECTION;
            case "stamp duty": return STAMP_DUTY;
            case "tax iftt": return TRANSACTION_TAX;
            default: return UNKNOWN;
        }
    }
}
