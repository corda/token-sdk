package com.r3.corda.lib.tokens.selection

import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute

// TODO clean up the module structure of token-sdk, because these function and types (eg PartyAndAmount) should be separate from workflows
// Sorts a query by state ref ascending.
internal fun sortByStateRefAscending(): Sort {
    val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
    return Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
}