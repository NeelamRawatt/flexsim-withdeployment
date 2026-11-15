package com.example.flexsim_worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks that the worker STARTS: all parts are found and connected.
 * Switched off here: the start-up login (no auth-service), the FlexSim file check (no FlexSim
 * on the build machine) and the Kafka listener (no Kafka). All values are test-only.
 */
@SpringBootTest(properties = {
        "app.worker.startup-login-check=false",
        "app.worker.password=test-only-password-not-a-real-secret-0123456789",
        "app.flexsim.startup-check=false",
        "app.flexsim.model-path=test-only-model.fsm",
        "spring.kafka.listener.auto-startup=false"
})
class FlexsimWorkerApplicationTests {

    @Test
    void contextLoads() {
    }
}
