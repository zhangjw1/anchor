package com.anchor.common.support;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;

import java.util.Arrays;
import java.util.List;

/** web3j 的返回值列表是不变泛型，匿名 TypeReference 需要一次转换。 */
public final class AbiOutputs {

    private AbiOutputs() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<TypeReference<Type>> single(TypeReference<?> reference) {
        return Arrays.asList((TypeReference) reference);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<TypeReference<Type>> of(TypeReference<?>... references) {
        TypeReference[] cast = new TypeReference[references.length];
        System.arraycopy(references, 0, cast, 0, references.length);
        return Arrays.asList(cast);
    }
}
