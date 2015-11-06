/*
 * Copyright (c) 2011-2015 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectreactor.bench.rx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.Processors;
import reactor.core.processor.BaseProcessor;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xmx1024m"})
@State(Scope.Thread)
public class ReactorComparison2 {

	PublishSubject<Integer> rxJust;
	PublishSubject<Integer> rxJustAsync;

	BaseProcessor<Integer, Integer> rcJust;
	BaseProcessor<Integer, Integer> rcJustAsync;

	LatchedRxObserver asyncRxObserver;
	LatchedObserver   asyncObserver;

	@Setup(Level.Iteration)
	public void setup(Blackhole bh) throws InterruptedException {
		//  Timers.global();

		rxJust = PublishSubject.create();
		rxJust.onBackpressureBuffer()
		      .subscribe(new LatchedRxObserver(bh));
		rxJust.onBackpressureBuffer()
		      .subscribe(new LatchedRxObserver(bh));

		rxJustAsync = PublishSubject.create();
		asyncRxObserver = new LatchedRxObserver(bh);
		rxJustAsync.onBackpressureBuffer()
		           .observeOn(Schedulers.computation())
		           .subscribe(asyncRxObserver);

		rcJust = Processors.emitter(256);
		rcJust.subscribe(new LatchedObserver(bh));
		rcJust.subscribe(new LatchedObserver(bh));
		rcJust.start();

		asyncObserver = new LatchedObserver(bh);
		rcJustAsync = Processors.emitter(256);
		rcJustAsync.process(Processors.singleGroup()
		                              .get())
		           .subscribe(asyncObserver);
		rcJustAsync.start();
	}

	@TearDown(Level.Iteration)
	public void doTeardown() throws InterruptedException {
		rxJust.onCompleted();

		rcJust.onComplete();

		rxJustAsync.onCompleted();
		asyncRxObserver.cdl.await();

		rcJustAsync.onComplete();
		asyncObserver.cdl.await();
	}

	static final Integer DATA = 0;

	@Benchmark
	public void rxJust() {
		rxJust.onNext(DATA);
	}

	@Benchmark
	public void rcJust() {
		rcJust.onNext(DATA);
	}

	@Benchmark
	public void rxJustAsync() throws Exception {
		rxJustAsync.onNext(DATA);
	}

	@Benchmark
	public void rcJustAsync() throws Exception {
		rcJustAsync.onNext(DATA);
	}

	@Benchmark
	public void rx() throws Exception {
		PublishSubject.create();
	}

	@Benchmark
	public void rc() throws Exception {
		Processors.emitter();
	}

	static final class LatchedObserver implements Subscriber<Object> {

		final CountDownLatch cdl;

		final Blackhole bh;

		Subscription s;

		public LatchedObserver(Blackhole bh) {
			cdl = new CountDownLatch(1);
			this.bh = bh;
		}

		@Override
		public void onSubscribe(Subscription s) {
			this.s = s;
			s.request(1L);
		}

		@Override
		public void onNext(Object t) {
			bh.consume(t);
			s.request(1L);
		}

		@Override
		public void onError(Throwable t) {
			t.printStackTrace();
			cdl.countDown();
		}

		@Override
		public void onComplete() {
			cdl.countDown();
		}

	}

	static final class LatchedRxObserver extends rx.Subscriber<Object> {

		final CountDownLatch cdl;

		final Blackhole bh;

		public LatchedRxObserver(Blackhole bh) {
			cdl = new CountDownLatch(1);
			this.bh = bh;
		}

		@Override
		public void onStart() {
			request(1L);
		}

		@Override
		public void onNext(Object t) {
			bh.consume(t);
			request(1L);
		}

		@Override
		public void onError(Throwable t) {
			t.printStackTrace();
			cdl.countDown();
		}

		@Override
		public void onCompleted() {
			cdl.countDown();
		}

	}
}