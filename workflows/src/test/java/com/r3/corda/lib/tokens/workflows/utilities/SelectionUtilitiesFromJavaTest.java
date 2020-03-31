package com.r3.corda.lib.tokens.workflows.utilities;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilities;
import com.r3.corda.lib.tokens.contracts.utilities.TokenUtilitiesKt;
import com.r3.corda.lib.tokens.selection.SelectionUtilities;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
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
import java.util.Collections;

public class SelectionUtilitiesFromJavaTest {
    private MockNetwork mockNetwork;
    private StartedMockNode a;
    private StartedMockNode b;


    @Before
    public void setup() {
        MockNetworkParameters mockNetworkParameters = new MockNetworkParameters().withCordappsForAllNodes(
                Arrays.asList(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                )
        ).withNotarySpecs(Arrays.asList(new MockNetworkNotarySpec(new CordaX500Name("Notary", "London", "GB"))));
        mockNetwork = new MockNetwork(mockNetworkParameters);

        a = mockNetwork.createNode(new MockNodeParameters());
        b = mockNetwork.createNode(new MockNodeParameters());
    }

    @After
    public void tearDown() {
        mockNetwork.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    /**
     * Sanity check for easy access of selection utilities from Java code.
     */
    @Test
    public void javaWrappedSelectionCriteriaIsIdenticalToKotlinCompanionObject() throws Exception {

        TokenType testTokenType = new TokenType("TEST", 1);
        CordaFuture<SignedTransaction> futureWithTokens = a.startFlow(
                new IssueTokens(Collections.singletonList(
                        TokenUtilitiesKt.heldBy(
                                AmountUtilities.issuedBy(
                                        AmountUtilities.amount(10, testTokenType),
                                        a.getInfo().getLegalIdentities().get(0)
                                ),
                                a.getInfo().getLegalIdentities().get(0)
                        )
                )));
        mockNetwork.runNetwork();

        Vault.Page<FungibleToken> tokenAmountResultsClassLess = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilities.tokenAmountCriteria(testTokenType));
        assert (tokenAmountResultsClassLess.getStates().size() == 1);

        Vault.Page<FungibleToken> tokenAmountWithHolderResultsClassLess = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilities.tokenAmountWithHolderCriteria(testTokenType, a.getInfo().getLegalIdentities().get(0)));
        assert (tokenAmountWithHolderResultsClassLess.getStates().size() == 1);

        Vault.Page<FungibleToken> tokenAmountWithIssuerResultsClassLess = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilities.tokenAmountWithIssuerCriteria(testTokenType, a.getInfo().getLegalIdentities().get(0)));
        assert (tokenAmountWithIssuerResultsClassLess.getStates().size() == 1);
    }
}
