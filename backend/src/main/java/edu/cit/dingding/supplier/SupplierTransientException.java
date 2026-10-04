package edu.cit.dingding.supplier;

class SupplierTransientException extends RuntimeException {
    SupplierTransientException(String message) { super(message); }
    SupplierTransientException(String message, Throwable cause) { super(message, cause); }
}
