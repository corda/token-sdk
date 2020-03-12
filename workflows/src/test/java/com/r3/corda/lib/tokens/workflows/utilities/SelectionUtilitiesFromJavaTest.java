package com.r3.corda.lib.tokens.workflows.utilities;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.contracts.utilities.TokenUtilitiesKt;
import com.r3.corda.lib.tokens.selection.SelectionUtilities;
import com.r3.corda.lib.tokens.selection.SelectionUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.services.Vault;
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

        a.startFlow(
            new IssueTokens(Collections.singletonList(
                TokenUtilitiesKt.heldBy(
                        AmountUtilitiesKt.issuedBy(
                                AmountUtilitiesKt.amount(10, testTokenType),
                                a.getInfo().getLegalIdentities().get(0)
                        ),
                        a.getInfo().getLegalIdentities().get(0)
                )
        )));

        Vault.Page<FungibleToken> tokenAmountResultsClassLess = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilitiesKt.tokenAmountCriteria(testTokenType));
        Vault.Page<FungibleToken> tokenAmountResultsClassWrapped = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilities.tokenAmountCriteria(testTokenType));
        assert(tokenAmountResultsClassLess.equals(tokenAmountResultsClassWrapped));

        Vault.Page<FungibleToken> tokenAmountWithHolderResultsClassLess = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilitiesKt.tokenAmountWithHolderCriteria(testTokenType, a.getInfo().getLegalIdentities().get(0)));
        Vault.Page<FungibleToken> tokenAmountWithHolderResultsClassWrapped = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilities.tokenAmountWithHolderCriteria(testTokenType, a.getInfo().getLegalIdentities().get(0)));
        assert(tokenAmountWithHolderResultsClassLess.equals(tokenAmountWithHolderResultsClassWrapped));

        Vault.Page<FungibleToken> tokenAmountWithIssuerResultsClassLess = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilitiesKt.tokenAmountWithIssuerCriteria(testTokenType, a.getInfo().getLegalIdentities().get(0)));
        Vault.Page<FungibleToken> tokenAmountWithIssuerResultsClassWrapped = a.getServices().getVaultService().queryBy(FungibleToken.class, SelectionUtilities.tokenAmountWithIssuerCriteria(testTokenType, a.getInfo().getLegalIdentities().get(0)));
        assert(tokenAmountWithIssuerResultsClassLess.equals(tokenAmountWithIssuerResultsClassWrapped));
    }
}
