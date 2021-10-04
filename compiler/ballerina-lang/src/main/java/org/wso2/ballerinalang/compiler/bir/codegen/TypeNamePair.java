/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.ballerinalang.compiler.bir.codegen;

import org.wso2.ballerinalang.compiler.semantics.model.types.BType;

/**
 * The class that keeps the ballerina types and their names for generating type constants.
 *
 * @since 2.0.0
 */
public class TypeNamePair {

    BType type;
    String varName;

    TypeNamePair(BType type, String varName) {
        this.type = type;
        this.varName = varName;
    }

}