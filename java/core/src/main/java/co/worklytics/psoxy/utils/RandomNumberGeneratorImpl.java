package co.worklytics.psoxy.utils;

import java.util.Random;

public class RandomNumberGeneratorImpl implements RandomNumberGenerator {

    private final java.util.Random random = new Random();

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
