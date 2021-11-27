package runonce.dao

import runonce.model.RunOnceExecutionStatus
import runonce.model.RunOnceExecutionStatus.*
import runonce.model.RunOnceRecord
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Instant
import kotlin.jvm.java
import kotlin.jvm.javaObjectType

/**
 * @author pobedenniy.alexey
 * @since 01.06.2020
 */
internal class RunOnceDao(
    private val databaseClient: DatabaseClient
) {
    private val insertSql = "insert into run_once_record (id, started_at, status_id, request) " +
            "values ($1, $2, $3, $4)"

    fun insert(record: RunOnceRecord): Mono<Int> {
        return databaseClient.inConnection { connection ->
            val statement = connection.createStatement(insertSql)
            with(statement) {
                bind("$1", record.id)
                bind("$2", record.startedAt)
                bind("$3", RUNNING.ordinal)
                if (record.request != null) {
                    bind("$4", record.request)
                } else {
                    bindNull("$4", String::class.java)
                }
            }
            statement
                .returnGeneratedValues("id")
                .execute()
                .toMono()
                .flatMap {
                    it
                        .rowsUpdated
                        .toMono()
                }
        }
    }

    private val selectSql = "select * from run_once_record where id=$1"
    fun load(idempotencyKey: String): Mono<RunOnceRecord> {
        return databaseClient
            .sql(selectSql)
            .bind("$1", idempotencyKey)
            .map { row ->
                RunOnceRecord(
                    id = idempotencyKey,
                    startedAt = row.get("started_at", Instant::class.java)!!,
                    finishedAt = row.get("finished_at", Instant::class.java),
                    status = values()[row.get(
                        "status_id",
                        Int::class.javaObjectType
                    )!!],
                    request = row.get("request", String::class.java),
                    response = row.get("response", String::class.java)
                )
            }
            .one()
    }

    private val finishSql = "update run_once_record set " +
            "finished_at=$1, status_id=$2, response=$3 where id=$4"

    fun finish(key: String, response: String?, status: RunOnceExecutionStatus): Mono<Int> {
        return databaseClient.inConnection { connection ->
            val statement = connection.createStatement(finishSql)
            with(statement) {
                bind("$1", Instant.now())
                bind("$2", status.ordinal)
                if (response != null) {
                    bind("$3", response)
                } else {
                    bindNull("$3", String::class.java)
                }
                bind("$4", key)
            }
            statement
                .execute()
                .toMono()
                .flatMap {
                    it
                        .rowsUpdated
                        .toMono()
                }
        }
    }

    private val runSql = "update run_once_record set " +
            "started_at=$1, status_id=$2 where id=$3 and status_id=$4"

    fun run(key: String, expectedStatus: RunOnceExecutionStatus): Mono<Int> {
        return databaseClient.inConnection { connection ->
            val statement = connection.createStatement(runSql)
            with(statement) {
                bind("$1", Instant.now())
                bind("$2", RUNNING.ordinal)
                bind("$3", key)
                bind("$4", expectedStatus.ordinal)
            }
            statement
                .execute()
                .toMono()
                .flatMap {
                    it
                        .rowsUpdated
                        .toMono()
                }
        }
    }
}