package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableToken;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import com.template.states.RealEstateEvolvableTokenType;
import net.corda.core.contracts.*;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;

import java.math.BigDecimal;
import java.util.UUID;

public class RealEstateEvolvableTokenFlow {

    private RealEstateEvolvableTokenFlow() {
        //Instantiation not allowed
    }

    /**
     * Create NonFungible Token in ledger
     */
    @StartableByRPC
    public static class CreateEvolvableTokenFlow extends FlowLogic<SignedTransaction> {

        private final BigDecimal valuation;

        public CreateEvolvableTokenFlow(BigDecimal valuation) {
            this.valuation = valuation;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            RealEstateEvolvableTokenType evolvableTokenType = new RealEstateEvolvableTokenType(valuation, getOurIdentity(),
                    new UniqueIdentifier(), 0);
            TransactionState transactionState = new TransactionState(evolvableTokenType, notary);
            return (SignedTransaction) subFlow(new CreateEvolvableToken(transactionState));
        }
    }

    /**
     *  Issue Non Fungible Token
     */
    @StartableByRPC
    public static class IssueEvolvableTokenFlow extends FlowLogic<SignedTransaction>{
        private final String evolvableTokenId;
        private final Party recipient;

        public IssueEvolvableTokenFlow(String evolvableTokenId, Party recipient) {
            this.evolvableTokenId = evolvableTokenId;
            this.recipient = recipient;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            UUID uuid = UUID.fromString(evolvableTokenId);
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(uuid), null,
                    Vault.StateStatus.UNCONSUMED, null);
            StateAndRef<RealEstateEvolvableTokenType> stateAndRef = getServiceHub().getVaultService().
                    queryBy(RealEstateEvolvableTokenType.class, queryCriteria).getStates().get(0);
            RealEstateEvolvableTokenType evolvableTokenType = stateAndRef.getState().getData();
            LinearPointer linearPointer = new LinearPointer(evolvableTokenType.getLinearId(), RealEstateEvolvableTokenType.class);
            TokenPointer token = new TokenPointer(linearPointer, evolvableTokenType.getFractionDigits());
            return (SignedTransaction) subFlow(new IssueTokens(token, this.getOurIdentity(), recipient));
        }
    }

    /**
     *  Move created non fungible token to other party
     */
    @StartableByRPC
    public static class MoveEvolvableTokenFlow extends FlowLogic<SignedTransaction>{
        private final String evolvableTokenId;
        private final Party recipient;


        public MoveEvolvableTokenFlow(String evolvableTokenId, Party recipient) {
            this.evolvableTokenId = evolvableTokenId;
            this.recipient = recipient;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            UUID uuid = UUID.fromString(evolvableTokenId);
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(uuid), null,
                    Vault.StateStatus.UNCONSUMED, null);
            StateAndRef<RealEstateEvolvableTokenType> stateAndRef = getServiceHub().getVaultService().
                    queryBy(RealEstateEvolvableTokenType.class, queryCriteria).getStates().get(0);
            RealEstateEvolvableTokenType evolvableTokenType = stateAndRef.getState().getData();
            LinearPointer linearPointer = new LinearPointer(evolvableTokenType.getLinearId(), RealEstateEvolvableTokenType.class);
            TokenPointer token = new TokenPointer(linearPointer, evolvableTokenType.getFractionDigits());
            PartyAndToken partyAndToken = new PartyAndToken(recipient, token);
            return (SignedTransaction) subFlow(new MoveNonFungibleTokens(partyAndToken));
        }
    }

    /**
     *  Holder Redeems non fungible token issued by issuer
     */
    @StartableByRPC
    public static class RedeemHouseToken extends FlowLogic<SignedTransaction> {

        private final String evolvableTokenId;
        private final Party issuer;

        public RedeemHouseToken(String evolvableTokenId, Party issuer) {
            this.evolvableTokenId = evolvableTokenId;
            this.issuer = issuer;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            UUID uuid = UUID.fromString(evolvableTokenId);
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(uuid), null,
                    Vault.StateStatus.UNCONSUMED, null);
            StateAndRef<RealEstateEvolvableTokenType> stateAndRef = getServiceHub().getVaultService().
                    queryBy(RealEstateEvolvableTokenType.class, queryCriteria).getStates().get(0);
            RealEstateEvolvableTokenType evolvableTokenType = stateAndRef.getState().getData();
            LinearPointer linearPointer = new LinearPointer(evolvableTokenType.getLinearId(), RealEstateEvolvableTokenType.class);
            TokenPointer token = new TokenPointer(linearPointer, evolvableTokenType.getFractionDigits());
            return (SignedTransaction) subFlow(new RedeemNonFungibleTokens(token, issuer));
        }
    }
}

