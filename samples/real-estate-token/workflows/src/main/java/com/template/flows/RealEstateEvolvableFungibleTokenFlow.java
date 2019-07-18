package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableToken;
import com.r3.corda.lib.tokens.workflows.flows.rpc.*;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
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

/**
 * Create,Issue,Move,Redeem token flows for a house asset on ledger
 */
public class RealEstateEvolvableFungibleTokenFlow {

    private RealEstateEvolvableFungibleTokenFlow() {
        //Instantiation not allowed
    }

    /**
     * Create Fungible Token for a house asset on ledger
     */
    @StartableByRPC
    public static class CreateEvolvableFungibleTokenFlow extends FlowLogic<SignedTransaction> {

        // valuation property of a house can change hence we are considering house as a evolvable asset
        private final BigDecimal valuation;

        public CreateEvolvableFungibleTokenFlow(BigDecimal valuation) {
            this.valuation = valuation;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            //get notary
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            //create house asset on ledger specifying maintainers and uuid
            RealEstateEvolvableTokenType evolvableTokenType = new RealEstateEvolvableTokenType(valuation, getOurIdentity(),
                    new UniqueIdentifier(), 0);
            //create on ledger state object using transaction state which is a wrapper around house state
            TransactionState transactionState = new TransactionState(evolvableTokenType, notary);
            //call built in CreateEvolvableToken flow to create house asset on ledger
            return (SignedTransaction) subFlow(new CreateEvolvableToken(transactionState));
        }
    }

    /**
     *  Issue Fungible Token against an evolvable house asset on ledger
     */
    @StartableByRPC
    public static class IssueEvolvableFungibleTokenFlow extends FlowLogic<SignedTransaction>{
        private final String evolvableTokenId;
        private final int quantity;

        public IssueEvolvableFungibleTokenFlow(String evolvableTokenId, int quantity) {
            this.evolvableTokenId = evolvableTokenId;
            this.quantity = quantity;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            //get uuid from input evolvableTokenId
            UUID uuid = UUID.fromString(evolvableTokenId);

            //create criteria to get all unconsumed house states on ledger with uuid as input evolvableTokenId
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(uuid), null,
                    Vault.StateStatus.UNCONSUMED, null);
            StateAndRef<RealEstateEvolvableTokenType> stateAndRef = getServiceHub().getVaultService().
                    queryBy(RealEstateEvolvableTokenType.class, queryCriteria).getStates().get(0);

            //get the RealEstateEvolvableTokenType object
            RealEstateEvolvableTokenType evolvableTokenType = stateAndRef.getState().getData();
            LinearPointer linearPointer = new LinearPointer(evolvableTokenType.getLinearId(), RealEstateEvolvableTokenType.class);
            TokenPointer token = new TokenPointer(linearPointer, evolvableTokenType.getFractionDigits());

            //specify how much amount to issue to self
            IssuedTokenType issuedTokenType = new IssuedTokenType(getOurIdentity(), token);
            Amount<IssuedTokenType> amount = new Amount(quantity, issuedTokenType);

            //use built in flow for issuing tokens on ledger
            subFlow(new IssueTokens(amount));
            return null;
        }
    }

    /**
     *  Move created fungible tokens to other party
     */
    @StartableByRPC
    public static class MoveEvolvableFungibleTokenFlow extends FlowLogic<SignedTransaction>{
        private final String evolvableTokenId;
        private final Party recipient;
        private final int quantity;


        public MoveEvolvableFungibleTokenFlow(String evolvableTokenId, Party recipient, int quantity) {
            this.evolvableTokenId = evolvableTokenId;
            this.recipient = recipient;
            this.quantity = quantity;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            //get uuid from input evolvableTokenId
            UUID uuid = UUID.fromString(evolvableTokenId);

            //create criteria to get all unconsumed house states on ledger with uuid as input evolvableTokenId
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(uuid), null,
                    Vault.StateStatus.UNCONSUMED, null);
            StateAndRef<RealEstateEvolvableTokenType> stateAndRef = getServiceHub().getVaultService().
                    queryBy(RealEstateEvolvableTokenType.class, queryCriteria).getStates().get(0);

            //get the RealEstateEvolvableTokenType object
            RealEstateEvolvableTokenType evolvableTokenType = stateAndRef.getState().getData();
            LinearPointer linearPointer = new LinearPointer(evolvableTokenType.getLinearId(), RealEstateEvolvableTokenType.class);
            TokenPointer token = new TokenPointer(linearPointer, evolvableTokenType.getFractionDigits());

            //specify how much amount to transfer to which recipient
            Amount<TokenPointer> amount = new Amount(quantity, token);
            PartyAndAmount partyAndAmount = new PartyAndAmount(recipient, amount);

            //use built in flow to move fungible tokens to recipient
            subFlow(new MoveFungibleTokens(partyAndAmount));
            return null;
        }
    }

    /**
     *  Holder Redeems fungible token issued by issuer
     */
    @StartableByRPC
    public static class RedeemHouseFungibleTokenFlow extends FlowLogic<SignedTransaction> {

        private final String evolvableTokenId;
        private final Party issuer;
        private final int quantity;

        public RedeemHouseFungibleTokenFlow(String evolvableTokenId, Party issuer, int quantity) {
            this.evolvableTokenId = evolvableTokenId;
            this.issuer = issuer;
            this.quantity = quantity;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            //get uuid from input evolvableTokenId
            UUID uuid = UUID.fromString(evolvableTokenId);

            //create criteria to get all unconsumed house states on ledger with uuid as input evolvableTokenId
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, ImmutableList.of(uuid), null,
                    Vault.StateStatus.UNCONSUMED, null);
            StateAndRef<RealEstateEvolvableTokenType> stateAndRef = getServiceHub().getVaultService().
                    queryBy(RealEstateEvolvableTokenType.class, queryCriteria).getStates().get(0);

            //get the RealEstateEvolvableTokenType object
            RealEstateEvolvableTokenType evolvableTokenType = stateAndRef.getState().getData();
            LinearPointer linearPointer = new LinearPointer(evolvableTokenType.getLinearId(), RealEstateEvolvableTokenType.class);
            TokenPointer token = new TokenPointer(linearPointer, evolvableTokenType.getFractionDigits());
            //specify how much amount quantity of tokens of type token parameter
            Amount<TokenPointer> amount = new Amount(quantity, token);

            //call built in redeem flow to redeem tokens with issuer
            subFlow(new RedeemFungibleTokens(amount, issuer));
            return null;
        }
    }
}

