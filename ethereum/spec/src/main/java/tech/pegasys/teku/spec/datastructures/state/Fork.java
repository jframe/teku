/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Fork extends Container3<Fork, SszBytes4, SszBytes4, SszUInt64> {
  private static final SerializableTypeDefinition<Fork> FORK_TYPE =
      SerializableTypeDefinition.object(Fork.class)
          .name("Fork")
          .description(
              "The [Fork](https://github.com/ethereum/consensus-specs/blob/v1.0.1/specs/phase0/beacon-chain.md#fork) object from the Eth2.0 spec.")
          .withField("previous_version", BYTES4_TYPE, Fork::getPreviousVersion)
          .withField("current_version", BYTES4_TYPE, Fork::getCurrentVersion)
          .withField("epoch", UINT64_TYPE, Fork::getEpoch)
          .build();

  public static class ForkSchema extends ContainerSchema3<Fork, SszBytes4, SszBytes4, SszUInt64> {

    public ForkSchema() {
      super(
          "Fork",
          namedSchema("previous_version", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("current_version", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public Fork createFromBackingNode(TreeNode node) {
      return new Fork(this, node);
    }
  }

  public static final ForkSchema SSZ_SCHEMA = new ForkSchema();

  private Fork(ForkSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Fork(Bytes4 previousVersion, Bytes4 currentVersion, UInt64 epoch) {
    super(
        SSZ_SCHEMA,
        SszBytes4.of(previousVersion),
        SszBytes4.of(currentVersion),
        SszUInt64.of(epoch));
  }

  public Bytes4 getPreviousVersion() {
    return getField0().get();
  }

  public Bytes4 getCurrentVersion() {
    return getField1().get();
  }

  public UInt64 getEpoch() {
    return getField2().get();
  }

  public static SerializableTypeDefinition<Fork> getJsonTypeDefinition() {
    return FORK_TYPE;
  }
}
