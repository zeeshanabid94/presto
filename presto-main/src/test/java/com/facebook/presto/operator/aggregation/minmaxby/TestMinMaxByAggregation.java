/*
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
package com.facebook.presto.operator.aggregation.minmaxby;

import com.facebook.presto.metadata.FunctionManager;
import com.facebook.presto.metadata.MetadataManager;
import com.facebook.presto.operator.aggregation.InternalAggregationFunction;
import com.facebook.presto.operator.aggregation.state.StateCompiler;
import com.facebook.presto.spi.type.ArrayType;
import com.facebook.presto.spi.type.RowType;
import com.facebook.presto.spi.type.SqlDecimal;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.sql.tree.QualifiedName;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.facebook.presto.SessionTestUtils.TEST_SESSION;
import static com.facebook.presto.block.BlockAssertions.createArrayBigintBlock;
import static com.facebook.presto.block.BlockAssertions.createBooleansBlock;
import static com.facebook.presto.block.BlockAssertions.createDoublesBlock;
import static com.facebook.presto.block.BlockAssertions.createIntsBlock;
import static com.facebook.presto.block.BlockAssertions.createLongDecimalsBlock;
import static com.facebook.presto.block.BlockAssertions.createLongsBlock;
import static com.facebook.presto.block.BlockAssertions.createShortDecimalsBlock;
import static com.facebook.presto.block.BlockAssertions.createStringsBlock;
import static com.facebook.presto.operator.aggregation.AggregationTestUtils.assertAggregation;
import static com.facebook.presto.spi.type.BigintType.BIGINT;
import static com.facebook.presto.spi.type.BooleanType.BOOLEAN;
import static com.facebook.presto.spi.type.DecimalType.createDecimalType;
import static com.facebook.presto.spi.type.DoubleType.DOUBLE;
import static com.facebook.presto.spi.type.IntegerType.INTEGER;
import static com.facebook.presto.spi.type.VarcharType.VARCHAR;
import static com.facebook.presto.sql.analyzer.TypeSignatureProvider.fromTypes;
import static com.facebook.presto.type.UnknownType.UNKNOWN;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertNotNull;

public class TestMinMaxByAggregation
{
    private static final MetadataManager metadata = MetadataManager.createTestMetadataManager();
    private static final FunctionManager functionManager = metadata.getFunctionManager();

    @Test
    public void testAllRegistered()
    {
        Set<Type> orderableTypes = getTypes().stream()
                .filter(Type::isOrderable)
                .collect(toImmutableSet());

        for (Type keyType : orderableTypes) {
            for (Type valueType : getTypes()) {
                if (StateCompiler.getSupportedFieldTypes().contains(valueType.getJavaType())) {
                    assertNotNull(getMinByAggregation(valueType, keyType));
                    assertNotNull(getMaxByAggregation(valueType, keyType));
                }
            }
        }
    }

    private static List<Type> getTypes()
    {
        List<Type> simpleTypes = metadata.getTypeManager().getTypes();
        return new ImmutableList.Builder<Type>()
                .addAll(simpleTypes)
                .add(VARCHAR)
                .add(createDecimalType(1))
                .add(RowType.anonymous(ImmutableList.of(BIGINT, VARCHAR, DOUBLE)))
                .build();
    }

    @Test
    public void testMinUnknown()
    {
        InternalAggregationFunction unknownKey = getMinByAggregation(UNKNOWN, DOUBLE);
        assertAggregation(
                unknownKey,
                null,
                createBooleansBlock(null, null),
                createDoublesBlock(1.0, 2.0));
        InternalAggregationFunction unknownValue = getMinByAggregation(DOUBLE, UNKNOWN);
        assertAggregation(
                unknownValue,
                null,
                createDoublesBlock(1.0, 2.0),
                createBooleansBlock(null, null));
    }

    @Test
    public void testMaxUnknown()
    {
        InternalAggregationFunction unknownKey = getMaxByAggregation(UNKNOWN, DOUBLE);
        assertAggregation(
                unknownKey,
                null,
                createBooleansBlock(null, null),
                createDoublesBlock(1.0, 2.0));
        InternalAggregationFunction unknownValue = getMaxByAggregation(DOUBLE, UNKNOWN);
        assertAggregation(
                unknownValue,
                null,
                createDoublesBlock(1.0, 2.0),
                createBooleansBlock(null, null));
    }

    @Test
    public void testMinNull()
    {
        InternalAggregationFunction function = getMinByAggregation(DOUBLE, DOUBLE);
        assertAggregation(
                function,
                1.0,
                createDoublesBlock(1.0, null),
                createDoublesBlock(1.0, 2.0));
        assertAggregation(
                function,
                10.0,
                createDoublesBlock(10.0, 9.0, 8.0, 11.0),
                createDoublesBlock(1.0, null, 2.0, null));
    }

    @Test
    public void testMaxNull()
    {
        InternalAggregationFunction function = getMaxByAggregation(DOUBLE, DOUBLE);
        assertAggregation(
                function,
                null,
                createDoublesBlock(1.0, null),
                createDoublesBlock(1.0, 2.0));
        assertAggregation(
                function,
                10.0,
                createDoublesBlock(8.0, 9.0, 10.0, 11.0),
                createDoublesBlock(-2.0, null, -1.0, null));
    }

    @Test
    public void testMinDoubleDouble()
    {
        InternalAggregationFunction function = getMinByAggregation(DOUBLE, DOUBLE);
        assertAggregation(
                function,
                null,
                createDoublesBlock(null, null),
                createDoublesBlock(null, null));

        assertAggregation(
                function,
                3.0,
                createDoublesBlock(3.0, 2.0, 5.0, 3.0),
                createDoublesBlock(1.0, 1.5, 2.0, 4.0));
    }

    @Test
    public void testMaxDoubleDouble()
    {
        InternalAggregationFunction function = getMaxByAggregation(DOUBLE, DOUBLE);
        assertAggregation(
                function,
                null,
                createDoublesBlock(null, null),
                createDoublesBlock(null, null));

        assertAggregation(
                function,
                2.0,
                createDoublesBlock(3.0, 2.0, null),
                createDoublesBlock(1.0, 1.5, null));
    }

    @Test
    public void testMinDoubleVarchar()
    {
        InternalAggregationFunction function = getMinByAggregation(VARCHAR, DOUBLE);
        assertAggregation(
                function,
                "z",
                createStringsBlock("z", "a", "x", "b"),
                createDoublesBlock(1.0, 2.0, 2.0, 3.0));

        assertAggregation(
                function,
                "a",
                createStringsBlock("zz", "hi", "bb", "a"),
                createDoublesBlock(0.0, 1.0, 2.0, -1.0));
    }

    @Test
    public void testMaxDoubleVarchar()
    {
        InternalAggregationFunction function = getMaxByAggregation(VARCHAR, DOUBLE);
        assertAggregation(
                function,
                "a",
                createStringsBlock("z", "a", null),
                createDoublesBlock(1.0, 2.0, null));

        assertAggregation(
                function,
                "hi",
                createStringsBlock("zz", "hi", null, "a"),
                createDoublesBlock(0.0, 1.0, null, -1.0));
    }

    @Test
    public void testMinLongLongArray()
    {
        InternalAggregationFunction function = getMinByAggregation(new ArrayType(BIGINT), BIGINT);
        assertAggregation(
                function,
                ImmutableList.of(8L, 9L),
                createArrayBigintBlock(ImmutableList.of(ImmutableList.of(8L, 9L), ImmutableList.of(1L, 2L), ImmutableList.of(6L, 7L), ImmutableList.of(2L, 3L))),
                createLongsBlock(1L, 2L, 2L, 3L));

        assertAggregation(
                function,
                ImmutableList.of(2L),
                createArrayBigintBlock(ImmutableList.of(ImmutableList.of(8L, 9L), ImmutableList.of(6L, 7L), ImmutableList.of(2L, 3L), ImmutableList.of(2L))),
                createLongsBlock(0L, 1L, 2L, -1L));
    }

    @Test
    public void testMinLongArrayLong()
    {
        InternalAggregationFunction function = getMinByAggregation(BIGINT, new ArrayType(BIGINT));
        assertAggregation(
                function,
                3L,
                createLongsBlock(1L, 2L, 2L, 3L),
                createArrayBigintBlock(ImmutableList.of(ImmutableList.of(8L, 9L), ImmutableList.of(1L, 2L), ImmutableList.of(6L, 7L), ImmutableList.of(1L, 1L))));

        assertAggregation(
                function,
                -1L,
                createLongsBlock(0L, 1L, 2L, -1L),
                createArrayBigintBlock(ImmutableList.of(ImmutableList.of(8L, 9L), ImmutableList.of(6L, 7L), ImmutableList.of(-1L, -3L), ImmutableList.of(-1L))));
    }

    @Test
    public void testMaxLongArrayLong()
    {
        InternalAggregationFunction function = getMaxByAggregation(BIGINT, new ArrayType(BIGINT));
        assertAggregation(
                function,
                1L,
                createLongsBlock(1L, 2L, 2L, 3L),
                createArrayBigintBlock(ImmutableList.of(ImmutableList.of(8L, 9L), ImmutableList.of(1L, 2L), ImmutableList.of(6L, 7L), ImmutableList.of(1L, 1L))));

        assertAggregation(
                function,
                2L,
                createLongsBlock(0L, 1L, 2L, -1L),
                createArrayBigintBlock(ImmutableList.of(ImmutableList.of(-8L, 9L), ImmutableList.of(-6L, 7L), ImmutableList.of(-1L, -3L), ImmutableList.of(-1L))));
    }

    @Test
    public void testMaxLongLongArray()
    {
        InternalAggregationFunction function = getMaxByAggregation(new ArrayType(BIGINT), BIGINT);
        assertAggregation(
                function,
                ImmutableList.of(1L, 2L),
                createArrayBigintBlock(asList(asList(3L, 4L), asList(1L, 2L), null)),
                createLongsBlock(1L, 2L, null));

        assertAggregation(
                function,
                ImmutableList.of(2L, 3L),
                createArrayBigintBlock(asList(asList(3L, 4L), asList(2L, 3L), null, asList(1L, 2L))),
                createLongsBlock(0L, 1L, null, -1L));
    }

    @Test
    public void testMinLongDecimalDecimal()
    {
        Type decimalType = createDecimalType(19, 1);
        InternalAggregationFunction function = getMinByAggregation(decimalType, decimalType);
        assertAggregation(
                function,
                SqlDecimal.of("2.2"),
                createLongDecimalsBlock("1.1", "2.2", "3.3"),
                createLongDecimalsBlock("1.2", "1.0", "2.0"));
    }

    @Test
    public void testMaxLongDecimalDecimal()
    {
        Type decimalType = createDecimalType(19, 1);
        InternalAggregationFunction function = getMaxByAggregation(decimalType, decimalType);
        assertAggregation(
                function,
                SqlDecimal.of("3.3"),
                createLongDecimalsBlock("1.1", "2.2", "3.3", "4.4"),
                createLongDecimalsBlock("1.2", "1.0", "2.0", "1.5"));
    }

    @Test
    public void testMinShortDecimalDecimal()
    {
        Type decimalType = createDecimalType(10, 1);
        InternalAggregationFunction function = getMinByAggregation(decimalType, decimalType);
        assertAggregation(
                function,
                SqlDecimal.of("2.2"),
                createShortDecimalsBlock("1.1", "2.2", "3.3"),
                createShortDecimalsBlock("1.2", "1.0", "2.0"));
    }

    @Test
    public void testMaxShortDecimalDecimal()
    {
        Type decimalType = createDecimalType(10, 1);
        InternalAggregationFunction function = getMaxByAggregation(decimalType, decimalType);
        assertAggregation(
                function,
                SqlDecimal.of("3.3"),
                createShortDecimalsBlock("1.1", "2.2", "3.3", "4.4"),
                createShortDecimalsBlock("1.2", "1.0", "2.0", "1.5"));
    }

    @Test
    public void testMinBooleanVarchar()
    {
        InternalAggregationFunction function = getMinByAggregation(VARCHAR, BOOLEAN);
        assertAggregation(
                function,
                "b",
                createStringsBlock("a", "b", "c"),
                createBooleansBlock(true, false, true));
    }

    @Test
    public void testMaxBooleanVarchar()
    {
        InternalAggregationFunction function = getMaxByAggregation(VARCHAR, BOOLEAN);
        assertAggregation(
                function,
                "c",
                createStringsBlock("a", "b", "c"),
                createBooleansBlock(false, false, true));
    }

    @Test
    public void testMinIntegerVarchar()
    {
        InternalAggregationFunction function = getMinByAggregation(VARCHAR, INTEGER);
        assertAggregation(
                function,
                "a",
                createStringsBlock("a", "b", "c"),
                createIntsBlock(1, 2, 3));
    }

    @Test
    public void testMaxIntegerVarchar()
    {
        InternalAggregationFunction function = getMaxByAggregation(VARCHAR, INTEGER);
        assertAggregation(
                function,
                "c",
                createStringsBlock("a", "b", "c"),
                createIntsBlock(1, 2, 3));
    }

    @Test
    public void testMinBooleanLongArray()
    {
        InternalAggregationFunction function = getMinByAggregation(new ArrayType(BIGINT), BOOLEAN);
        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(asList(3L, 4L), null, null)),
                createBooleansBlock(true, false, true));
    }

    @Test
    public void testMaxBooleanLongArray()
    {
        InternalAggregationFunction function = getMaxByAggregation(new ArrayType(BIGINT), BOOLEAN);
        assertAggregation(
                function,
                asList(2L, 2L),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))),
                createBooleansBlock(false, false, true));
    }

    @Test
    public void testMinLongVarchar()
    {
        InternalAggregationFunction function = getMinByAggregation(VARCHAR, BIGINT);
        assertAggregation(
                function,
                "a",
                createStringsBlock("a", "b", "c"),
                createLongsBlock(1, 2, 3));
    }

    @Test
    public void testMaxLongVarchar()
    {
        InternalAggregationFunction function = getMaxByAggregation(VARCHAR, BIGINT);
        assertAggregation(
                function,
                "c",
                createStringsBlock("a", "b", "c"),
                createLongsBlock(1, 2, 3));
    }

    @Test
    public void testMinDoubleLongArray()
    {
        InternalAggregationFunction function = getMinByAggregation(new ArrayType(BIGINT), DOUBLE);
        assertAggregation(
                function,
                asList(3L, 4L),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))),
                createDoublesBlock(1.0, 2.0, 3.0));

        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(null, null, asList(2L, 2L))),
                createDoublesBlock(0.0, 1.0, 2.0));
    }

    @Test
    public void testMaxDoubleLongArray()
    {
        InternalAggregationFunction function = getMaxByAggregation(new ArrayType(BIGINT), DOUBLE);
        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))),
                createDoublesBlock(1.0, 2.0, null));

        assertAggregation(
                function,
                asList(2L, 2L),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))),
                createDoublesBlock(0.0, 1.0, 2.0));
    }

    @Test
    public void testMinSliceLongArray()
    {
        InternalAggregationFunction function = getMinByAggregation(new ArrayType(BIGINT), VARCHAR);
        assertAggregation(
                function,
                asList(3L, 4L),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))),
                createStringsBlock("a", "b", "c"));

        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(null, null, asList(2L, 2L))),
                createStringsBlock("a", "b", "c"));
    }

    @Test
    public void testMaxSliceLongArray()
    {
        InternalAggregationFunction function = getMaxByAggregation(new ArrayType(BIGINT), VARCHAR);
        assertAggregation(
                function,
                asList(2L, 2L),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))),
                createStringsBlock("a", "b", "c"));

        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(asList(3L, 4L), null, null)),
                createStringsBlock("a", "b", "c"));
    }

    @Test
    public void testMinLongArrayLongArray()
    {
        InternalAggregationFunction function = getMinByAggregation(new ArrayType(BIGINT), new ArrayType(BIGINT));
        assertAggregation(
                function,
                asList(1L, 2L),
                createArrayBigintBlock(asList(asList(3L, 3L), null, asList(1L, 2L))),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))));
    }

    @Test
    public void testMaxLongArrayLongArray()
    {
        InternalAggregationFunction function = getMaxByAggregation(new ArrayType(BIGINT), new ArrayType(BIGINT));
        assertAggregation(
                function,
                asList(3L, 3L),
                createArrayBigintBlock(asList(asList(3L, 3L), null, asList(1L, 2L))),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))));
    }

    @Test
    public void testMinLongArraySlice()
    {
        InternalAggregationFunction function = getMinByAggregation(VARCHAR, new ArrayType(BIGINT));
        assertAggregation(
                function,
                "c",
                createStringsBlock("a", "b", "c"),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))));
    }

    @Test
    public void testMaxLongArraySlice()
    {
        InternalAggregationFunction function = getMaxByAggregation(VARCHAR, new ArrayType(BIGINT));
        assertAggregation(
                function,
                "a",
                createStringsBlock("a", "b", "c"),
                createArrayBigintBlock(asList(asList(3L, 4L), null, asList(2L, 2L))));
    }

    @Test
    public void testMinUnknownSlice()
    {
        InternalAggregationFunction function = getMinByAggregation(VARCHAR, UNKNOWN);
        assertAggregation(
                function,
                null,
                createStringsBlock("a", "b", "c"),
                createArrayBigintBlock(asList(null, null, null)));
    }

    @Test
    public void testMaxUnknownSlice()
    {
        InternalAggregationFunction function = getMaxByAggregation(VARCHAR, UNKNOWN);
        assertAggregation(
                function,
                null,
                createStringsBlock("a", "b", "c"),
                createArrayBigintBlock(asList(null, null, null)));
    }

    @Test
    public void testMinUnknownLongArray()
    {
        InternalAggregationFunction function = getMinByAggregation(new ArrayType(BIGINT), UNKNOWN);
        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(asList(3L, 3L), null, asList(1L, 2L))),
                createArrayBigintBlock(asList(null, null, null)));
    }

    @Test
    public void testMaxUnknownLongArray()
    {
        InternalAggregationFunction function = getMaxByAggregation(new ArrayType(BIGINT), UNKNOWN);
        assertAggregation(
                function,
                null,
                createArrayBigintBlock(asList(asList(3L, 3L), null, asList(1L, 2L))),
                createArrayBigintBlock(asList(null, null, null)));
    }

    private InternalAggregationFunction getMinByAggregation(Type... arguments)
    {
        return functionManager.getAggregateFunctionImplementation(functionManager.resolveFunction(TEST_SESSION, QualifiedName.of("min_by"), fromTypes(arguments)));
    }

    private InternalAggregationFunction getMaxByAggregation(Type... arguments)
    {
        return functionManager.getAggregateFunctionImplementation(functionManager.resolveFunction(TEST_SESSION, QualifiedName.of("max_by"), fromTypes(arguments)));
    }
}
