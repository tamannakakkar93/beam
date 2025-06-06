/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.dataflow.worker.windmill.client;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.beam.runners.dataflow.worker.windmill.client.grpc.observers.TerminatingStreamObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.LoggerFactory;

@RunWith(JUnit4.class)
public class ResettableThrowingStreamObserverTest {
  private final TerminatingStreamObserver<Integer> delegate = newDelegate();

  private static TerminatingStreamObserver<Integer> newDelegate() {
    return spy(
        new TerminatingStreamObserver<Integer>() {
          @Override
          public void onNext(Integer integer) {}

          @Override
          public void onError(Throwable throwable) {}

          @Override
          public void onCompleted() {}

          @Override
          public void terminate(Throwable terminationException) {}
        });
  }

  @Test
  public void testPoison_beforeDelegateSet() {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    observer.poison();
    verifyNoInteractions(delegate);
  }

  @Test
  public void testPoison_afterDelegateSet() throws WindmillStreamShutdownException {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    observer.reset(delegate);
    observer.poison();
    verify(delegate).terminate(isA(WindmillStreamShutdownException.class));
  }

  @Test
  public void testReset_afterPoisonedThrows() {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    observer.poison();
    assertThrows(WindmillStreamShutdownException.class, () -> observer.reset(newDelegate()));
  }

  @Test
  public void testOnNext_afterPoisonedThrows() {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    observer.poison();
    assertThrows(WindmillStreamShutdownException.class, () -> observer.onNext(1));
  }

  @Test
  public void testOnError_afterPoisonedThrows() {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    observer.poison();
    assertThrows(
        WindmillStreamShutdownException.class,
        () -> observer.onError(new RuntimeException("something bad happened.")));
  }

  @Test
  public void testOnCompleted_afterPoisonedThrows() {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    observer.poison();
    assertThrows(WindmillStreamShutdownException.class, observer::onCompleted);
  }

  @Test
  public void testReset_usesNewDelegate()
      throws WindmillStreamShutdownException,
          ResettableThrowingStreamObserver.StreamClosedException {
    ResettableThrowingStreamObserver<Integer> observer = newStreamObserver();
    TerminatingStreamObserver<Integer> firstObserver = newDelegate();
    observer.reset(firstObserver);
    observer.onNext(1);

    TerminatingStreamObserver<Integer> secondObserver = newDelegate();
    observer.reset(secondObserver);
    observer.onNext(2);

    verify(firstObserver).onNext(eq(1));
    verify(secondObserver).onNext(eq(2));
  }

  private <T> ResettableThrowingStreamObserver<T> newStreamObserver() {
    return new ResettableThrowingStreamObserver<>(LoggerFactory.getLogger(getClass()));
  }
}
