/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
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
package org.wso2.ballerinalang.compiler.tree;

import io.ballerina.types.Env;

import java.util.HashMap;
import java.util.Map;

/**
 * @since 0.983.0
 */
public class BLangTestablePackage extends BLangPackage {

    public BLangPackage parent;
    // Semantic Data
    //Map to maintain all the mock functions
    private final Map<String, String> mockFunctionNamesMap = new HashMap<>();

    private final Map<String, Boolean> isLegacyMockingMap = new HashMap<>();

    public BLangTestablePackage(Env env) {
        super(env);
    }

    public Map<String, String> getMockFunctionNamesMap() {
        return mockFunctionNamesMap;
    }

    public void addMockFunction(String id, String function) {
        this.mockFunctionNamesMap.put(id, function);
    }

    @Override
    public <T> void accept(BLangNodeAnalyzer<T> analyzer, T props) {
        analyzer.visit(this, props);
    }

    @Override
    public <T, R> R apply(BLangNodeTransformer<T, R> modifier, T props) {
        return modifier.transform(this, props);
    }

    public Map<String, Boolean> getIsLegacyMockingMap() {
        return isLegacyMockingMap;
    }

    public void addIsLegacyMockingMap(String id, Boolean isLegacy) {
        this.isLegacyMockingMap.put(id, isLegacy);
    }
}
