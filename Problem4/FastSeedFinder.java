public class FastSeedFinder {
    private static final long MULTIPLIER = 25214903917L;
    private static final long ADDEND = 11L;
    private static final long MASK = (1L << 48) - 1;
    private static final long SCRAMBLE = 0x5DEECE66DL;

    public static void main(String[] args) {
        // Your target sequence
        int[] targets = {23, 6, 9, 21, 18, 23, 2, 4, 6, 18, 17, 19, 0, 10, 3, 12, 0, 21, 11, 2, 19, 9, 19, 13, 5, 3, 22, 8, 2, 12, 11, 8, 8, 25, 24, 11, 17, 5, 16, 24, 13, 15, 1, 14, 11, 6, 4, 23, 0, 25, 3, 20, 19, 24, 11, 6, 25, 17, 1, 24, 17, 2, 24, 1, 25, 6, 3, 12, 9, 24, 3, 24, 11, 25, 7, 17, 1, 8, 12, 11};
        int range = 26;

        int threads = 8; 
        long SEARCH_LIMIT = 1L << 48;
        long chunkSize = SEARCH_LIMIT / threads;

        System.out.println("Starting optimized search...");
        long startMillis = System.currentTimeMillis();

        Thread[] threadArray = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            final long startSeed = i * chunkSize;
            final long endSeed = (i + 1) * chunkSize;

            threadArray[i] = new Thread(() -> {
                for (long s = startSeed; s < endSeed; s++) {
                    // 1. Initial Scramble (Mimics 'new Random(s)')
                    long state = (s ^ SCRAMBLE) & MASK;

                    state = (state * MULTIPLIER + ADDEND) & MASK;

                    // 2. Inline first check (Filter Early)
                    if (nextInternalInt(state, range) == targets[0]) {
                        
                        // 3. Only proceed if first matches
                        long nextState = state;
                        boolean match = true;
                        
                        for (int j = 1; j < targets.length; j++) {
                            // Update LCG state manually: (state * A + C) & MASK
                            nextState = (nextState * MULTIPLIER + ADDEND) & MASK;
                            if (nextInternalInt(nextState, range) != targets[j]) {
                                match = false;
                                break;
                            }
                        }

                        if (match) {
                            System.out.println("\n>>> FOUND SEED: " + s);
                        }
                    }
                    
                    // Progress reporting is omitted here for speed. 
                    // Incrementing a counter every loop slows down math-heavy code by ~30%.
                }
            });
            threadArray[i].start();
        }

        // Wait for all threads to complete
        for (Thread t : threadArray) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long endMillis = System.currentTimeMillis();
        long elapsedMillis = endMillis - startMillis;
        System.out.println("\nSearch completed in " + elapsedMillis + " ms (" + (elapsedMillis / 1000.0) + " seconds)");
    }

    /**
     * Replicates Random.nextInt(bound) logic using primitive math.
     * This avoids all object creation.
     */
    private static int nextInternalInt(long state, int bound) {
        // Random.nextInt uses the 31 most significant bits of the 48-bit state
        int bits = (int) (state >>> 17);
        
        // This is the common path for small bounds like 26
        // It simplifies the 'do-while' loop from Random.java 
        // which handles modulo bias.
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((bound * (long) bits) >> 31);
        }
        return bits % bound;
    }
}