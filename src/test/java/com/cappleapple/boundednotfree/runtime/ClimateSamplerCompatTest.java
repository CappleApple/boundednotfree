package com.cappleapple.boundednotfree.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ClimateSamplerCompatTest {
    @Test
    void copiesSeedThroughOptionalHookContract() throws ReflectiveOperationException {
        TestSampler source = new TestSampler();
        TestSampler target = new TestSampler();
        source.fabric_setSeed(0x5EEDC0DEL);

        ClimateSamplerCompat.copySeed(source, target, TestSamplerHooks.class);

        assertEquals(0x5EEDC0DEL, target.fabric_getSeed());
    }

    @Test
    void rejectsSamplerThatDoesNotImplementOptionalHooks() {
        TestSampler source = new TestSampler();

        assertThrows(IllegalArgumentException.class,
                () -> ClimateSamplerCompat.copySeed(source, new Object(), TestSamplerHooks.class));
    }

    public interface TestSamplerHooks {
        long fabric_getSeed();

        void fabric_setSeed(long seed);
    }

    private static final class TestSampler implements TestSamplerHooks {
        private long seed;

        @Override
        public long fabric_getSeed() {
            return seed;
        }

        @Override
        public void fabric_setSeed(long seed) {
            this.seed = seed;
        }
    }
}
