package com.r3.corda.lib.tokens.workflows.utilities;

import com.r3.corda.lib.tokens.workflows.JITMockNetworkTests;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NotaryUtilitiesFromJavaTest extends JITMockNetworkTests {

    private MockNetwork mockNetwork;
    private StartedMockNode a;

    @Before
    public void setup() {
        a = node(new CordaX500Name("Alice", "NYC", "US"));
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    /**
     * Sanity check for notary utilities wrapped in class for access from Java.
     */
    @Test
    public void javaWrappedNotarySelectionIsIdenticalToKotlinCompanionObject() throws Exception {
        Party kotlinPreferredNotary = NotaryUtilitiesKt.getPreferredNotary(a.getServices(), NotaryUtilitiesKt.firstNotary());
        Party javaWrappedPreferredNotary = NotaryUtilities.getPreferredNotary(a.getServices());
        assert(kotlinPreferredNotary.equals(javaWrappedPreferredNotary));

        Party kotlinFirstNotary = NotaryUtilitiesKt.firstNotary().invoke(a.getServices());
        Party javaWrappedFirstNotary = NotaryUtilities.getPreferredNotary(a.getServices());
        assert(kotlinFirstNotary.equals(javaWrappedFirstNotary));

        Party kotlinRandomNotary = NotaryUtilitiesKt.randomNotary().invoke(a.getServices());
        Party javaWrappedRandomNotary = NotaryUtilities.randomNotary(a.getServices());
        assert(kotlinRandomNotary.equals(javaWrappedRandomNotary));
    }

    /**
     * Sanity check for preferred notary backup selector.
     */
    @Test
    public void javaWrappedNotarySelectionWorksWithBackupSelection() throws Exception {
        Party preferredNotary = NotaryUtilities.getPreferredNotary(a.getServices());
        Party preferredNotaryWithBackup = NotaryUtilities.getPreferredNotary(a.getServices(), NotaryUtilitiesKt.firstNotary());
        assert(preferredNotary.equals(preferredNotaryWithBackup));
    }
}

