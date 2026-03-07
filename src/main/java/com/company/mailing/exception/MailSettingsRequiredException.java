package com.company.mailing.exception;

public class MailSettingsRequiredException extends RuntimeException {

    public MailSettingsRequiredException(String message) {
        super(message);
    }
}
