package runonce.model


import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/**
 * @author pobedenniy.alexey
 * @since 29.05.2020
 */
@Table("RUN_ONCE_RECORD")
data class RunOnceRecord(
    @Id
    val id: String,
    @Column("started_at")
    val startedAt: Instant,
    @Column("finished_at")
    val finishedAt: Instant? = null,
    val status: RunOnceExecutionStatus,
    val request: String? = null,
    val response: String? = null
)  {
    fun update(status: RunOnceExecutionStatus, now: Instant): RunOnceRecord {
        return RunOnceRecord(
            this.id,
            now,
            this.finishedAt,
            status,
            this.request,
            this.response
        )
    }
}

enum class RunOnceExecutionStatus {
    INITIAL, RUNNING, FAILED_RETRYABLE, FAILED_NON_RETRYABLE,
    COMPLETED
}
