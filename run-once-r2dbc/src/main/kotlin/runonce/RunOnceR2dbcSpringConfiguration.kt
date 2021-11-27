package runonce

import com.fasterxml.jackson.databind.ObjectMapper
import runonce.dao.RunOnceDao
import runonce.DatabaseSharedStateServiceImpl
import runonce.SharedStateService
import runonce.RunOnceServiceImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient

@Configuration
@Suppress("UndocumentedPublicFunction")
class RunOnceR2dbcSpringConfiguration {
    @Bean
    fun sharedStateService(
        databaseClient: DatabaseClient,
        mapper: ObjectMapper,
        tm: R2dbcTransactionManager
    ): SharedStateService = DatabaseSharedStateServiceImpl(
        RunOnceDao(databaseClient), mapper, tm
    )

    @Bean
    fun runOnceService(sharedStateServiceImpl: SharedStateService) =
        RunOnceServiceImpl(sharedStateServiceImpl)
}