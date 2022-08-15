package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.selection.api.ConfigSelection
import com.r3.corda.lib.tokens.selection.database.config.MAX_RETRIES_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_CAP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_SLEEP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.selection.memory.config.CACHE_SIZE_DEFAULT
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.memory.selector.LocalTokenSelector
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import com.typesafe.config.ConfigFactory
import net.corda.core.identity.CordaX500Name
import net.corda.node.internal.cordapp.TypesafeCordappConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.createMockCordaService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test

class ConfigSelectionTest {
    private lateinit var services: MockServices

    @Before
    fun setup() {
        services = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = listOf("com.r3.corda.lib.tokens.workflows"),
                initialIdentity = TestIdentity(CordaX500Name("Test", "London", "GB")),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6),
                moreIdentities = emptySet(),
                moreKeys = emptySet()
        ).second
    }

    @Test
    fun `test full database selection`() {
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "database {\n" +
                "maxRetries: 13\n" +
                "retrySleep: 300\n" +
                "retryCap: 1345\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)
        val selection = ConfigSelection.getPreferredSelection(services, cordappConfig)
        assertThat(selection).isInstanceOf(DatabaseTokenSelection::class.java)
        assertThat(selection).hasFieldOrPropertyWithValue("maxRetries", 13)
        assertThat(selection).hasFieldOrPropertyWithValue("retrySleep", 300)
        assertThat(selection).hasFieldOrPropertyWithValue("retryCap", 1345)
    }

    @Test
    fun `test full in memory selection public key`() {
        createMockCordaService(services, ::VaultWatcherService)
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "inMemory {\n" +
                "cacheSize: 9000\n" +
                "indexingStrategies: [${VaultWatcherService.IndexingType.PUBLIC_KEY}]\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)

        val inMemoryConfig = InMemorySelectionConfig.parse(cordappConfig)
        assertThat(inMemoryConfig.cacheSize).isEqualTo(9000)
        assertThat(inMemoryConfig.indexingStrategies).isEqualTo(listOf(VaultWatcherService.IndexingType.PUBLIC_KEY))

        val selection = ConfigSelection.getPreferredSelection(services, cordappConfig)
        assertThat(selection).isInstanceOf(LocalTokenSelector::class.java)
    }

    @Test
    fun `test full in memory selection with external id`() {
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "inMemory {\n" +
                "cacheSize: 9000\n" +
                "indexingStrategies: [\"${VaultWatcherService.IndexingType.EXTERNAL_ID}\", \"${VaultWatcherService.IndexingType.PUBLIC_KEY}\"]\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)
        val inMemoryConfig = InMemorySelectionConfig.parse(cordappConfig)
        assertThat(inMemoryConfig.cacheSize).isEqualTo(9000)
        assertThat(inMemoryConfig.indexingStrategies).isEqualTo(listOf(VaultWatcherService.IndexingType.EXTERNAL_ID, VaultWatcherService.IndexingType.PUBLIC_KEY))
    }

    @Test
    fun `test no selection in config`() {
        // no
        val configNull = ConfigFactory.parseString("string=string\nint=1\nfloat=1.0\ndouble=1.0\nnumber=2\ndouble=1.01\nbool=false")
        val cordappConfigNull = TypesafeCordappConfig(configNull)
        val selectionNull = ConfigSelection.getPreferredSelection(services, cordappConfigNull)
        assertThat(selectionNull).isInstanceOf(DatabaseTokenSelection::class.java)
        // blank
        val configBlank = ConfigFactory.parseString("string=string\nstateSelection {\n}")
        val cordappConfigBlank = TypesafeCordappConfig(configBlank)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            ConfigSelection.getPreferredSelection(services, cordappConfigBlank)
        }
    }

    @Test
    fun `test defaults database selection`() {
        val configOne = ConfigFactory.parseString("stateSelection {\n" +
                "database {\n" +
                "maxRetries: 13\n" +
                "retryCap: 1345\n" +
                "}\n" +
                "}")
        val cordappConfigOne = TypesafeCordappConfig(configOne)
        val selectionOne = ConfigSelection.getPreferredSelection(services, cordappConfigOne)
        assertThat(selectionOne).isInstanceOf(DatabaseTokenSelection::class.java)
        assertThat(selectionOne).hasFieldOrPropertyWithValue("maxRetries", 13)
        assertThat(selectionOne).hasFieldOrPropertyWithValue("retrySleep", RETRY_SLEEP_DEFAULT)
        assertThat(selectionOne).hasFieldOrPropertyWithValue("retryCap", 1345)

        val configAll = ConfigFactory.parseString("stateSelection {\n" +
                "database {}\n" +
                "}")
        val cordappConfigAll = TypesafeCordappConfig(configAll)
        val selectionAll = ConfigSelection.getPreferredSelection(services, cordappConfigAll)
        assertThat(selectionAll).isInstanceOf(DatabaseTokenSelection::class.java)
        assertThat(selectionAll).hasFieldOrPropertyWithValue("maxRetries", MAX_RETRIES_DEFAULT)
        assertThat(selectionAll).hasFieldOrPropertyWithValue("retrySleep", RETRY_SLEEP_DEFAULT)
        assertThat(selectionAll).hasFieldOrPropertyWithValue("retryCap", RETRY_CAP_DEFAULT)
    }

    @Test
    fun `test defaults in memory selection`() {
        createMockCordaService(services, ::VaultWatcherService)
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "inMemory {\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)
        val inMemoryConfig = InMemorySelectionConfig.parse(cordappConfig)
        assertThat(inMemoryConfig.cacheSize).isEqualTo(CACHE_SIZE_DEFAULT)
    }

    @Test
    fun `test fail database selection`() {
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "database {\n" +
                "maxRetries: 13\n" +
                "retrySleep: kerfuffle\n" +
                "retryCap: 1345\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            ConfigSelection.getPreferredSelection(services, cordappConfig)
        }
    }

    @Test
    fun `test fail in memory selection`() {
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "inMemory {\n" +
                "cacheSize: kerfuffle\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            ConfigSelection.getPreferredSelection(services, cordappConfig)
        }
    }

    @Test
    fun `no in memory selection installed on node`() {
        val config = ConfigFactory.parseString("stateSelection {\n" +
                "inMemory {\n" +
                "cacheSize: 9000\n" +
                "}\n" +
                "}")
        val cordappConfig = TypesafeCordappConfig(config)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            ConfigSelection.getPreferredSelection(services, cordappConfig)
        }.withMessageContaining("Couldn't find VaultWatcherService in CordaServices")
    }
}