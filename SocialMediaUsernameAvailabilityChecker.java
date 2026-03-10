import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public class SocialMediaUsernameAvailabilityChecker {
    private final ConcurrentMap<String, Long> usernameToUserId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> attemptFrequency = new ConcurrentHashMap<>();

    private final AtomicReference<String> mostAttemptedUsername = new AtomicReference<>(null);
    private final AtomicLong mostAttemptedCount = new AtomicLong(0L);

    public SocialMediaUsernameAvailabilityChecker() {
    }

    public SocialMediaUsernameAvailabilityChecker(Map<String, Long> initialUsers) {
        if (initialUsers != null) {
            for (Map.Entry<String, Long> entry : initialUsers.entrySet()) {
                String username = normalize(entry.getKey());
                usernameToUserId.put(username, entry.getValue());
            }
        }
    }

    public boolean registerUsername(String username, long userId) {
        String normalized = normalize(username);
        return usernameToUserId.putIfAbsent(normalized, userId) == null;
    }

    public boolean checkAvailability(String username) {
        String normalized = normalize(username);
        recordAttempt(normalized);
        return !usernameToUserId.containsKey(normalized);
    }

    public List<String> suggestAlternatives(String username) {
        return suggestAlternatives(username, 3);
    }

    public List<String> suggestAlternatives(String username, int maxSuggestions) {
        String base = normalize(username);
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();

        addIfAvailable(suggestions, base + "1", maxSuggestions);
        addIfAvailable(suggestions, base + "2", maxSuggestions);

        if (base.contains("_")) {
            addIfAvailable(suggestions, base.replace('_', '.'), maxSuggestions);
            addIfAvailable(suggestions, base.replace("_", ""), maxSuggestions);
        }
        if (base.contains(".")) {
            addIfAvailable(suggestions, base.replace('.', '_'), maxSuggestions);
            addIfAvailable(suggestions, base.replace(".", ""), maxSuggestions);
        }

        for (int i = 3; suggestions.size() < maxSuggestions && i <= 9999; i++) {
            addIfAvailable(suggestions, base + i, maxSuggestions);
        }

        return new ArrayList<>(suggestions);
    }

    public AttemptInfo getMostAttempted() {
        String username = mostAttemptedUsername.get();
        long attempts = mostAttemptedCount.get();
        if (username == null) {
            return new AttemptInfo("", 0L);
        }
        return new AttemptInfo(username, attempts);
    }

    public long getAttemptCount(String username) {
        LongAdder count = attemptFrequency.get(normalize(username));
        return count == null ? 0L : count.sum();
    }

    private void addIfAvailable(LinkedHashSet<String> suggestions, String candidate, int maxSuggestions) {
        if (suggestions.size() >= maxSuggestions) {
            return;
        }
        if (!usernameToUserId.containsKey(candidate)) {
            suggestions.add(candidate);
        }
    }

    private void recordAttempt(String username) {
        LongAdder counter = attemptFrequency.computeIfAbsent(username, k -> new LongAdder());
        counter.increment();
        long newCount = counter.sum();

        while (true) {
            long currentMax = mostAttemptedCount.get();
            if (newCount <= currentMax) {
                return;
            }
            if (mostAttemptedCount.compareAndSet(currentMax, newCount)) {
                mostAttemptedUsername.set(username);
                return;
            }
        }
    }

    private String normalize(String username) {
        if (username == null) {
            throw new IllegalArgumentException("username cannot be null");
        }
        String normalized = username.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("username cannot be empty");
        }
        return normalized;
    }

    public static final class AttemptInfo {
        private final String username;
        private final long attempts;

        public AttemptInfo(String username, long attempts) {
            this.username = username;
            this.attempts = attempts;
        }

        public String getUsername() {
            return username;
        }

        public long getAttempts() {
            return attempts;
        }

        @Override
        public String toString() {
            return "\"" + username + "\" (" + attempts + " attempts)";
        }
    }

    public static void main(String[] args) {
        SocialMediaUsernameAvailabilityChecker checker = new SocialMediaUsernameAvailabilityChecker();
        checker.registerUsername("john_doe", 1001L);
        checker.registerUsername("admin", 1L);

        System.out.println("checkAvailability(\"john_doe\") -> " + checker.checkAvailability("john_doe"));
        System.out.println("checkAvailability(\"jane_smith\") -> " + checker.checkAvailability("jane_smith"));
        System.out.println("suggestAlternatives(\"john_doe\") -> " + checker.suggestAlternatives("john_doe"));

        for (int i = 0; i < 10543; i++) {
            checker.checkAvailability("admin");
        }
        System.out.println("getMostAttempted() -> " + checker.getMostAttempted());
    }
}
