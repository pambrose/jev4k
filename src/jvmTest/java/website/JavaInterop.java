package website;

import com.pambrose.jev4k.BlockingJev;
import com.pambrose.jev4k.BlockingJevKt;
import com.pambrose.jev4k.BuildersKt;
import com.pambrose.jev4k.JevApi;
import com.pambrose.jev4k.JevApiException;
import com.pambrose.jev4k.JevCallOptions;
import com.pambrose.jev4k.JevCallOptionsKt;
import com.pambrose.jev4k.JevClient;
import com.pambrose.jev4k.JevDefaults;
import com.pambrose.jev4k.JevRateLimitException;
import com.pambrose.jev4k.JevResult;
import com.pambrose.jev4k.ModelList;
import com.pambrose.jev4k.RetryPolicy;
import com.pambrose.jev4k.TestingKt;
import java.util.List;
import java.util.Map;
import kotlin.Unit;

/*
 * The Java example for the documentation site. Like the Kotlin examples in src/jvmTest/kotlin/website, it is
 * compiled with the test sources so it can't drift from the API, but it is not a test and nothing runs it.
 * It also pins part of the Java-visible surface: it stops compiling if an overload it calls disappears (the
 * two-argument QueryBuilder.noul, BlockingJev.query without a model, jevResult, jevApiException), if BlockingJev
 * loses its @Throws (javac then rejects the catch of InterruptedException), or if a millisecond member for a
 * Duration setting goes. The ABI dump in api/ guards everything else.
 */
public final class JavaInterop {
    private JavaInterop() {
    }

    public static void main(String[] args) throws InterruptedException {
        // --8<-- [start:basics]
        JevClient jev = new JevClient(builder -> {
            builder.setApiKey(System.getenv("TYPESAFE_API_KEY"));
            return Unit.INSTANCE;
        });

        JevResult r = jev.getBlocking().query("The payout failed again and I need this fixed today.", null, qb -> {
            qb.noul("urgent", "Does this message convey urgency?");
            return Unit.INSTANCE;
        });

        double urgency = r.noul("urgent").getNoul();
        // --8<-- [end:basics]

        System.out.println(urgency);
        System.out.println(settings().getBlocking().models().getRequestId());
        System.out.println(perCall(jev));
        jev.close();
    }

    static JevClient settings() {
        // --8<-- [start:settings]
        return new JevClient(builder -> {
            builder.setTimeoutMillis(2 * JevDefaults.TIMEOUT_MILLIS);
            builder.setRetry(new RetryPolicy().withMaxRetries(4).withInitialBackoffMillis(250));
            return Unit.INSTANCE;
        });
        // --8<-- [end:settings]
    }

    static double perCall(JevApi api) {
        // --8<-- [start:per-call]
        JevCallOptions fast = new JevCallOptions(options -> {
            options.setTimeoutMillis(2_000L);
            options.getHeaders().put("X-Trace-Id", "ticket-4711");
            return Unit.INSTANCE;
        });
        BlockingJev blocking = BlockingJevKt.blocking(JevCallOptionsKt.withOptions(api, fast));

        try {
            ModelList models = blocking.models();
            JevResult r = blocking.query("The payout failed again.", qb -> {
                qb.noul("urgent", "Does this message convey urgency?");
                return Unit.INSTANCE;
            });
            System.out.println(models.size() + " models, request " + models.getRequestId());
            return r.noul("urgent").getNoul();
        } catch (JevRateLimitException e) {
            Long waitMillis = e.getRetryAfterMillis();
            System.out.println("Rate limited; retry after " + waitMillis + " ms");
            return Double.NaN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Double.NaN;
        }
        // --8<-- [end:per-call]
    }

    // The testing helpers, as a Java test would call them.
    static void testing() {
        JevApiException limited = TestingKt.jevApiException(429, null, null, Map.of("retry-after-ms", List.of("2000")));
        JevResult canned = TestingKt.jevResult("{\"answers\":{}}", BuildersKt.questions(qb -> {
            qb.noul("urgent", "Does this message convey urgency?");
            return Unit.INSTANCE;
        }));
        System.out.println(((JevRateLimitException) limited).getRetryAfterMillis() + " " + canned.getModel());
    }
}
