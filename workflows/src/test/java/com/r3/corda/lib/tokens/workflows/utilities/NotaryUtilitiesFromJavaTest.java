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
        Party javaWrappedAllArgs = NotaryUtilities.getPreferredNotary(a.getServices(), NotaryUtilities.firstNotary());
        Party javaWrappedDefaultArgs = NotaryUtilities.getPreferredNotary(a.getServices());
        assert (javaWrappedAllArgs.equals(javaWrappedDefaultArgs));

        Party javaWrappedFirstNotary = NotaryUtilities.getPreferredNotary(a.getServices());
        assert (javaWrappedAllArgs.equals(javaWrappedFirstNotary));

        Party kotlinRandomNotary = NotaryUtilities.randomNotary().invoke(a.getServices());
        assert (javaWrappedAllArgs.equals(kotlinRandomNotary));
    }

    /**
     * Sanity check for preferred notary backup selector.
     */
    @Test
    public void javaWrappedNotarySelectionWorksWithBackupSelection() throws Exception {
        Party preferredNotaryWithBackup = NotaryUtilities.getPreferredNotary(a.getServices(), NotaryUtilities.firstNotary());
        Party preferredNotary = NotaryUtilities.getPreferredNotary(a.getServices());
        assert (preferredNotary.equals(preferredNotaryWithBackup));
    }
}

