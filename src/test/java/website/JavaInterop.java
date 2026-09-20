package website;

import com.pambrose.jev4k.JevClient;
import com.pambrose.jev4k.JevResult;
import kotlin.Unit;

/*
 * The Java example for the documentation site. Like the Kotlin examples in src/test/kotlin/website, it is
 * compiled with the test sources so it can't drift from the API, but it is not a test and nothing runs it.
 * It also pins the Java-visible surface: if an @JvmOverloads or @JvmSynthetic annotation is lost, this stops
 * compiling.
 */
public final class JavaInterop {
    private JavaInterop() {
    }

    public static void main(String[] args) {
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
        jev.close();
    }
}
