package com.example.flexsim_worker.auth;

/**
 * Thrown when the worker could not log in.
 *
 * configProblem = true  -> OUR settings are wrong (wrong password, account is not WORKER).
 *                          Trying again will not help. A person must fix the settings.
 * configProblem = false -> auth-service is down or slow right now. Trying again later can help.
 */
public class WorkerLoginException extends RuntimeException {

    private final boolean configProblem;

    public WorkerLoginException(String message, boolean configProblem) {
        super(message);
        this.configProblem = configProblem;
    }

    public WorkerLoginException(String message, Throwable cause) {
        super(message, cause);
        this.configProblem = false;
    }

    public boolean isConfigProblem() {
        return configProblem;
    }
}
