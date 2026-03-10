import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class RealTimeAnalyticsDashboardforWebsiteTraffic {
    public record Event(String url, String userId, String source) {}

    private final ConcurrentHashMap<String, LongAdder> pageViews = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> uniqueUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> sourceCounts = new ConcurrentHashMap<>();
    private volatile String cachedDashboard = "No data yet";

    public RealTimeAnalyticsDashboardforWebsiteTraffic() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> cachedDashboard = buildDashboard(), 0, 5, TimeUnit.SECONDS);
    }

    public void processEvent(Event e) {
        pageViews.computeIfAbsent(e.url(), k -> new LongAdder()).increment();
        uniqueUsers.computeIfAbsent(e.url(), k -> ConcurrentHashMap.newKeySet()).add(e.userId());
        sourceCounts.computeIfAbsent(normalizeSource(e.source()), k -> new LongAdder()).increment();
    }

    public String getDashboard() {
        return cachedDashboard;
    }

    private String buildDashboard() {
        var topPages = pageViews.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .limit(10)
                .collect(Collectors.toList());

        long totalSource = sourceCounts.values().stream().mapToLong(LongAdder::sum).sum();
        StringBuilder sb = new StringBuilder("Top Pages:\n");

        int rank = 1;
        for (var e : topPages) {
            String url = e.getKey();
            long views = e.getValue().sum();
            int unique = uniqueUsers.getOrDefault(url, Set.of()).size();
            sb.append(rank++).append(". ").append(url)
              .append(" - ").append(views).append(" views (")
              .append(unique).append(" unique)\n");
        }

        sb.append("\nTraffic Sources:\n");
        sourceCounts.forEach((src, cnt) -> {
            double pct = totalSource == 0 ? 0 : (cnt.sum() * 100.0 / totalSource);
            sb.append(src).append(": ").append(String.format("%.1f", pct)).append("%\n");
        });

        return sb.toString();
    }

    private String normalizeSource(String s) {
        if (s == null) return "other";
        s = s.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "google", "facebook", "direct", "twitter", "linkedin" -> s;
            default -> "other";
        };
    }
}
