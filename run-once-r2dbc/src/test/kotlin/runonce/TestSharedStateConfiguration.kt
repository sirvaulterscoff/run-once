package runonce

import com.fasterxml.jackson.databind.ObjectMapper
import runonce.dao.RunOnceDao
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import org.springframework.r2dbc.core.DatabaseClient
import javax.xml.crypto.Data

@SpringBootConfiguration
@EnableAutoConfiguration
open class TestSharedStateConfiguration {

    @Bean
    open fun stateService(
        tm: R2dbcTransactionManager,
        databaseClient: DatabaseClient
    ): SharedStateService = DatabaseSharedStateServiceImpl(
        RunOnceDao(databaseClient), ObjectMapper(),
        tm
    )

    @Bean
    open fun sharedStateService(stateService: SharedStateService) =
        RunOnceServiceImpl(stateService)

    @Bean
    open fun initializer(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
        val initializer =
            ConnectionFactoryInitializer()
        initializer.setConnectionFactory(connectionFactory)
        val populator = CompositeDatabasePopulator()
        populator.addPopulators(ResourceDatabasePopulator(ClassPathResource("create.sql")))
        initializer.setDatabasePopulator(populator)
        return initializer
    }
}
