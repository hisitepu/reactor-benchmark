/*
 * Copyright (c) 2011-2014 GoPivotal, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectreactor.bench.reactor;

import org.openjdk.jmh.annotations.*;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.Processors;
import reactor.bus.Event;
import reactor.core.processor.RingBufferProcessor;
import reactor.core.processor.RingBufferWorkProcessor;
import reactor.core.support.wait.YieldingWaitStrategy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jon Brisbin
 * @author Stephane Maldini
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class DispatcherBenchmarks {

	static int BACKLOG = 2048;

	RingBufferProcessor<Event<?>>     ringBufferDispatcher;
	RingBufferWorkProcessor<Event<?>> workQueueDispatcher;
	Event<?>                          event;
	AtomicLong                        counter;

	@Setup
	public void setup() {
		event = Event.wrap("Hello World!");
		counter = new AtomicLong(0);

		ringBufferDispatcher = RingBufferProcessor.create(
		  "ringBufferDispatcher",
		  BACKLOG,
		  new YieldingWaitStrategy()
		);

		workQueueDispatcher = RingBufferWorkProcessor.create(
		  "workQueueDispatcher",
		  BACKLOG,
		  new YieldingWaitStrategy()
		);

		Subscriber<Event<?>> sharedCounter = new Subscriber<Event<?>>() {
			@Override
			public void onNext(Event<?> event) {
				counter.incrementAndGet();
			}

			@Override
			public void onSubscribe(Subscription s) {
				s.request(Long.MAX_VALUE);
			}

			@Override
			public void onError(Throwable t) {

			}

			@Override
			public void onComplete() {

			}
		};

		ringBufferDispatcher.subscribe(sharedCounter);
		for(int i = 0 ; i < Processors.DEFAULT_POOL_SIZE ; i++){
			workQueueDispatcher.subscribe(sharedCounter);
		}
	}

	@TearDown
	public void tearDown() throws InterruptedException {
		ringBufferDispatcher.onComplete();
		workQueueDispatcher.onComplete();
	}

	@Benchmark
	public void ringBuffer() {
		doTest(ringBufferDispatcher);
	}

	@Benchmark
	public void workQueue() {
		doTest(workQueueDispatcher);
	}

	private void doTest(Processor<Event<?>, Event<?>> dispatcher) {
		dispatcher.onNext(
		  event
		);
	}

}
