package runonce.exceptions



class RetryableExceptionWrapper(msg: String, ex: Throwable) : RetryableException(msg, ex)