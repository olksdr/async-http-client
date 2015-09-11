/*
 * Copyright (c) 2015 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.asynchttpclient.reactivestreams;

import static org.asynchttpclient.test.TestUtils.LARGE_IMAGE_BYTES;
import static org.testng.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.asynchttpclient.AbstractBasicTest;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseHeaders;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.handler.StreamedAsyncHandler;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.annotations.Test;

public abstract class ReactiveStreamsTest extends AbstractBasicTest {

    @Test(groups = { "standalone", "default_provider" })
    public void streamedResponseTest() throws Throwable {
        try (AsyncHttpClient c = getAsyncHttpClient(null)) {

            ListenableFuture<SimpleStreamedAsyncHandler> future = c.preparePost(getTargetUrl())
                    .setBody(LARGE_IMAGE_BYTES)
                    .execute(new SimpleStreamedAsyncHandler());

            assertEquals(future.get().getBytes(), LARGE_IMAGE_BYTES);

            // Run it again to check that the pipeline is in a good state
            future = c.preparePost(getTargetUrl())
                    .setBody(LARGE_IMAGE_BYTES)
                    .execute(new SimpleStreamedAsyncHandler());

            assertEquals(future.get().getBytes(), LARGE_IMAGE_BYTES);

            // Make sure a regular request still works
            assertEquals(c.preparePost(getTargetUrl())
                    .setBody("Hello")
                    .execute().get().getResponseBody(), "Hello");

        }
    }

    @Test(groups = { "standalone", "default_provider" })
    public void cancelStreamedResponseTest() throws Throwable {
        try (AsyncHttpClient c = getAsyncHttpClient(null)) {

            // Cancel immediately
            c.preparePost(getTargetUrl())
                    .setBody(LARGE_IMAGE_BYTES)
                    .execute(new CancellingStreamedAsyncProvider(0)).get();

            // Cancel after 1 element
            c.preparePost(getTargetUrl())
                    .setBody(LARGE_IMAGE_BYTES)
                    .execute(new CancellingStreamedAsyncProvider(1)).get();

            // Cancel after 10 elements
            c.preparePost(getTargetUrl())
                    .setBody(LARGE_IMAGE_BYTES)
                    .execute(new CancellingStreamedAsyncProvider(10)).get();

            // Make sure a regular request works
            assertEquals(c.preparePost(getTargetUrl())
                    .setBody("Hello")
                    .execute().get().getResponseBody(), "Hello");

        }
    }

    static protected class SimpleStreamedAsyncHandler implements StreamedAsyncHandler<SimpleStreamedAsyncHandler>{
        private final SimpleSubscriber<HttpResponseBodyPart> subscriber;

        public SimpleStreamedAsyncHandler() {
            this(new SimpleSubscriber<HttpResponseBodyPart>());
        }

        public SimpleStreamedAsyncHandler(SimpleSubscriber<HttpResponseBodyPart> subscriber) {
            this.subscriber = subscriber;
        }
        @Override
        public State onStream(Publisher<HttpResponseBodyPart> publisher) {
            publisher.subscribe(subscriber);
            return State.CONTINUE;
        }

        @Override
        public void onThrowable(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
            throw new AssertionError("Should not have received body part");
        }

        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpResponseHeaders headers) throws Exception {
            return State.CONTINUE;
        }

        @Override
        public SimpleStreamedAsyncHandler onCompleted() throws Exception {
            return this;
        }

        public byte[] getBytes() throws Throwable {
            List<HttpResponseBodyPart> bodyParts = subscriber.getElements();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (HttpResponseBodyPart part : bodyParts) {
                part.writeTo(bytes);
            }
            return bytes.toByteArray();
        }
    }

    /**
     * Simple subscriber that requests and buffers one element at a time.
     */
    static protected class SimpleSubscriber<T> implements Subscriber<T> {
        private volatile Subscription subscription;
        private volatile Throwable error;
        private final List<T> elements = Collections.synchronizedList(new ArrayList<T>());
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(T t) {
            elements.add(t);
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            latch.countDown();
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        public List<T> getElements() throws Throwable {
            latch.await();
            if (error != null) {
                throw error;
            } else {
                return elements;
            }
        }
    }

    static class CancellingStreamedAsyncProvider implements StreamedAsyncHandler<CancellingStreamedAsyncProvider> {
        private final int cancelAfter;

        public CancellingStreamedAsyncProvider(int cancelAfter) {
            this.cancelAfter = cancelAfter;
        }

        @Override
        public State onStream(Publisher<HttpResponseBodyPart> publisher) {
            publisher.subscribe(new CancellingSubscriber<HttpResponseBodyPart>(cancelAfter));
            return State.CONTINUE;
        }

        @Override
        public void onThrowable(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
            throw new AssertionError("Should not have received body part");
        }

        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpResponseHeaders headers) throws Exception {
            return State.CONTINUE;
        }

        @Override
        public CancellingStreamedAsyncProvider onCompleted() throws Exception {
            return this;
        }
    }

    /**
     * Simple subscriber that cancels after receiving n elements.
     */
    static class CancellingSubscriber<T> implements Subscriber<T> {
        private final int cancelAfter;

        public CancellingSubscriber(int cancelAfter) {
            this.cancelAfter = cancelAfter;
        }

        private volatile Subscription subscription;
        private volatile int count;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            if (cancelAfter == 0) {
                subscription.cancel();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(T t) {
            count++;
            if (count == cancelAfter) {
                subscription.cancel();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable error) {
        }

        @Override
        public void onComplete() {
        }
    }
}
