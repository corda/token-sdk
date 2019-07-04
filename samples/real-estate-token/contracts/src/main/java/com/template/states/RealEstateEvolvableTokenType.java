package com.template.states;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.template.RealEstateEvolvableTokenTypeContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;

import java.math.BigDecimal;
import java.util.List;

@BelongsToContract(RealEstateEvolvableTokenTypeContract.class)
public class RealEstateEvolvableTokenType extends EvolvableTokenType {

    private final BigDecimal valuation;
    private final Party maintainer;
    private final UniqueIdentifier uniqueIdentifier;
    private final int fractionDigits;

    public RealEstateEvolvableTokenType(BigDecimal valuation, Party maintainer,
                                        UniqueIdentifier uniqueIdentifier, int fractionDigits) {
        this.valuation = valuation;
        this.maintainer = maintainer;
        this.uniqueIdentifier = uniqueIdentifier;
        this.fractionDigits = fractionDigits;
    }

    @Override
    public List<Party> getMaintainers() {
        return ImmutableList.of(maintainer);
    }

    @Override
    public int getFractionDigits() {
        return this.fractionDigits;
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return this.uniqueIdentifier;
    }
}
