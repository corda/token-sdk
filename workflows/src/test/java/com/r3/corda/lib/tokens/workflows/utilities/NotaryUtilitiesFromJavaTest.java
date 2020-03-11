package com.r3.corda.lib.tokens.workflows.utilities;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.JITMockNetworkTests;
import com.r3.corda.lib.tokens.workflows.MockNetworkTest;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        Party javaWrappedPreferredNotary = new NotaryUtilities(a.getServices()).getPreferred();
        assert(kotlinPreferredNotary.equals(javaWrappedPreferredNotary));

        Party kotlinFirstNotary = NotaryUtilitiesKt.getPreferredNotary(a.getServices(), NotaryUtilitiesKt.firstNotary());
        Party javaWrappedFirstNotary = new NotaryUtilities(a.getServices()).getPreferred();
        assert(kotlinFirstNotary.equals(javaWrappedFirstNotary));

        Party kotlinRandomNotary = NotaryUtilitiesKt.getPreferredNotary(a.getServices(), NotaryUtilitiesKt.firstNotary());
        Party javaWrappedRandomNotary = new NotaryUtilities(a.getServices()).getPreferred();
        assert(kotlinRandomNotary.equals(javaWrappedRandomNotary));
    }
}

