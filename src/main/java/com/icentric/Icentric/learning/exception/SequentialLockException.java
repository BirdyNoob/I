package com.icentric.Icentric.learning.exception;

import com.icentric.Icentric.common.exception.DomainException;

/**
 * Thrown when a learner attempts to submit progress on a lesson before
 * completing all preceding lessons in the same module (sequential locking).
 * Maps to HTTP 403 Forbidden via the global exception handler.
 */
public class SequentialLockException extends DomainException {

    public SequentialLockException(String blockedByLessonTitle) {
        super("You must complete the previous lesson \"" + blockedByLessonTitle
                + "\" before unlocking this one.");
    }
}
