package com.example.shoppos.common.web;

/** A unique-constraint violation on a form field (SKU, barcode, name...). */
public class DuplicateValueException extends RuntimeException {

    private final String field;

    public DuplicateValueException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
