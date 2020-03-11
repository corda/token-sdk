package com.r3.corda.lib.tokens.workflows.utilities;

import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkNotarySpec;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;

public class NotaryUtilitiesFromJavaTest {

    private MockNetwork mockNetwork;
    private StartedMockNode a;

    @Before
    public void setup() {
        MockNetworkParameters mockNetworkParameters = new MockNetworkParameters().withCordappsForAllNodes(
                Arrays.asList(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
                )
        ).withNotarySpecs(Arrays.asList(new MockNetworkNotarySpec(new CordaX500Name("Notary", "London", "GB"))));
        mockNetwork = new MockNetwork(mockNetworkParameters);

        a = mockNetwork.createNode(new MockNodeParameters());
    }

    @After
    public void tearDown() {
        mockNetwork.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    /**
     * Sanity check for notary utilities wrapped in class for access from Java.
     */
    @Test
    public void javaWrappedNotarySelectionIsIdenticalToKotlinCompanionObject() throws Exception {
        Party kotlinPreferredNotary = NotaryUtilitiesKt.getPreferredNotary(a.getServices(), NotaryUtilitiesKt.firstNotary());
        Party javaWrappedPreferredNotary = NotaryUtilities.getPreferred(a.getServices());
        assert(kotlinPreferredNotary.equals(javaWrappedPreferredNotary));

        Party kotlinFirstNotary = NotaryUtilitiesKt.firstNotary().invoke(a.getServices());
        Party javaWrappedFirstNotary = NotaryUtilities.getFirst(a.getServices());
        assert(kotlinFirstNotary.equals(javaWrappedFirstNotary));

        Party kotlinRandomNotary = NotaryUtilitiesKt.randomNotary().invoke(a.getServices());
        Party javaWrappedRandomNotary = NotaryUtilities.getPreferred(a.getServices());
        assert(kotlinRandomNotary.equals(javaWrappedRandomNotary));
    }

    /**
     * Sanity check for preferred notary backup selector.
     */
    @Test
    public void javaWrappedNotarySelectionWorksWithBackupSelection() throws Exception {
        Party preferredNotary = NotaryUtilities.getPreferred(a.getServices());
        Party preferredNotaryWithBackup = NotaryUtilities.getPreferred(a.getServices(), NotaryUtilitiesKt.firstNotary());
        assert(preferredNotary.equals(preferredNotaryWithBackup));
    }
}

