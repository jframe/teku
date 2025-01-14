/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.leveldb;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import com.google.common.primitives.Ints;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

class LevelDbInstanceTest {

  private static final IntSerializer INT_SERIALIZER = new IntSerializer();

  private static final KvStoreVariable<Integer> VARIABLE_1 =
      KvStoreVariable.create(0, INT_SERIALIZER);
  private static final KvStoreVariable<Integer> VARIABLE_2 =
      KvStoreVariable.create(1, INT_SERIALIZER);
  private static final KvStoreVariable<UInt64> VARIABLE_3 =
      KvStoreVariable.create(2, UINT64_SERIALIZER);

  private static final KvStoreColumn<Integer, Integer> COLUMN_1 =
      KvStoreColumn.create(1, INT_SERIALIZER, INT_SERIALIZER);
  private static final KvStoreColumn<Integer, Integer> COLUMN_2 =
      KvStoreColumn.create(2, INT_SERIALIZER, INT_SERIALIZER);
  private static final KvStoreColumn<Integer, Integer> COLUMN_3 =
      KvStoreColumn.create(3, INT_SERIALIZER, INT_SERIALIZER);
  private static final KvStoreColumn<UInt64, UInt64> COLUMN_4 =
      KvStoreColumn.create(4, UINT64_SERIALIZER, UINT64_SERIALIZER);

  private KvStoreAccessor instance;

