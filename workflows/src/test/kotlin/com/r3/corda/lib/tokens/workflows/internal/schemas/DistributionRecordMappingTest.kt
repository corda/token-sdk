package com.r3.corda.lib.tokens.workflows.internal.schemas

import net.corda.node.services.persistence.AbstractPartyToX500NameAsStringConverter
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.JDBCType

/**
 * Corda Enterprise maps string-valued attributes as nationalised character data (`NVARCHAR`) on Oracle and SQL Server: its session
 * factories for those databases call `MetadataBuilder.enableGlobalNationalizedCharacterDataSupport(true)`. The `linear_id` column of
 * `distribution_record` is a `VARCHAR` (see `distribution-record-schema.changelog-init.xml`), so [DistributionRecord.linearId] must be
 * mapped as `VARCHAR` under that setting, on the Hibernate of every Corda version the SDK runs on.
 *
 * A JPA `AttributeConverter<UUID, String>` was mapped as `NVARCHAR`, and Corda Enterprise 4.12 - 4.14 nodes (Hibernate 5) on Oracle and
 * SQL Server failed their schema validation at start-up:
 * ```
 * Schema-validation: wrong column type encountered in column [linear_id] in table [distribution_record]; found [varchar2 (Types#VARCHAR)],
 * but expecting [nvarchar2(255) (Types#NVARCHAR)]
 * ```
 * Corda 4.15 (Hibernate 7) accepts an `NVARCHAR` mapping of a `VARCHAR` column in its validation, but binds `NVARCHAR` values, which
 * stops Oracle from using `dist_record_idx`.
 *
 * The test builds the mapping as the Enterprise Oracle session factory does, without a database, and checks the JDBC type of
 * `linear_id`, which is what the node's schema validation compares with the column. It runs on the Hibernate of the Corda version the
 * SDK is built against: Hibernate 5 by default, Hibernate 7 with `-PcordaReleaseVersion=4.15...`.
 */
class DistributionRecordMappingTest {
    @Test(timeout = 300_000)
    fun `linear_id is mapped as VARCHAR when strings are nationalised, as on Corda Enterprise's Oracle and SQL Server`() {
        val registry = StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", oracleDialect())
                // Build the mapping without connecting to a database: the Hibernate 5 and the Hibernate 7 setting.
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build()
        try {
            val metadata = MetadataSources(registry)
                    .addAnnotatedClass(DistributionRecord::class.java)
                    .metadataBuilder
                    // The node maps the party column with this converter.
                    .applyAttributeConverter(AbstractPartyToX500NameAsStringConverter({ null }, { null }))
                    // As Corda Enterprise's Oracle and SQL Server session factories do.
                    .enableGlobalNationalizedCharacterDataSupport(true)
                    .build()
            val linearId = metadata.getEntityBinding(DistributionRecord::class.java.name)!!.table.getColumn(Identifier.toIdentifier("linear_id"))

            assertEquals("JDBC type of distribution_record.linear_id, a VARCHAR column, with nationalised strings",
                    JDBCType.VARCHAR, JDBCType.valueOf(linearId.getSqlTypeCode(metadata)))
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

    /** The dialect Corda Enterprise 4.12 - 4.14 resolves on Oracle 12c and later (Hibernate 5), or Hibernate 7's (Corda 4.15). */
    private fun oracleDialect(): String {
        return listOf("org.hibernate.dialect.Oracle12cDialect", "org.hibernate.dialect.OracleDialect").first { name ->
            runCatching { Class.forName(name) }.isSuccess
        }
    }
}
