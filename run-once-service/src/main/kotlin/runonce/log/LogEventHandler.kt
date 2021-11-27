package runonce.log

import java.util.concurrent.TimeoutException

interface LogEventHandler {
    fun logCompletedEarlier(key: String)
    fun logRetry(key: String)
    fun logStart(key: String)
    fun finish(key: String)
    fun timeout(key: String, err: TimeoutException)
    fun error(key: String, err: Throwable)

}
