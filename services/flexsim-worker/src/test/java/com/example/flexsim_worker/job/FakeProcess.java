package com.example.flexsim_worker.job;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A FAKE FlexSim program for tests. The test decides when it "closes" (exitNow),
 * and we can check whether the worker closed it (destroyed).
 */
class FakeProcess extends Process {

    private final CompletableFuture<Process> exit = new CompletableFuture<>();
    volatile boolean destroyed;
    private volatile int exitCode = -1;

    void exitNow(int code) {
        exitCode = code;
        exit.complete(this);
    }

    @Override
    public CompletableFuture<Process> onExit() {
        return exit;
    }

    @Override
    public void destroy() {
        destroyed = true;
        exitNow(1);
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
        return exit.isDone();
    }

    @Override
    public int waitFor() {
        exit.join();
        return exitCode;
    }

    @Override
    public int exitValue() {
        if (!exit.isDone()) {
            throw new IllegalThreadStateException("still running");
        }
        return exitCode;
    }

    @Override
    public boolean isAlive() {
        return !exit.isDone();
    }

    @Override
    public OutputStream getOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }
}
