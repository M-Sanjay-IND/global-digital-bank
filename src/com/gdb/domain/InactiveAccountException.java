package com.gdb.domain;
public class InactiveAccountException extends AccountException {
    public InactiveAccountException(String message) {
        super(message);
    }
}
