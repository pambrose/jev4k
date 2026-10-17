package website;

import com.pambrose.jev4k.BlockingJev;
import com.pambrose.jev4k.BlockingJevKt;
import com.pambrose.jev4k.BoundingBox;
import com.pambrose.jev4k.BuildersKt;
import com.pambrose.jev4k.JevApi;
import com.pambrose.jev4k.JevApiException;
import com.pambrose.jev4k.JevCallOptions;
import com.pambrose.jev4k.JevCallOptionsKt;
import com.pambrose.jev4k.JevClient;
import com.pambrose.jev4k.JevDefaults;
import com.pambrose.jev4k.JevImage;
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
 * Duration setting goes, or if the image calls Java needs do (JevImage.of, the vararg QueryBuilder.attach, box). The
 * ABI dump in api/ guards everything else.
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

    // --8<-- [start:images]
    static BoundingBox findMug(BlockingJev blocking, byte[] photoBytes) throws InterruptedException {
        JevImage photo = JevImage.of(photoBytes);
        // Box needs Jev 1.14, so name a model that has it.
        JevResult r = blocking.query("The photo: " + photo.getReference(), "jev-preview", qb -> {
            qb.attach(photo);
            qb.noul("mug_visible", "Is there a coffee mug in the photo?");
            qb.box("mug", "the coffee mug in the photo");
            return Unit.INSTANCE;
        });
        // A Box always returns a box, so read it only when the Noul says the mug is there.
        return r.noul("mug_visible").isTrue(0.5) ? r.box("mug").getBox() : null;
    }
    // --8<-- [end:images]

    // A literal null model must keep resolving to ask(query, state, model): Java sees the image overloads under
    // another name, askWithImages, so they can't make this call ambiguous.
    static JevResult askWithDefaultModel(BlockingJev blocking) throws InterruptedException {
        return blocking.ask(SpamBroad.INSTANCE, "Win a free cruise!", null);
    }
}