  @BeforeAll
  static void setUp() {
    assumeThat(DatabaseVersion.isLevelDbSupported())
        .describedAs("LevelDB support required")
        .isTrue();
  }

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    instance =
        LevelDbInstanceFactory.create(
            new NoOpMetricsSystem(),
            TekuMetricCategory.STORAGE,
            KvStoreConfiguration.v6SingleDefaults().withDatabaseDir(tempDir),
            List.of(COLUMN_1, COLUMN_2, COLUMN_3));
  }

  @AfterEach
  void tearDown() throws Exception {
    instance.close();
  }

  @Test
  void shouldStoreAndLoadSimpleKey() {
    try (final KvStoreTransaction update = instance.startTransaction()) {
      update.put(COLUMN_1, 0, 0);
      update.put(COLUMN_1, 1, 1);
      update.put(COLUMN_2, 1, 2);
      update.put(COLUMN_3, 0, 3);
      update.commit();
    }

    assertThat(instance.get(COLUMN_1, 0)).contains(0);
    assertThat(instance.get(COLUMN_1, 1)).contains(1);
    assertThat(instance.get(COLUMN_1, 2)).isEmpty();

    assertThat(instance.get(COLUMN_2, 0)).isEmpty();
    assertThat(instance.get(COLUMN_2, 1)).contains(2);

    assertThat(instance.get(COLUMN_3, 0)).contains(3);
    assertThat(instance.get(COLUMN_3, 1)).isEmpty();
  }

  @Test
  void shouldStoreAndLoadVariables() {
    assertThat(instance.get(VARIABLE_1)).isEmpty();
    assertThat(instance.get(VARIABLE_2)).isEmpty();

    try (final KvStoreTransaction update = instance.startTransaction()) {
      update.put(VARIABLE_1, 0);
      update.put(VARIABLE_2, 1);
      update.commit();
    }

    assertThat(instance.get(VARIABLE_1)).contains(0);
    assertThat(instance.get(VARIABLE_2)).contains(1);
  }

  @Test
  void stream_includeValuesFromMiddleOfColumnRange_startAndEndExist() {
    update(
        tx -> {
          for (int i = 0; i < 10; i++) {
            tx.put(COLUMN_1, i, i);
          }
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(COLUMN_1, 2, 8)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7),
              ColumnEntry.create(8, 8));
    }
  }

  @Test
  void stream_includeAllValuesInDatabase_startAndEndExist() {
    update(
        tx -> {
          for (int i = 0; i < 10; i++) {
            tx.put(COLUMN_1, i, i);
          }
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(COLUMN_1, 0, 9)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(0, 0),
              ColumnEntry.create(1, 1),
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7),
              ColumnEntry.create(8, 8),
              ColumnEntry.create(9, 9));
    }
  }

  @Test
  void stream_includeAllValuesInDatabase_startAndEndBeyondRange() {
    update(
        tx -> {
          for (int i = 2; i < 8; i++) {
            tx.put(COLUMN_1, i, i);
          }
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(COLUMN_1, 0, 10)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7));
    }
  }

  @Test
  void stream_includeAllValuesFromMiddleColumn_startAndEndBeyondRange() {
    update(
        tx -> {
          tx.put(COLUMN_1, 5, 5);
          tx.put(COLUMN_1, 6, 6);
          for (int i = 2; i < 8; i++) {
            tx.put(COLUMN_2, i, i);
          }
          tx.put(COLUMN_3, 0, 0);
          tx.put(COLUMN_3, 8, 8);
          tx.put(VARIABLE_1, 6);
          tx.put(VARIABLE_2, 7);
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(COLUMN_2, 0, 10)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7));
    }
  }

  @Test
  void stream_performUnsignedComparisonsOnKeys() {
    update(
        tx -> {
          for (int i = 499; i <= 655; i++) {
            tx.put(COLUMN_4, UInt64.valueOf(i), UInt64.valueOf(i));
          }
        });

    // Deliberately select a range where signed comparison would cause the stream to stop early
    final int startInclusive = 500;
    final int endInclusive = 650;

    final List<UInt64> expectedKeys =
        IntStream.rangeClosed(startInclusive, endInclusive)
            .mapToObj(UInt64::valueOf)
            .collect(toList());
    try (final Stream<ColumnEntry<UInt64, UInt64>> stream =
        instance.stream(COLUMN_4, UInt64.valueOf(startInclusive), UInt64.valueOf(endInclusive))) {
      assertThat(stream.map(ColumnEntry::getKey)).containsExactlyElementsOf(expectedKeys);
    }
  }

  @Test
  void putRaw_shouldWriteToStorage() {
    // random data is fine, but must not have signed bit set because of the 'valueOf' logic
    final Bytes byteData = Bytes.random(8).and(Bytes.fromHexString("0x7FFFFFFFFFFFFFFF"));
    final UInt64 data = UInt64.valueOf(byteData.toLong());
    update(tx -> tx.putRaw(VARIABLE_3, byteData));
    assertThat(instance.get(VARIABLE_3)).contains(data);
    assertThat(instance.getRaw(VARIABLE_3)).contains(byteData);
  }

  @Test
  void streamRaw_shouldGetByteData() {
    // random data is fine, but must not have signed bit set because of the 'valueOf' logic
    final Bytes byteData = Bytes.random(8).and(Bytes.fromHexString("0x7FFFFFFFFFFFFFFF"));
    final UInt64 data = UInt64.valueOf(byteData.toLong());
    update(tx -> tx.put(COLUMN_4, UInt64.ZERO, data));

    assertThat(instance.get(COLUMN_4, UInt64.ZERO)).contains(data);
    try (final Stream<ColumnEntry<Bytes, Bytes>> stream = instance.streamRaw(COLUMN_4)) {
      assertThat(stream)
          .containsExactly(ColumnEntry.create(Bytes.fromHexString("0x0000000000000000"), byteData));
    }
  }

  @Test
  void getFloorEntry_shouldGetMatchingEntryWhenKeyExists() {
    update(
        tx -> {
          tx.put(COLUMN_1, 1, 1);
          tx.put(COLUMN_1, 2, 2);
          tx.put(COLUMN_1, 3, 3);
          tx.put(COLUMN_1, 4, 4);
        });

    assertThat(instance.getFloorEntry(COLUMN_1, 3)).contains(ColumnEntry.create(3, 3));
  }

  @Test
  void getFloorEntry_shouldGetClosestPriorEntryWhenKeyDoesNotExist() {
    update(
        tx -> {
          tx.put(COLUMN_1, 1, 1);
          tx.put(COLUMN_1, 4, 4);
        });

    assertThat(instance.getFloorEntry(COLUMN_1, 3)).contains(ColumnEntry.create(1, 1));
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenNoPriorKey() {
    update(
        tx -> {
          tx.put(COLUMN_1, 3, 3);
          tx.put(COLUMN_1, 4, 4);
        });

    assertThat(instance.getFloorEntry(COLUMN_1, 2)).isEmpty();
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenNoPriorKeyInColumn() {
    update(
        tx -> {
          tx.put(COLUMN_1, 1, 1);
          tx.put(COLUMN_1, 4, 4);

          tx.put(COLUMN_2, 3, 3);
          tx.put(COLUMN_2, 4, 4);
        });

    assertThat(instance.getFloorEntry(COLUMN_2, 2)).isEmpty();
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenKeyAfterLastEntryInColumn() {
    update(
        tx -> {
          tx.put(COLUMN_1, 1, 1);
          tx.put(COLUMN_1, 4, 4);

          tx.put(COLUMN_2, 3, 3);
          tx.put(COLUMN_2, 4, 4);
        });

    assertThat(instance.getFloorEntry(COLUMN_1, 5)).contains(ColumnEntry.create(4, 4));
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenKeyAfterLastEntryInDatabase() {
    update(
        tx -> {
          tx.put(COLUMN_1, 1, 1);
          tx.put(COLUMN_1, 4, 4);
        });

    assertThat(instance.getFloorEntry(COLUMN_1, 5)).contains(ColumnEntry.create(4, 4));
  }

  private void update(final Consumer<KvStoreTransaction> updater) {
    try (final KvStoreTransaction transaction = instance.startTransaction()) {
      updater.accept(transaction);
      transaction.commit();
    }
  }

  private static class IntSerializer implements KvStoreSerializer<Integer> {

    @Override
    public Integer deserialize(final byte[] data) {
      return Ints.fromByteArray(data);
    }

    @Override
    public byte[] serialize(final Integer value) {
      return Ints.toByteArray(value);
    }
  }
}
