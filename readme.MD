# Run-once

Run-once is a library for implementing idempotent fail-safe services, built on top of [project reactor](https://projectreactor.io).
Idempotency is crucial when working in unreliable environment. Kafka can actually deliver same
message multiple times, your client can try to PUT same entities through your REST endpoints several time and so on.

## Unreliable environment

Everything can break. Point. During several past years i came across various scenarios when systems were not ready
for unreliable environment. 

### Kafka

One of the first problems i stumbled upon when working on a new project was "at least once" Kafka's 
delivery guarantee. While i was totally aware of this, faults came as a surprise. Actualy your application
can get multiple copies of one message if you fail to send acks to brokers. 

### Rest

Imagine someone's browsing through your webstore and cliks on "Create order" button. There are multiple
points of failure. Users outbound channel can fail leaving him with an error and you unaware of an order.
Your service can fail (i hope you can handle such cases). And what's more important users' inbound channel can fail,
leaving him unaware what's his order status - is it accepted, rejected, else?

From the client point of view all three cases are the same - we're aware of failure but can't tell one from
another. In such cases common pattern is to use retires. So client decides to "Create order" once again leaving
you in a worst case scenario with multiple orders (i hope you know how to deal with them). 

That can be even worse. Imagine there is not network failure, but rather client's request got stuck in your
service waiting for some job to finish. So once client get's network timeout he can retry failed request once again.

### Other systems

Another interesting point of failure is interaction with other backend's, when you play role of a client. Here
we can observe same fault patterns as above. As we all know, network is unreliable and we all have to treat network
as something that can fail any time. Rly.

## Tracking requests with run-once

Run-once is the library to help you track your requests (both incoming and outgoing), their state and resume failed
requests in case of subsequent request. In order to make your system idempotent, you need to:

- agree with all your clients that all requests should have unique identifiers of some sort (i suggest to use [ULID](https://github.com/ulid/spec)). 
Any subsequent retried request should use the same key as original request. You can use some header (in case of REST), some part (field)
  of data (like customer order number), key in case of Kafka or whatever.
- implement your idempotent logic inside  run-once  call 
- handle failures and retries


```kotlin
service
  .runOnce(
    //this is unique key which identifies request
    operationKey,
    RunOnceRequest.newRequest(
      //this is data passed to you from client
      Mono.just(requestData),
      //this is the handler which will be called only if request with same id did not run previously 
      //or failed with RetryableException. 
      //requestData is data passed from previous argument, isRetry will be true if this request failed previously
      { requestData, isRetry : Boolean ->
        Mono.just(request)
      },
      //this is post-handler, which will be invoked every time, no matter if request has previously completed
      //response is a result of invoking handler
      //alreadyCompleted will be true if this is subsequent request with same id
      { response, alreadyCompleted ->
        if (!alreadyCompleted) handlerMock else Mono.just(expectedResponseData)
      }
    ))
```

## What run-once will do for me

1. run-once uses shared state to sync different instances of your application. [R2dbc](https://r2dbc.io) implementation provided,
other can be written. Shared state guarantees only one instance is serving request with this id
2. If a request has not run previously run-once will call your handler code and save the response to shared state
3. If a request had run previously run-once depending on previous result wlll:
 - call post-handler and pass previous result from shared state if request previously completed normally
 - call handler with isRetry flag true if previous run completed with RetryableException (or any custom exception you consider retryable)
 - return OperationFailedException if previous run completed with non-RetryableException
4. If a request is still running and not exceeded its ttl yet OperationAlreadyStartedException will be returned

That's all, happy coding.
