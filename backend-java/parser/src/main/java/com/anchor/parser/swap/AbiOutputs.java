package com.anchor.parser.swap;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;

import java.util.Arrays;
import java.util.List;

/** web3j 的返回值列表是不变泛型，匿名 TypeReference 需要一次转换。 */
final class AbiOutputs {

    private AbiOutputs() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<TypeReference<Type>> single(TypeReference<?> reference) {
        return Arrays.asList((TypeReference) reference);
    }
}
