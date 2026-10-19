package com.example.experiment_service.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A small helper used by many tests: "this code must fail with this HTTP status". */
public final class StatusAssert {

    private StatusAssert() {
    }

    public static void assertStatus(ThrowingCallable code, HttpStatus expected) {
        Throwable thrown = catchThrowable(code);
        assertThat(thrown)
                .as("expected a ResponseStatusException with status " + expected.value())
                .isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode().value()).isEqualTo(expected.value());
    }
}