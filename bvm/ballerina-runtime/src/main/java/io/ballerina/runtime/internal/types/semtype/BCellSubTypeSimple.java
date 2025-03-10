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

import io.ballerina.runtime.api.types.semtype.BasicTypeCode;
import io.ballerina.runtime.api.types.semtype.Bdd;
import io.ballerina.runtime.api.types.semtype.BddAllOrNothing;
import io.ballerina.runtime.api.types.semtype.BddNode;
import io.ballerina.runtime.api.types.semtype.Builder;
import io.ballerina.runtime.api.types.semtype.Context;
import io.ballerina.runtime.api.types.semtype.Core;
import io.ballerina.runtime.api.types.semtype.Env;
import io.ballerina.runtime.api.types.semtype.SemType;
import io.ballerina.runtime.api.types.semtype.SubType;
import io.ballerina.runtime.api.types.semtype.TypeAtom;
import io.ballerina.runtime.internal.TypeChecker;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.ballerina.runtime.api.types.semtype.BddNode.bddAtom;

/**
 * Simplified representation of cell if the type is only basic type union and mutability is limited.
 *
 * @since 2201.12.0
 */
final class BCellSubTypeSimple extends BCellSubType implements DelegatedSubType {

    private final List<SemType> pos;
    private final List<SemType> neg;
    private BddNode inner;

    BCellSubTypeSimple(SemType type) {
        super(type.all() == BasicTypeCode.VT_MASK, type.all() == 0);
        assert type.some() == 0;
        this.pos = List.of(type);
        this.neg = List.of();
    }

    BCellSubTypeSimple(SemType type, BddNode bddNode) {
        this(type);
        inner = bddNode;
    }

    private BCellSubTypeSimple(List<SemType> pos, List<SemType> neg) {
        super(false, false);
        this.pos = pos;
        this.neg = neg;
    }

    @Override
    public SubType union(SubType other) {
        if (other instanceof BCellSubTypeSimple simple) {
            // P1\N1 U P2\N2 = (P1 U P2)\(N1 U N2)
            List<SemType> combinedPos = Stream.concat(pos.stream(), simple.pos.stream()).toList();
            List<SemType> combinedNeg = Stream.concat(neg.stream(), simple.neg.stream()).toList();
            return new BCellSubTypeSimple(combinedPos, combinedNeg);
        } else if (other instanceof BCellSubTypeImpl complex) {
            return createDelegate(inner().union(complex.inner()));
        }
        throw new IllegalArgumentException("union of different subtypes");
    }

    @Override
    public SubType intersect(SubType other) {
        if (other instanceof BCellSubTypeSimple simple) {
            // P1\N1 ∩ P2\N2 = (P1 ∩ P2)\(N1 U N2)
            SemType pos =
                    Stream.concat(this.pos.stream(), simple.pos.stream()).reduce(Builder.getValType(), Core::intersect);
            List<SemType> neg = Stream.concat(this.neg.stream(), simple.neg.stream()).toList();
            return new BCellSubTypeSimple(List.of(pos), neg);
        } else if (other instanceof BCellSubTypeImpl complex) {
            return createDelegate(inner().intersect(complex.inner()));
        }
        throw new IllegalArgumentException("intersection of different subtypes");
    }

    @Override
    public SubType complement() {
        return new BCellSubTypeSimple(neg, pos);
    }

    @Override
    public boolean isEmpty(Context cx) {
        if (pos.isEmpty()) {
            return true;
        }
        SemType posUnion = pos.stream().reduce(Builder.getNeverType(), Core::union);
        if (neg.isEmpty()) {
            return Core.isEmpty(cx, posUnion);
        }
        return neg.stream().anyMatch(neg -> Core.isEmpty(cx, Core.diff(posUnion, neg)));
    }

    @Override
    public SubTypeData data() {
        throw new IllegalStateException("unimplemented");
    }

    @Override
    public SubType inner() {
        if (inner != null) {
            return inner;
        }
        Env env = TypeChecker.getEnv();
        Optional<Bdd> posBdd =
                pos.stream().map(semType -> fromSemType(env, semType)).reduce((acum, bdd) -> (Bdd) acum.union(bdd));
        if (posBdd.isEmpty()) {
            return BddAllOrNothing.NOTHING;
        }
        Optional<Bdd> negBdd =
                neg.stream().map(semType -> fromSemType(env, semType)).reduce((acum, bdd) -> (Bdd) acum.union(bdd));
        if (negBdd.isEmpty()) {
            return posBdd.get();
        }
        return posBdd.get().diff(negBdd.get());
    }

    private static Bdd fromSemType(Env env, SemType type) {
        CellAtomicType atomicCell = CellAtomicType.from(type, CellAtomicType.CellMutability.CELL_MUT_LIMITED);
        TypeAtom atom = env.cellAtom(atomicCell);
        return bddAtom(atom);
    }

    @Override
    public int hashCode() {
        return Stream.concat(pos.stream(), neg.stream()).map(SemType::hashCode).reduce(0, Integer::sum);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BCellSubTypeSimple other)) {
            return false;
        }
        return pos.equals(other.pos) && neg.equals(other.neg);
    }
}
