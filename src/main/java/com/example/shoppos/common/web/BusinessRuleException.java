package com.example.shoppos.common.web;

/** A business rule was violated (e.g. adjustment would result in negative stock). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
