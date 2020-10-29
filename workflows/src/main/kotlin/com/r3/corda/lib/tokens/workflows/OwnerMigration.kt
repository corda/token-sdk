package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.database.core.PostgresDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import liquibase.statement.core.UpdateStatement
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.readFully
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import java.sql.ResultSet


class OwnerMigration : CustomSqlChange {

	companion object {
		private val logger = contextLogger()
	}

	private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
		override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
			return true
		}

		override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
		override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
	}

	val serializationFactory = SerializationFactoryImpl().apply {
		registerScheme(AMQPInspectorSerializationScheme)
	}
	val context = AMQP_STORAGE_CONTEXT.withLenientCarpenter()


	override fun validate(database: Database?): ValidationErrors? {
		return null
	}

	override fun getConfirmationMessage(): String? {
		return null
	}

	override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
	}

	override fun setUp() {
	}


	override fun generateStatements(database: Database?): Array<SqlStatement> {

		if (database == null) {
			throw IllegalStateException("Cannot migrate tokens without owner as Liquibase failed to provide a suitable connection")
		}
		val selectStatement = """
            SELECT output_index, transaction_id, transaction_value 
            FROM fungible_token, node_transactions 
            WHERE holding_key IS NULL and node_transactions.tx_id = fungible_token.transaction_id
        """.trimIndent()
		val connection = (database.connection as JdbcConnection).wrappedConnection
		val preparedStatement = connection.prepareStatement(selectStatement)
		val resultSet = preparedStatement.executeQuery()
		val listOfUpdates = mutableListOf<Pair<StateRef, String>>()
		resultSet.use { rs ->
			while (rs.next()) {
				val outputIdx = rs.getInt(1)
				val txId = rs.getString(2)
				val txBytes = getBytes(database, resultSet)
				val signedTx: SignedTransaction = txBytes.deserialize(serializationFactory, context)
				val fungibleTokensOutRefs = signedTx.coreTransaction.outRefsOfType(FungibleToken::class.java)
				val tokenMatchingRef = fungibleTokensOutRefs.singleOrNull { it.ref.index == outputIdx }
				tokenMatchingRef?.state?.data?.holder?.owningKey?.toStringShort()?.let { ownerHash ->
					listOfUpdates.add(StateRef(SecureHash.parse(txId), outputIdx) to ownerHash)
				}
			}
		}

		return listOfUpdates.map { (stateRef, holdingKeyHash) ->
			UpdateStatement(connection.catalog, connection.schema, "fungible_token")
				.setWhereClause("output_index=? AND transaction_id=?")
				.addNewColumnValue("holding_key", holdingKeyHash)
				.addWhereParameters(stateRef.index, stateRef.txhash.toString())
		}.toTypedArray()

	}


}

fun getBytes(database: Database, resultSet: ResultSet): ByteArray {

	return if (database is PostgresDatabase) {
		val lom: org.postgresql.largeobject.LargeObjectManager =
			(database.connection as JdbcConnection).underlyingConnection.unwrap(org.postgresql.PGConnection::class.java).largeObjectAPI

		val oid = resultSet.getLong("transaction_value")
		val loadedbject: org.postgresql.largeobject.LargeObject = lom.open(oid)
		loadedbject.use {
			loadedbject.inputStream.readFully()
		}
	} else {
		resultSet.getBytes("transaction_value")
	}

}

