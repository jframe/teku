/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.pow;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

public class BlockBasedEth1HeadTracker implements Eth1HeadTracker {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AsyncRunner asyncRunner;
  private final Eth1Provider eth1Provider;
  private final SpecConfig config;
  private Optional<UInt64> headAtFollowDistance = Optional.empty();
  private final AtomicBoolean reachedHead = new AtomicBoolean(false);

  private final Subscribers<HeadUpdatedSubscriber> subscribers = Subscribers.create(true);

  public BlockBasedEth1HeadTracker(
      final AsyncRunner asyncRunner, final Eth1Provider eth1Provider, final SpecConfig config) {
    this.asyncRunner = asyncRunner;
    this.eth1Provider = eth1Provider;
    this.config = config;
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    pollLatestHead();
  }

  private void pollLatestHead() {
    if (!running.get()) {
      return;
    }
    eth1Provider
        .getLatestEth1Block()
        .thenAccept(this::onLatestBlockHead)
        .exceptionally(
            error -> {
              LOG.debug("Failed to get latest Eth1 chain head. Will retry.", error);
              return null;
            })
        .always(
            () ->
                asyncRunner
                    .runAfterDelay(
                        this::pollLatestHead, Duration.ofSeconds(config.getSecondsPerEth1Block()))
                    .finish(
                        () -> {},
                        error ->
                            LOG.error("Scheduling next check of Eth1 chain head failed", error)));
  }

  private void onLatestBlockHead(final Block headBlock) {
    final UInt64 headBlockNumber = UInt64.valueOf(headBlock.getNumber());
    if (headBlockNumber.compareTo(config.getEth1FollowDistance()) < 0) {
      LOG.debug("Not processing Eth1 blocks because chain has not reached minimum follow distance");
      return;
    }
    final UInt64 newHeadAtFollowDistance = headBlockNumber.minus(config.getEth1FollowDistance());
    if (headAtFollowDistance
        .map(current -> current.compareTo(newHeadAtFollowDistance) < 0)
        .orElse(true)) {
      if (reachedHead.compareAndSet(false, true)) {
        reachedHead.set(true);
      }
      headAtFollowDistance = Optional.of(newHeadAtFollowDistance);
      LOG.debug("ETH1 block at follow distance updated to {}", newHeadAtFollowDistance);
      subscribers.deliver(HeadUpdatedSubscriber::onHeadUpdated, newHeadAtFollowDistance);
    }
  }

  @Override
  public long subscribe(final HeadUpdatedSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  @Override
  public void unsubscribe(final long subscriberId) {
    subscribers.unsubscribe(subscriberId);
  }

  @Override
  public void stop() {
    running.set(false);
  }
}
