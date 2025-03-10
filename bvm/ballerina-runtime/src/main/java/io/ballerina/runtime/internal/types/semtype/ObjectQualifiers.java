/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.runtime.internal.types.semtype;

import io.ballerina.runtime.api.types.semtype.Builder;
import io.ballerina.runtime.api.types.semtype.Env;
import io.ballerina.runtime.api.types.semtype.SemType;

import static io.ballerina.runtime.api.types.semtype.Builder.getBooleanConst;
import static io.ballerina.runtime.api.types.semtype.Builder.getStringConst;
import static io.ballerina.runtime.api.types.semtype.Core.union;

/**
 * Represents the qualifiers of an object type.
 *
 * @param isolated         whether the object is isolated
 * @param readonly         whether the object is readonly
 * @param networkQualifier the network qualifier of the object (either {@link NetworkQualifier#Client},
 *                         {@link NetworkQualifier#Service}, or {@link NetworkQualifier#None})
 * @since 2201.12.0
 */
public record ObjectQualifiers(boolean isolated, boolean readonly, NetworkQualifier networkQualifier) {

    public MappingDefinition.Field field(Env env) {
        MappingDefinition md = new MappingDefinition();
        MappingDefinition.Field isolatedField =
                new MappingDefinition.Field("isolated", isolated ? getBooleanConst(true) : Builder.getBooleanType(),
                        true, false);
        MappingDefinition.Field networkField = networkQualifier.field();
        SemType ty = md.defineMappingTypeWrapped(env, new MappingDefinition.Field[]{isolatedField, networkField},
                Builder.getNeverType(), CellAtomicType.CellMutability.CELL_MUT_NONE);
        return new MappingDefinition.Field("$qualifiers", ty, true, false);
    }

    public enum NetworkQualifier {
        Client,
        Service,
        None;

        private static final SemType CLIENT_TAG = getStringConst("client");
        private static final MappingDefinition.Field CLIENT =
                new MappingDefinition.Field("network", CLIENT_TAG, true, false);

        private static final SemType SERVICE_TAG = getStringConst("service");
        private static final MappingDefinition.Field SERVICE =
                new MappingDefinition.Field("network", SERVICE_TAG, true, false);

        // Object can't be both client and service, which is enforced by the enum. We are using a union here so that
        // if this is none it matches both
        private static final MappingDefinition.Field NONE =
                new MappingDefinition.Field("network", union(CLIENT_TAG, SERVICE_TAG), true, false);

        private MappingDefinition.Field field() {
            return switch (this) {
                case Client -> CLIENT;
                case Service -> SERVICE;
                case None -> NONE;
            };
        }
    }
}
